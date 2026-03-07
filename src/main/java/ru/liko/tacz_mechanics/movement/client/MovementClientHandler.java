package ru.liko.tacz_mechanics.movement.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
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
import ru.liko.tacz_mechanics.mixin.movement.EntityDimensionsAccessor;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.network.MovementNetworkHandler;

/**
 * Client-side handler for player movement input and camera effects.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public class MovementClientHandler {
    
    private static PlayerState clientState = new PlayerState();
    private static int lastSentCode = 0;
    
    // Slide mechanics
    private static Vec3 slideDirection = Vec3.ZERO;
    private static double slideAmplifier = 0;
    private static double slideCharging = 0;
    
    // Camera offsets
    public static double cameraOffsetY = 0;
    public static float cameraProbeOffset = 0;
    private static long lastSyncTime = System.currentTimeMillis();
    
    // Input lock flags
    private static boolean sitKeyLock = false;
    private static boolean crawlKeyLock = false;
    private static boolean probeKeyLock = false;
    
    // Crawling view limit
    public static int crawlingMousePosXMove = 0;
    
    public static PlayerState getClientState() {
        return clientState;
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
        
        // Update slide charging when sprinting
        if (mc.player.isSprinting() && !clientState.isSitting()) {
            slideCharging += 1.0 / 20.0;
            slideCharging = Math.min(slideCharging, 1.0);
        } else {
            slideCharging = 0;
        }
        
        // Handle slide movement
        if (slideAmplifier > 0 && clientState.isSitting()) {
            applySlideMovement(mc.player);
        }
        
        // Update player dimensions
        updateClientDimensions(mc.player);
        
        // Sync state to server
        syncStateToServer();
    }
    
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!Config.Movement.enabled) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        
        handleSitInput(mc, event);
        handleCrawlInput(mc, event);
        handleLeanInput(mc, event);
    }
    
    private static void handleSitInput(Minecraft mc, InputEvent.Key event) {
        if (!Config.Movement.sitEnabled) return;
        KeyMapping sitKey = MovementKeyBindings.SIT_KEY;
        
        if (sitKey.isDown() && !sitKeyLock && clientState.canSit()) {
            sitKeyLock = true;
            
            if (!clientState.isSitting()) {
                if (mc.player.onGround()) {
                    // Check collision for sitting
                    if (canFitInHeight(mc.player, 1.35f)) {
                        clientState.enableSit();
                        
                        // Sliding
                        if (mc.player.isSprinting() && Config.Movement.slideEnabled) {
                            slideAmplifier = Config.Movement.slideMaxForce;
                            slideDirection = new Vec3(
                                mc.player.getX() - mc.player.xOld, 0,
                                mc.player.getZ() - mc.player.zOld
                            ).normalize();
                        }
                    }
                }
            } else {
                // Stand up
                if (canFitInHeight(mc.player, 1.8f)) {
                    slideAmplifier = 0;
                    clientState.disableSit();
                }
            }
        }
        
        if (!sitKey.isDown()) {
            sitKeyLock = false;
        }
    }
    
    private static void handleCrawlInput(Minecraft mc, InputEvent.Key event) {
        if (!Config.Movement.crawlEnabled) return;
        KeyMapping crawlKey = MovementKeyBindings.CRAWL_KEY;
        
        if (crawlKey.isDown() && !crawlKeyLock && clientState.canCrawl()) {
            crawlKeyLock = true;
            
            if (!clientState.isCrawling()) {
                if (mc.player.onGround()) {
                    clientState.enableCrawling();
                    crawlingMousePosXMove = 0;
                    
                    // Dive if sprinting
                    if (mc.player.isSprinting()) {
                        Vec3 dir = new Vec3(
                            mc.player.getX() - mc.player.xOld, 0,
                            mc.player.getZ() - mc.player.zOld
                        ).normalize();
                        mc.player.setDeltaMovement(
                            dir.x * slideCharging,
                            0.2 * slideCharging,
                            dir.z * slideCharging
                        );
                    }
                }
            } else {
                if (canFitInHeight(mc.player, 1.8f)) {
                    clientState.disableCrawling();
                }
            }
        }
        
        if (!crawlKey.isDown()) {
            crawlKeyLock = false;
        }
    }
    
    private static void handleLeanInput(Minecraft mc, InputEvent.Key event) {
        KeyMapping leftLean = MovementKeyBindings.LEAN_LEFT_KEY;
        KeyMapping rightLean = MovementKeyBindings.LEAN_RIGHT_KEY;
        
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
        
        if (clientState.isSitting()) {
            event.getInput().forwardImpulse *= 0.3f;
            event.getInput().leftImpulse *= 0.3f;
            
            if (event.getInput().jumping) {
                slideAmplifier = 0;
            }
        }
        
        if (clientState.isCrawling()) {
            event.getInput().forwardImpulse *= 0.4f;
            event.getInput().leftImpulse *= 0.4f;
            
            if (event.getInput().jumping) {
                event.getInput().jumping = false;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && canFitInHeight(mc.player, 1.8f)) {
                    clientState.disableCrawling();
                }
            }
        }
        
        if (clientState.getProbe() != 0) {
            event.getInput().forwardImpulse *= 0.9f;
            event.getInput().leftImpulse *= 0.9f;
        }
    }
    
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!Config.Movement.enabled) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
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
        
        // Apply lean rotation
        float roll = (float) event.getRoll();
        event.setRoll(roll + 10 * cameraProbeOffset);
    }
    
    private static void applySlideMovement(LocalPlayer player) {
        player.setDeltaMovement(
            slideDirection.x * slideAmplifier * 0.8,
            player.getDeltaMovement().y,
            slideDirection.z * slideAmplifier * 0.8
        );
        
        slideAmplifier -= 0.1;
        if (slideAmplifier <= 0) {
            slideAmplifier = 0;
            if (!Config.Movement.sitAutoHold) {
                clientState.disableSit();
            }
        }
    }
    
    private static void updateClientDimensions(LocalPlayer player) {
        MovementStateManager.updateState(player.getUUID(), clientState.writeCode());

        EntityDimensions newDims = clientState.getCustomDimensions();
        if (newDims != null) {
            float newEyeHeight = clientState.getCustomEyeHeight();
            ((EntityDimensionsAccessor) player).tacz$setDimensions(newDims);
            ((EntityDimensionsAccessor) player).tacz$setEyeHeight(newEyeHeight);
            player.setPos(player.getX(), player.getY(), player.getZ());
        } else {
            player.refreshDimensions();
        }
    }
    
    private static boolean canFitInHeight(Player player, float height) {
        float width = player.getBbWidth();
        AABB box = new AABB(
            player.getX() - width / 2, player.getY(), player.getZ() - width / 2,
            player.getX() + width / 2, player.getY() + height, player.getZ() + width / 2
        );
        return player.level().noCollision(player, box);
    }
    
    private static void syncStateToServer() {
        int code = clientState.writeCode();
        if (code != lastSentCode) {
            MovementNetworkHandler.sendStateToServer(code);
            lastSentCode = code;
        }
    }
    
    private static void resetState() {
        clientState.reset();
        slideAmplifier = 0;
        slideCharging = 0;
        cameraOffsetY = 0;
        cameraProbeOffset = 0;
        lastSentCode = 0;
    }
    
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!Config.Movement.enabled) return;
        
        Player player = event.getEntity();
        PlayerState state = getStateForPlayer(player);
        if (state == null) return;
        
        PoseStack poseStack = event.getPoseStack();
        float yBodyRot = player.yBodyRot;
        float probeOffset = state.getProbeOffset();
        
        // Apply sitting translation
        if (state.isSitting()) {
            poseStack.translate(0, -0.5, 0);
        }
        
        // Apply crawling rotation
        if (state.isCrawling()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yBodyRot));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.translate(0, -1.3, 0.1);
            poseStack.translate(probeOffset * 0.4, 0, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-(180.0F - yBodyRot)));
        }
        // Apply leaning rotation
        else if (probeOffset != 0) {
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
