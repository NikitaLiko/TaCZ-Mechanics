package ru.liko.tacz_mechanics.movement.server;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.mixin.movement.EntityDimensionsAccessor;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;

/**
 * Server-side handler for player movement states.
 * Manages hitbox updates and eye height changes.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID)
public class MovementServerHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedOnce = false;
    
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
        
        state.updateOffset();
        updatePlayerDimensions(player, state);
    }
    
    private static void updatePlayerDimensions(Player player, PlayerState state) {
        EntityDimensions newDims = state.getCustomDimensions();
        if (newDims == null) {
            loggedOnce = false;
            return;
        }
        if (!loggedOnce) {
            LOGGER.debug("[TaCZ] Applying custom dimensions for player {}", player.getName().getString());
            loggedOnce = true;
        }
        float newEyeHeight = state.getCustomEyeHeight();
        ((EntityDimensionsAccessor) player).tacz$setDimensions(newDims);
        ((EntityDimensionsAccessor) player).tacz$setEyeHeight(newEyeHeight);
        player.setPos(player.getX(), player.getY(), player.getZ());
    }
    
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!Config.Movement.enabled) return;
        if (!(event.getEntity() instanceof Player player)) return;
        
        PlayerState state = MovementStateManager.get(player.getUUID());
        if (state == null) return;
        
        EntityDimensions dims = state.getCustomDimensions();
        if (dims != null) {
            event.setNewSize(dims);
        }
    }
}
