package ru.liko.tacz_mechanics.client.freeaim;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.network.FreeAimSyncPacket;

/**
 * Free Aim System - gun lags behind camera movement for realistic feel.
 */
public class FreeAimHandler {
    
    private static final FreeAimHandler INSTANCE = new FreeAimHandler();
    
    // Offset in degrees
    private float pitchOffset = 0f;
    private float yawOffset = 0f;
    
    // For interpolation
    private float prevPitchOffset = 0f;
    private float prevYawOffset = 0f;
    
    // Previous player rotation
    private float lastPitch = Float.NaN;
    private float lastYaw = Float.NaN;
    
    // Sync
    private int syncTimer = 0;
    
    public static FreeAimHandler getInstance() {
        return INSTANCE;
    }
    
    /**
     * Called every client tick.
     */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        // Early exit conditions
        if (!Config.FreeAim.enabled || player == null || mc.isPaused()) {
            reset();
            return;
        }
        
        // Must be holding TACZ gun
        if (!isHoldingGun(player)) {
            reset();
            return;
        }
        
        // Disable when aiming down sights
        if (Config.FreeAim.disableWhenAiming && isAiming(player)) {
            smoothReset();
            return;
        }
        
        // Save previous for interpolation
        prevPitchOffset = pitchOffset;
        prevYawOffset = yawOffset;
        
        // Get current rotation
        float currentPitch = player.getXRot();
        float currentYaw = player.getYRot();
        
        // First tick - initialize
        if (Float.isNaN(lastPitch)) {
            lastPitch = currentPitch;
            lastYaw = currentYaw;
            return;
        }
        
        // Calculate delta
        float deltaPitch = currentPitch - lastPitch;
        float deltaYaw = currentYaw - lastYaw;
        
        // Yaw wrap handling
        while (deltaYaw > 180) deltaYaw -= 360;
        while (deltaYaw < -180) deltaYaw += 360;
        
        // Save for next tick
        lastPitch = currentPitch;
        lastYaw = currentYaw;
        
        // Apply delta to offset (negative = gun lags behind)
        pitchOffset -= deltaPitch;
        yawOffset -= deltaYaw;
        
        // Clamp to max angle
        float max = (float) Config.FreeAim.maxAngle;
        pitchOffset = Mth.clamp(pitchOffset, -max, max);
        yawOffset = Mth.clamp(yawOffset, -max, max);
        
        // Lerp back to center (gun catches up)
        float lerp = (float) Config.FreeAim.lerpSpeed;
        pitchOffset *= (1f - lerp);
        yawOffset *= (1f - lerp);
        
        // Snap small values to zero
        if (Math.abs(pitchOffset) < 0.001f) pitchOffset = 0;
        if (Math.abs(yawOffset) < 0.001f) yawOffset = 0;
        
        // Sync to server
        if (++syncTimer >= 2) {
            syncTimer = 0;
            if (pitchOffset != 0 || yawOffset != 0) {
                try {
                    PacketDistributor.sendToServer(new FreeAimSyncPacket(pitchOffset, yawOffset));
                } catch (Exception e) {
                    LogUtils.getLogger().warn("Failed to send FreeAim sync packet", e);
                }
            }
        }
    }
    
    private boolean isHoldingGun(LocalPlayer player) {
        try {
            return IGun.mainHandHoldGun(player);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isAiming(LocalPlayer player) {
        try {
            IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(player);
            return op.getClientAimingProgress(1.0f) > 0.5f;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void smoothReset() {
        prevPitchOffset = pitchOffset;
        prevYawOffset = yawOffset;
        float lerp = (float) Config.FreeAim.lerpSpeed * 2f;
        pitchOffset *= (1f - lerp);
        yawOffset *= (1f - lerp);
        if (Math.abs(pitchOffset) < 0.001f) pitchOffset = 0;
        if (Math.abs(yawOffset) < 0.001f) yawOffset = 0;
    }
    
    public void reset() {
        pitchOffset = 0;
        yawOffset = 0;
        prevPitchOffset = 0;
        prevYawOffset = 0;
        lastPitch = Float.NaN;
        lastYaw = Float.NaN;
    }
    
    // === Getters ===
    
    public float getPitchOffset() {
        return pitchOffset;
    }
    
    public float getYawOffset() {
        return yawOffset;
    }
    
    public float getInterpolatedPitch(float pt) {
        return Mth.lerp(pt, prevPitchOffset, pitchOffset);
    }
    
    public float getInterpolatedYaw(float pt) {
        return Mth.lerp(pt, prevYawOffset, yawOffset);
    }
    
    /**
     * Crosshair X offset (screen pixels).
     */
    public float getCrosshairX(float pt) {
        if (!isEnabled()) return 0;
        // Yaw offset -> horizontal screen movement (inverted)
        return -getInterpolatedYaw(pt) * (float) Config.FreeAim.crosshairScale;
    }
    
    /**
     * Crosshair Y offset (screen pixels).
     */
    public float getCrosshairY(float pt) {
        if (!isEnabled()) return 0;
        // Pitch offset -> vertical screen movement (inverted)
        return -getInterpolatedPitch(pt) * (float) Config.FreeAim.crosshairScale;
    }
    
    /**
     * Check if free aim should be active right now.
     */
    public boolean isEnabled() {
        if (!Config.FreeAim.enabled) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (!isHoldingGun(player)) return false;
        if (Config.FreeAim.disableWhenAiming && isAiming(player)) return false;
        return true;
    }
    
    /**
     * Has any offset right now?
     */
    public boolean hasOffset() {
        return pitchOffset != 0 || yawOffset != 0;
    }
}
