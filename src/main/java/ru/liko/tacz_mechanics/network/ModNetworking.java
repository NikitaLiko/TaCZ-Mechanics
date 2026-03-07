package ru.liko.tacz_mechanics.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.network.MovementStateBroadcastPayload;
import ru.liko.tacz_mechanics.movement.network.MovementStatePayload;

@EventBusSubscriber(modid = TaczMechanics.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {
    private static final String VERSION = "1.0.0";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToClient(
                DistantFireSoundPacket.TYPE,
                DistantFireSoundPacket.STREAM_CODEC,
                DistantFireSoundPacket::handle);

        registrar.playToClient(
                SuppressionPacket.TYPE,
                SuppressionPacket.STREAM_CODEC,
                SuppressionPacket::handle);

        registrar.playToServer(
                FreeAimSyncPacket.TYPE,
                FreeAimSyncPacket.STREAM_CODEC,
                FreeAimSyncPacket::handle);

        registrar.playBidirectional(
                MovementStatePayload.TYPE,
                MovementStatePayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ModNetworking::handleMovementClientState,
                        ModNetworking::handleMovementServerState));

        registrar.playToClient(
                MovementStateBroadcastPayload.TYPE,
                MovementStateBroadcastPayload.STREAM_CODEC,
                ModNetworking::handleMovementBroadcast);
    }

    private static void handleMovementServerState(MovementStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                PlayerState state = MovementStateManager.getOrCreate(serverPlayer.getUUID());
                int oldCode = state.writeCode();
                state.readCode(payload.stateCode());

                if (oldCode != payload.stateCode()) {
                    PacketDistributor.sendToAllPlayers(
                            new MovementStateBroadcastPayload(serverPlayer.getUUID(), payload.stateCode()));
                }
            }
        });
    }

    private static void handleMovementClientState(MovementStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                PlayerState state = MovementStateManager.getOrCreate(context.player().getUUID());
                state.readCode(payload.stateCode());
            }
        });
    }

    private static void handleMovementBroadcast(MovementStateBroadcastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null && !context.player().getUUID().equals(payload.playerId())) {
                MovementStateManager.updateState(payload.playerId(), payload.stateCode());
            }
        });
    }

    public static void sendMovementStateToServer(int stateCode) {
        PacketDistributor.sendToServer(new MovementStatePayload(stateCode));
    }
}
