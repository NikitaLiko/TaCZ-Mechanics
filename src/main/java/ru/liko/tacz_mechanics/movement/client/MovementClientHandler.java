package ru.liko.tacz_mechanics.movement.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.compat.FpvDroneCompat;
import ru.liko.tacz_mechanics.movement.LeanCollision;
import ru.liko.tacz_mechanics.movement.MovementPosture;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.network.MovementNetworkHandler;

/**
 * Client-side handler for player leaning input and camera/model effects.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public class MovementClientHandler {

    private static PlayerState clientState = new PlayerState();
    private static int lastSentCode = 0;

    // Camera offset for leaning
    public static float cameraProbeOffset = 0;
    private static long lastSyncTime = System.currentTimeMillis();

    // Input lock flags
    private static boolean probeKeyLock = false;
    private static boolean sitKeyLock = false;
    private static boolean proneKeyLock = false;

    // Slide (sit) / dive (prone) — client-driven motion, like ModularMovements.
    private static final float SLIDE_USER = 0.8f;
    private static float slideCharge = 0f;
    private static float slideAmp = 0f;
    private static float slideChargeEntry = 0f;
    private static Vec3 slideVec = Vec3.ZERO;

    // Facing yaw captured when going prone, for the view clamp.
    private static float proneAnchorYaw = 0f;

    // Smooths the eye-height drop when entering/leaving a posture (MM's cameraOffsetY). Blocks the
    // eye still has to travel, eased to 0. Tune the speed below for how snappy the drop feels.
    // Only posture toggles are compensated — sneak and other vanilla eye changes have their own
    // smoothing, and counter-offsetting them made the camera pop.
    private static float cameraPostureOffsetY = 0f;
    private static float lastEyeHeight = -1f;
    private static int lastCamPosture = 0;
    private static long lastPostureCamTime = System.currentTimeMillis();

    public static PlayerState getClientState() {
        return clientState;
    }

    public static boolean isLocalProne() {
        return clientState.isProne();
    }

    public static float getProneAnchorYaw() {
        return proneAnchorYaw;
    }

    /**
     * Per-frame vertical camera offset that eases the eye-height jump when a posture toggles. Called
     * once per frame from the camera mixin (first person only). When the eye height changes by Δ, the
     * camera is kept at the old height and then eased to the new one, instead of snapping.
     */
    public static float postureCameraOffsetY(Player player) {
        float eye = player.getEyeHeight();
        int posture = clientState.isSitting() ? 1 : clientState.isProne() ? 2 : 0;
        if (lastEyeHeight < 0f) {
            lastEyeHeight = eye;
            lastCamPosture = posture;
        }
        // Compensate the eye jump only when it comes from a posture toggle. Any other eye-height
        // change (sneak, swimming...) is vanilla's business and already animated by the camera.
        if (posture != lastCamPosture) {
            cameraPostureOffsetY += lastEyeHeight - eye;
            lastCamPosture = posture;
        }
        lastEyeHeight = eye;

        long now = System.currentTimeMillis();
        double amplifier = (now - lastPostureCamTime) * (60 / 1000d);
        lastPostureCamTime = now;
        float step = 0.035f * (float) amplifier; // ~0.3s for a full sit/prone drop
        if (cameraPostureOffsetY > 0f) {
            cameraPostureOffsetY = Math.max(0f, cameraPostureOffsetY - step);
        } else if (cameraPostureOffsetY < 0f) {
            cameraPostureOffsetY = Math.min(0f, cameraPostureOffsetY + step);
        }
        return cameraPostureOffsetY;
    }

    /**
     * Откат/принудительная синхронизация с сервером (невалидное состояние и т.п.).
     */
    public static void applySyncedStateFromServer(int code) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        clientState.readCode(code);
        lastSentCode = code;
        MovementStateManager.updateState(mc.player.getUUID(), code);
        mc.player.refreshDimensions();
    }

    public static PlayerState getStateForPlayer(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            return clientState;
        }
        return MovementStateManager.get(player.getUUID());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Config.Movement.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            resetState();
            return;
        }

        // Update client state offsets for smooth animation
        clientState.updateOffset();
        clampLeanForCollision(mc.player);

        // Posture upkeep: sprint charge, auto-cancel, slide physics, pose pin.
        updateSlideCharge(mc.player);
        cancelPostureIfInvalid(mc.player);
        applySlide(mc.player);
        MovementPosture.applyForcedPose(mc.player, clientState);

        // Remote players only get their `probe` synced via broadcast; without
        // ticking updateOffset() here their probeOffset stays 0 and the lean
        // never animates (legs + body tilt read probeOffset).
        for (Player other : mc.level.players()) {
            if (other == mc.player) continue;
            PlayerState state = MovementStateManager.get(other.getUUID());
            if (state != null) {
                state.updateOffset();
                float maxLeft = LeanCollision.maxLeanMagnitude(other, -1f);
                float maxRight = LeanCollision.maxLeanMagnitude(other, 1f);
                state.clampProbeOffset(maxLeft, maxRight);
            }
        }

        // Push state to server + refresh dimensions only on change.
        flushStateChange(mc.player);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!Config.Movement.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        handlePostureInput(mc);
        handleLeanInput(mc, event);
    }

    private static void handlePostureInput(Minecraft mc) {
        LocalPlayer player = mc.player;
        boolean sitDown = MovementKeyBindings.SIT_KEY.isDown();
        boolean proneDown = MovementKeyBindings.PRONE_KEY.isDown();

        if (sitDown && !sitKeyLock) {
            sitKeyLock = true;
            toggleSit(player);
        }
        if (!sitDown) sitKeyLock = false;

        if (proneDown && !proneKeyLock) {
            proneKeyLock = true;
            toggleProne(player);
        }
        if (!proneDown) proneKeyLock = false;

        // Pin the vanilla pose immediately (before this tick's updatePlayerPose) so entering a
        // posture under a low ceiling never flips the player into a one-tick crouch.
        MovementPosture.applyForcedPose(player, clientState);
    }

    private static void toggleSit(LocalPlayer player) {
        if (!clientState.canSit()) return;
        if (clientState.isSitting()) {
            if (MovementPosture.canStand(player)) clientState.standUp();
        } else if (Config.Movement.sitEnabled && player.onGround() && MovementPosture.canHoldPosture(player)) {
            // Only a sprint from *standing* earns a slide. Switching in from prone must not re-arm the
            // impulse, or alternating sit/prone perpetually re-triggers it — a client-driven speedhack.
            boolean slide = clientState.isStanding() && player.isSprinting();
            clientState.enableSit();
            if (Config.Movement.slideEnabled && slide) startSlide(player);
        }
    }

    private static void toggleProne(LocalPlayer player) {
        if (!clientState.canProne()) return;
        if (clientState.isProne()) {
            if (MovementPosture.canStand(player)) clientState.standUp();
        } else if (Config.Movement.proneEnabled && player.onGround() && MovementPosture.canHoldPosture(player)) {
            // Same guard as sit: a dive only fires from a standing sprint, never when switching in from
            // sit — otherwise alternating the two keys re-arms the velocity burst every switch.
            boolean dive = clientState.isStanding() && player.isSprinting();
            clientState.enableProne();
            proneAnchorYaw = player.getYRot();
            slideAmp = 0f;
            if (Config.Movement.diveEnabled && dive) startDive(player);
        }
    }

    private static void startSlide(LocalPlayer player) {
        // The burst is fueled entirely by real sprint time and consumed on use — a free floor
        // (old max(charge, 0.2)) let posture spam chain bursts faster than plain sprinting.
        slideChargeEntry = slideCharge;
        slideCharge = 0f;
        if (slideChargeEntry <= 0f) return;
        Vec3 dm = player.getDeltaMovement();
        Vec3 horiz = new Vec3(dm.x, 0, dm.z);
        if (horiz.lengthSqr() < 1.0e-4) {
            horiz = player.getLookAngle().multiply(1, 0, 1);
        }
        slideVec = horiz.lengthSqr() < 1.0e-4 ? Vec3.ZERO : horiz.normalize();
        slideAmp = (float) Config.Movement.slideMaxForce;
    }

    private static void startDive(LocalPlayer player) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0e-4) return;
        look = look.normalize();
        float charge = slideCharge;
        slideCharge = 0f;
        if (charge <= 0f) return;
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(look.x * charge, Math.max(dm.y, 0.2 * charge), look.z * charge);
        player.hurtMarked = true;
    }

    private static void updateSlideCharge(LocalPlayer player) {
        if (player.isSprinting()) {
            slideCharge = Math.min(1f, slideCharge + 1f / 20f);
        } else {
            slideCharge = 0f;
        }
    }

    private static void cancelPostureIfInvalid(LocalPlayer player) {
        if (clientState.isStanding()) return;
        if (!MovementPosture.canHoldPosture(player)) {
            clientState.standUp();
            slideAmp = 0f;
        }
    }

    private static void applySlide(LocalPlayer player) {
        if (!clientState.isSitting() || slideAmp <= 0f) {
            if (!clientState.isSitting()) slideAmp = 0f;
            return;
        }
        double amp = slideAmp * SLIDE_USER * slideChargeEntry;
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(slideVec.x * amp, dm.y, slideVec.z * amp);
        boolean airborne = player.fallDistance > 1.0f && !player.isInWater();
        if (!airborne) {
            slideAmp = Math.max(0f, slideAmp - (float) Config.Movement.slideDecay);
        }
    }

    private static void handleLeanInput(Minecraft mc, InputEvent.Key event) {
        KeyMapping leftLean = MovementKeyBindings.LEAN_LEFT_KEY;
        KeyMapping rightLean = MovementKeyBindings.LEAN_RIGHT_KEY;

        // Lean and posture are mutually exclusive.
        if (!clientState.isStanding()) return;
        if (!clientState.canProbe()) return;

        // Left lean
        if (leftLean.isDown() && !probeKeyLock) {
            probeKeyLock = true;
            if (clientState.getProbe() != -1) {
                clientState.leftProbe();
            } else {
                clientState.resetProbe();
            }
        }

        // Right lean
        if (rightLean.isDown() && !probeKeyLock) {
            probeKeyLock = true;
            if (clientState.getProbe() != 1) {
                clientState.rightProbe();
            } else {
                clientState.resetProbe();
            }
        }

        // Reset lean lock
        if (!leftLean.isDown() && !rightLean.isDown()) {
            probeKeyLock = false;
            if (!Config.Movement.leanAutoHold && clientState.getProbe() != 0) {
                clientState.resetProbe();
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!Config.Movement.enabled) return;

        float mult = 1f;
        if (clientState.isSitting()) {
            mult = (float) Config.Movement.sitSpeed;
        } else if (clientState.isProne()) {
            mult = (float) Config.Movement.proneSpeed;
        } else if (clientState.getProbe() != 0) {
            mult = 0.9f;
        }
        if (mult != 1f) {
            event.getInput().forwardImpulse *= mult;
            event.getInput().leftImpulse *= mult;
        }
    }

    // LOWEST so our lean roll is written after every other listener has read event.roll.
    // Superb Warfare snapshots event.roll into its own cameraRoll and re-applies it to the
    // world pose in GameRenderer#renderLevel without clearing the event — running before it
    // makes vanilla and SBW each apply our lean, doubling the world roll while the hand
    // (LeanHandRollMixin) only rolls once.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!Config.Movement.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Пилот в очках: камерой владеет fpvdrone, наш крен заваливал бы ему горизонт,
        // а CameraOffsetMixin ещё и уводил камеру вбок. Гасим сразу, без плавного
        // затухания, — иначе остаток наклона доезжает уже внутри FPV.
        if (FpvDroneCompat.isPilotingDrone(mc.player)) {
            cameraProbeOffset = 0;
            return;
        }

        // Update camera probe offset here for smooth per-frame animation
        double amplifier = (System.currentTimeMillis() - lastSyncTime) * (60 / 1000d);
        lastSyncTime = System.currentTimeMillis();

        // Slower speed for more realistic leaning (0.05 instead of 0.1)
        double speed = 0.05;

        if (clientState.getProbe() == -1) {
            if (cameraProbeOffset > -1) {
                cameraProbeOffset -= speed * amplifier;
            }
            if (cameraProbeOffset < -1) {
                cameraProbeOffset = -1;
            }
        } else if (clientState.getProbe() == 1) {
            if (cameraProbeOffset < 1) {
                cameraProbeOffset += speed * amplifier;
            }
            if (cameraProbeOffset > 1) {
                cameraProbeOffset = 1;
            }
        } else {
            if (Math.abs(cameraProbeOffset) <= speed * amplifier) {
                cameraProbeOffset = 0;
            } else if (cameraProbeOffset < 0) {
                cameraProbeOffset += speed * amplifier;
            } else if (cameraProbeOffset > 0) {
                cameraProbeOffset -= speed * amplifier;
            }
        }

        float maxLeft = LeanCollision.maxLeanMagnitude(mc.player, -1f);
        float maxRight = LeanCollision.maxLeanMagnitude(mc.player, 1f);
        cameraProbeOffset = Mth.clamp(cameraProbeOffset, -maxLeft, maxRight);

        // Apply lean rotation
        float roll = (float) event.getRoll();
        event.setRoll(roll + 10 * cameraProbeOffset);
    }

    private static void flushStateChange(LocalPlayer player) {
        int code = clientState.writeCode();
        if (code == lastSentCode) return;
        MovementStateManager.updateState(player.getUUID(), code);
        player.refreshDimensions();
        MovementPosture.logHitbox("CLIENT", player, clientState);
        MovementNetworkHandler.sendStateToServer(code);
        lastSentCode = code;
    }

    private static void clampLeanForCollision(LocalPlayer player) {
        if (player == null) {
            return;
        }
        float maxLeft = LeanCollision.maxLeanMagnitude(player, -1f);
        float maxRight = LeanCollision.maxLeanMagnitude(player, 1f);
        clientState.clampProbeOffset(maxLeft, maxRight);
        cameraProbeOffset = Mth.clamp(cameraProbeOffset, -maxLeft, maxRight);
    }

    private static void resetState() {
        clientState.reset();
        cameraProbeOffset = 0;
        lastSentCode = 0;
        slideAmp = 0f;
        slideCharge = 0f;
        cameraPostureOffsetY = 0f;
        lastEyeHeight = -1f;
        lastCamPosture = 0;
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!Config.Movement.enabled) return;
        // Whole-body lean lives in LeanBodyRollMixin (matched by the hitbox skeleton); applying the
        // legacy translate+rotate on top of it tilts the model past its own hitbox.
        if (Config.Movement.leanBodyRoll) return;

        Player player = event.getEntity();
        PlayerState state = getStateForPlayer(player);
        if (state == null) return;

        PoseStack poseStack = event.getPoseStack();
        // Lerp with partial tick for smooth render between tick-driven updates
        float probeOffset = Mth.lerp(event.getPartialTick(), state.getProbeOffsetOld(), state.getProbeOffset());

        // Apply leaning rotation
        if (probeOffset != 0) {
            float partialTick = event.getPartialTick();

            // Interpolate yaws for smooth rendering
            float headYaw = player.yHeadRotO + (player.yHeadRot - player.yHeadRotO) * partialTick;
            float bodyYaw = player.yBodyRotO + (player.yBodyRot - player.yBodyRotO) * partialTick;

            // Translate sideways based on head yaw (World Space)
            double headRadians = Math.toRadians(headYaw);
            // Use same formula as camera for consistency
            double offsetX = -probeOffset * 0.1 * Math.cos(headRadians);
            double offsetZ = -probeOffset * 0.1 * Math.sin(headRadians);
            poseStack.translate(offsetX, 0, offsetZ);

            // Apply lean rotation using conjugation
            // 1. Rotate to Body Frame
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));

            // 2. Apply Lean (Rotation around Z)
            poseStack.mulPose(Axis.ZP.rotationDegrees(probeOffset * -20.0F));

            // 3. Rotate back to World Frame
            poseStack.mulPose(Axis.YP.rotationDegrees(-(180.0F - bodyYaw)));
        }
    }
}
