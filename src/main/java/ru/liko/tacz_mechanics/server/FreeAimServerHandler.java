package ru.liko.tacz_mechanics.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import ru.liko.tacz_mechanics.Config;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side handler for free aim state.
 * Stores and provides free aim offsets for each player.
 */
public class FreeAimServerHandler {
    
    // Store free aim offsets per player UUID
    private static final Map<UUID, FreeAimData> PLAYER_FREE_AIM = new ConcurrentHashMap<>();
    
    /**
     * Update a player's free aim offset.
     * Called when receiving FreeAimSyncPacket from client.
     */
    public static void updatePlayerFreeAim(ServerPlayer player, float pitchOffset, float yawOffset) {
        if (player == null) return;
        
        // Clamp to max angle for security (prevent client from sending invalid values)
        float maxAngle = (float) Config.FreeAim.maxAngle;
        pitchOffset = Mth.clamp(pitchOffset, -maxAngle, maxAngle);
        yawOffset = Mth.clamp(yawOffset, -maxAngle, maxAngle);
        
        PLAYER_FREE_AIM.put(player.getUUID(), new FreeAimData(pitchOffset, yawOffset, System.currentTimeMillis()));
    }
    
    /**
     * Get a player's current pitch offset.
     */
    public static float getPitchOffset(ServerPlayer player) {
        if (player == null || !Config.FreeAim.enabled) return 0;
        
        FreeAimData data = PLAYER_FREE_AIM.get(player.getUUID());
        if (data == null) return 0;
        
        // Invalidate old data (more than 1 second old)
        if (System.currentTimeMillis() - data.timestamp > 1000) {
            PLAYER_FREE_AIM.remove(player.getUUID());
            return 0;
        }
        
        return data.pitchOffset;
    }
    
    /**
     * Get a player's current yaw offset.
     */
    public static float getYawOffset(ServerPlayer player) {
        if (player == null || !Config.FreeAim.enabled) return 0;
        
        FreeAimData data = PLAYER_FREE_AIM.get(player.getUUID());
        if (data == null) return 0;
        
        // Invalidate old data (more than 1 second old)
        if (System.currentTimeMillis() - data.timestamp > 1000) {
            PLAYER_FREE_AIM.remove(player.getUUID());
            return 0;
        }
        
        return data.yawOffset;
    }
    
    // Sensitivity multipliers - must match FreeAimGunModelMixin for visual accuracy
    private static final float PITCH_SENSITIVITY = 0.5f;
    private static final float YAW_SENSITIVITY = 0.4f;
    
    /**
     * Get adjusted pitch for a player (player pitch + free aim offset).
     * Uses negative offset and sensitivity to match visual gun rotation.
     */
    public static float getAdjustedPitch(ServerPlayer player, float basePitch) {
        return basePitch - getPitchOffset(player) * PITCH_SENSITIVITY;
    }
    
    /**
     * Get adjusted yaw for a player (player yaw + free aim offset).
     * Uses sensitivity to match visual gun rotation.
     */
    public static float getAdjustedYaw(ServerPlayer player, float baseYaw) {
        return baseYaw + getYawOffset(player) * YAW_SENSITIVITY;
    }
    
    /**
     * Remove a player's free aim data (called on disconnect).
     */
    public static void removePlayer(ServerPlayer player) {
        if (player != null) {
            PLAYER_FREE_AIM.remove(player.getUUID());
        }
    }
    
    /**
     * Clear all stored data.
     */
    public static void clear() {
        PLAYER_FREE_AIM.clear();
    }
    
    /**
     * Data class for storing free aim offset with timestamp.
     */
    private record FreeAimData(float pitchOffset, float yawOffset, long timestamp) {}
}
