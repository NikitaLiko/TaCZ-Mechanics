package ru.liko.tacz_mechanics.movement.network;

import ru.liko.tacz_mechanics.network.ModNetworking;

/**
 * Thin delegate for movement network operations.
 * All registration is handled by {@link ModNetworking}.
 */
public class MovementNetworkHandler {

    public static void sendStateToServer(int stateCode) {
        ModNetworking.sendMovementStateToServer(stateCode);
    }
}
