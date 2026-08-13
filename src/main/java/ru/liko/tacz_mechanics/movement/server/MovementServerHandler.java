package ru.liko.tacz_mechanics.movement.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.movement.LeanCollision;
import ru.liko.tacz_mechanics.movement.MovementPosture;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.network.ModNetworking;

/**
 * Server-side per-tick maintenance for movement state: lean offset interpolation,
 * collision-based clamping, and dimension refresh so the mixin override is reapplied.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID)
public class MovementServerHandler {
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MovementStateManager.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!Config.Movement.enabled) return;

        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        PlayerState state = MovementStateManager.get(player.getUUID());
        if (state == null) return;

        // Authority check: water, vehicles, elytra… cancel the posture even if the client kept it.
        if (!state.isStanding() && !MovementPosture.canHoldPosture(player)) {
            state.standUp();
            if (player instanceof ServerPlayer serverPlayer) {
                ModNetworking.syncMovementState(serverPlayer, state.writeCode());
            }
        }

        state.updateOffset();
        float maxLeft = LeanCollision.maxLeanMagnitude(player, -1f);
        float maxRight = LeanCollision.maxLeanMagnitude(player, 1f);
        state.clampProbeOffset(maxLeft, maxRight);
        MovementPosture.applyForcedPose(player, state);
        player.refreshDimensions();
    }
}
