package ru.liko.tacz_mechanics.network;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.movement.MovementPosture;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;
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

        registrar.playToClient(
                TweaksSyncPayload.TYPE,
                TweaksSyncPayload.STREAM_CODEC,
                TweaksSyncPayload::handle);

        registrar.playToClient(
                DistantFireSyncPayload.TYPE,
                DistantFireSyncPayload.STREAM_CODEC,
                DistantFireSyncPayload::handle);

        registrar.playToServer(
                FreeAimSyncPacket.TYPE,
                FreeAimSyncPacket.STREAM_CODEC,
                FreeAimSyncPacket::handle);

        registrar.playToClient(
                FreeAimBroadcastPacket.TYPE,
                FreeAimBroadcastPacket.STREAM_CODEC,
                FreeAimBroadcastPacket::handle);

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
                if (oldCode == payload.stateCode()) {
                    return;
                }
                if (!MovementPosture.canApplyMovementCode(serverPlayer, oldCode, payload.stateCode())) {
                    PacketDistributor.sendToPlayer(serverPlayer, new MovementStatePayload(oldCode));
                    return;
                }
                // Client cooldowns are advisory only — enforce the posture transition rate here so
                // modified clients (or packet spam) can't chain лег/сел faster than honest ones.
                // 80% of the configured cooldown leaves headroom for latency jitter.
                boolean postureChanged = oldCode / 10 != payload.stateCode() / 10;
                long minInterval = (long) (Math.min(Config.Movement.sitCooldown, Config.Movement.proneCooldown) * 1000 * 0.8);
                if (postureChanged && state.sincePostureChange() < minInterval) {
                    PacketDistributor.sendToPlayer(serverPlayer, new MovementStatePayload(oldCode));
                    return;
                }
                state.readCode(payload.stateCode());
                MovementPosture.applyForcedPose(serverPlayer, state);
                serverPlayer.refreshDimensions();
                MovementPosture.logHitbox("SERVER", serverPlayer, state);

                PacketDistributor.sendToAllPlayers(
                        new MovementStateBroadcastPayload(serverPlayer.getUUID(), payload.stateCode()));
            }
        });
    }

    private static void handleMovementClientState(MovementStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof LocalPlayer) {
                MovementClientHandler.applySyncedStateFromServer(payload.stateCode());
            }
        });
    }

    private static void handleMovementBroadcast(MovementStateBroadcastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null && !context.player().getUUID().equals(payload.playerId())) {
                MovementStateManager.updateState(payload.playerId(), payload.stateCode());
                var level = context.player().level();
                if (level.isClientSide()) {
                    for (var p : level.players()) {
                        if (p.getUUID().equals(payload.playerId())) {
                            p.refreshDimensions();
                            break;
                        }
                    }
                }
            }
        });
    }

    /** Server → everyone: force a player's movement state (rollback / server-side cancel). */
    public static void syncMovementState(ServerPlayer player, int stateCode) {
        PacketDistributor.sendToPlayer(player, new MovementStatePayload(stateCode));
        PacketDistributor.sendToAllPlayers(new MovementStateBroadcastPayload(player.getUUID(), stateCode));
    }

    public static void sendMovementStateToServer(int stateCode) {
        PacketDistributor.sendToServer(new MovementStatePayload(stateCode));
    }
}
