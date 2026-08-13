package ru.liko.tacz_mechanics.client.freeaim;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.network.FreeAimSyncPacket;

/**
 * Free Aim orchestrator: spring-driven weapon sway.
 *
 * <p>Two independent channels, because one spring cannot be both weighty and punchy:
 * <ul>
 *   <li><b>sway</b> (soft, slow) — look lag, movement bob, hand tremor. This is the aim offset:
 *       it drives the crosshair and the bullet direction synced to the server.</li>
 *   <li><b>recoil</b> (stiff, fast) — per-shot kicks. Visual only: TaCZ already moves the camera
 *       on recoil, so feeding it into the aim offset again would double-count it.</li>
 * </ul>
 */
public class FreeAimHandler {

    private static final FreeAimHandler INSTANCE = new FreeAimHandler();

    /** Explicit damping is only stable while damping*step < 1; 4 substeps allow damping up to ~4. */
    private static final int SUBSTEPS = 4;
    /** Per-tick view change beyond this is a camera cut, not a turn the arms could follow. */
    private static final float CAMERA_CUT_DEGREES = 45f;

    private final SwaySpring pitchSpring = new SwaySpring();
    private final SwaySpring yawSpring = new SwaySpring();
    private final SwaySpring recoilPitchSpring = new SwaySpring();
    private final SwaySpring recoilYawSpring = new SwaySpring();
    private final MovementSource movementSource = new MovementSource();
    private final TremorSource tremorSource = new TremorSource();
    private final BreathHold breathHold = new BreathHold();

    // Previous player rotation (for look-delta source)
    private float lastPitch = Float.NaN;
    private float lastYaw = Float.NaN;

    // Low-passed look delta, so a flick becomes a push instead of a hammer blow
    private float smoothLookPitch = 0f;
    private float smoothLookYaw = 0f;

    // Pending recoil impulse (added by RecoilSource between ticks)
    private float pendingRecoilPitch = 0f;
    private float pendingRecoilYaw = 0f;

    private int syncTimer = 0;

    public static FreeAimHandler getInstance() {
        return INSTANCE;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (!Config.FreeAim.enabled || player == null || mc.isPaused() || !isHoldingGun(player)) {
            reset();
            return;
        }

        // Sync spring params from config every tick (cheap, allows live reload)
        float stiffness = (float) Config.FreeAim.stiffness;
        float damping = (float) Config.FreeAim.damping;
        float max = (float) Config.FreeAim.maxAngle;
        pitchSpring.setParams(stiffness, damping, max);
        yawSpring.setParams(stiffness, damping, max);
        float recoilStiffness = (float) Config.FreeAim.recoilStiffness;
        float recoilDamping = (float) Config.FreeAim.recoilDamping;
        recoilPitchSpring.setParams(recoilStiffness, recoilDamping, max);
        recoilYawSpring.setParams(recoilStiffness, recoilDamping, max);

        float currentPitch = player.getXRot();
        float currentYaw = player.getYRot();

        if (Float.isNaN(lastPitch)) {
            lastPitch = currentPitch;
            lastYaw = currentYaw;
        }

        // === Source: look delta ===
        float deltaPitch = currentPitch - lastPitch;
        float deltaYaw = currentYaw - lastYaw;
        while (deltaYaw > 180) deltaYaw -= 360;
        while (deltaYaw < -180) deltaYaw += 360;
        lastPitch = currentPitch;
        lastYaw = currentYaw;

        // A jump this large in one tick is a teleport, respawn or cutscene cut, not a turn.
        if (Math.abs(deltaPitch) > CAMERA_CUT_DEGREES || Math.abs(deltaYaw) > CAMERA_CUT_DEGREES) {
            deltaPitch = 0f;
            deltaYaw = 0f;
        }

        // Mouse movement arrives as one lump per tick, so a flick used to hit the spring as a single
        // hammer blow. Spreading it over a few ticks preserves the total impulse but not the jolt.
        float lookBlend = 1f - (float) Config.FreeAim.lookSmoothing;
        smoothLookPitch += (deltaPitch - smoothLookPitch) * lookBlend;
        smoothLookYaw += (deltaYaw - smoothLookYaw) * lookBlend;

        float lookSens = (float) Config.FreeAim.lookSensitivity;
        // Gun lags behind: impulse opposite to camera movement
        pitchSpring.addImpulse(-smoothLookPitch * lookSens);
        yawSpring.addImpulse(-smoothLookYaw * lookSens);

        // === Source: movement ===
        movementSource.apply(player, pitchSpring, yawSpring);

        // === Breath hold: sneak while aiming to steady the sights ===
        float aiming = aimingProgress(1f);
        breathHold.setParams(
                Config.FreeAim.breathHoldTicks,
                Config.FreeAim.breathRecoverTicks,
                (float) Config.FreeAim.breathSteadiness,
                (float) Config.FreeAim.breathExhaustedTremor,
                Config.FreeAim.breathSettleTicks);
        // isShiftKeyDown, not the raw key, so toggle-sneak players get the same behaviour.
        boolean holdRequested = Config.FreeAim.breathEnabled && aiming > 0.5f && player.isShiftKeyDown();
        breathHold.tick(holdRequested);

        // === Source: breathing sway while holding ADS ===
        // Deliberately outside the sway springs: it must NOT be damped by adsMultiplier, otherwise a
        // scope — where the wander matters most — would be the steadiest the gun ever gets.
        tremorSource.update(aiming, aimingZoom(player), breathHold.getSteadiness());

        // === Source: recoil (queued by RecoilSource) — own channel, never touches the aim offset ===
        if (pendingRecoilPitch != 0f || pendingRecoilYaw != 0f) {
            recoilPitchSpring.addImpulse(pendingRecoilPitch);
            recoilYawSpring.addImpulse(pendingRecoilYaw);
            pendingRecoilPitch = 0f;
            pendingRecoilYaw = 0f;
        }

        // === Integrate (dt = 1 tick) ===
        pitchSpring.update(1f, SUBSTEPS);
        yawSpring.update(1f, SUBSTEPS);
        recoilPitchSpring.update(1f, SUBSTEPS);
        recoilYawSpring.update(1f, SUBSTEPS);

        // === Sync effective offset (ADS already applied) to server every 2 ticks ===
        if (++syncTimer >= 2) {
            syncTimer = 0;
            float effPitch = getEffectivePitch(1f);
            float effYaw = getEffectiveYaw(1f);
            if (effPitch != 0f || effYaw != 0f) {
                try {
                    PacketDistributor.sendToServer(new FreeAimSyncPacket(effPitch, effYaw));
                } catch (Exception e) {
                    LogUtils.getLogger().warn("Failed to send FreeAim sync packet", e);
                }
            }
        }
    }

    public void addRecoilImpulse(float pitchImpulse, float yawImpulse) {
        pendingRecoilPitch += pitchImpulse;
        pendingRecoilYaw += yawImpulse;
    }

    private boolean isHoldingGun(LocalPlayer player) {
        try {
            return IGun.mainHandHoldGun(player);
        } catch (Exception e) {
            return false;
        }
    }

    /** 0 = hip fire, 1 = fully aiming down sights. */
    public float aimingProgress(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0f;
        try {
            return IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(pt);
        } catch (Exception e) {
            return 0f;
        }
    }

    /** Magnification of the held gun's sight (1 = none). */
    private float aimingZoom(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) return 1f;
        try {
            return Math.max(1f, iGun.getAimingZoom(stack));
        } catch (Exception e) {
            return 1f;
        }
    }

    /** ADS scale: lerp(1.0, adsMultiplier, aimingProgress). */
    private float adsFactor(float pt) {
        float ads = (float) Config.FreeAim.adsMultiplier;
        float p = aimingProgress(pt);
        return 1f + (ads - 1f) * p;
    }

    /** Aim offset (sway + tremor): where the bullet actually goes, and where the crosshair sits. */
    public float getEffectivePitch(float pt) {
        return pitchSpring.getInterpolated(pt) * adsFactor(pt) + tremorSource.getPitch(pt);
    }

    public float getEffectiveYaw(float pt) {
        return yawSpring.getInterpolated(pt) * adsFactor(pt) + tremorSource.getYaw(pt);
    }

    /** Model offset: aim offset plus the visual-only recoil kick. */
    public float getVisualPitch(float pt) {
        return getEffectivePitch(pt) + recoilPitchSpring.getInterpolated(pt);
    }

    public float getVisualYaw(float pt) {
        return getEffectiveYaw(pt) + recoilYawSpring.getInterpolated(pt);
    }

    /** Recoil kick in degrees, for the model push-back (pitch) and roll (yaw). */
    public float getRecoilPitch(float pt) {
        return recoilPitchSpring.getInterpolated(pt);
    }

    public float getRecoilYaw(float pt) {
        return recoilYawSpring.getInterpolated(pt);
    }

    public float getCrosshairX(float pt) {
        if (!isActive() || Config.FreeAim.disableCrosshairMovement) return 0f;
        return -getEffectiveYaw(pt) * (float) Config.FreeAim.crosshairScale;
    }

    public float getCrosshairY(float pt) {
        if (!isActive() || Config.FreeAim.disableCrosshairMovement) return 0f;
        return -getEffectivePitch(pt) * (float) Config.FreeAim.crosshairScale;
    }

    public boolean isActive() {
        if (!Config.FreeAim.enabled) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player != null && isHoldingGun(player);
    }

    public void reset() {
        pitchSpring.reset();
        yawSpring.reset();
        recoilPitchSpring.reset();
        recoilYawSpring.reset();
        lastPitch = Float.NaN;
        lastYaw = Float.NaN;
        smoothLookPitch = 0f;
        smoothLookYaw = 0f;
        pendingRecoilPitch = 0f;
        pendingRecoilYaw = 0f;
        syncTimer = 0;
        movementSource.reset();
        tremorSource.reset();
        breathHold.reset();
    }
}
