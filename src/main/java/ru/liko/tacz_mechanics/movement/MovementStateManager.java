package ru.liko.tacz_mechanics.movement;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Manages movement states for all players.
 * Used on both client and server sides.
 */
public class MovementStateManager {
    private static final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();
    
    public static PlayerState getOrCreate(UUID playerId) {
        return playerStates.computeIfAbsent(playerId, k -> new PlayerState());
    }
    
    public static PlayerState get(UUID playerId) {
        return playerStates.get(playerId);
    }
    
    public static void remove(UUID playerId) {
        playerStates.remove(playerId);
    }
    
    public static void clear() {
        playerStates.clear();
    }
    
    public static boolean isSitting(UUID playerId) {
        PlayerState state = playerStates.get(playerId);
        return state != null && state.isSitting();
    }
    
    public static boolean isCrawling(UUID playerId) {
        PlayerState state = playerStates.get(playerId);
        return state != null && state.isCrawling();
    }
    
    public static float getProbeOffset(UUID playerId) {
        PlayerState state = playerStates.get(playerId);
        return state != null ? state.getProbeOffset() : 0f;
    }
    
    public static void updateState(UUID playerId, int code) {
        PlayerState state = getOrCreate(playerId);
        state.readCode(code);
    }
}
