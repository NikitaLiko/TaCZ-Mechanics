package ru.liko.tacz_mechanics.mixin.movement;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;

/**
 * Mixin to modify player dimensions based on movement state (sitting, crawling).
 */
@Mixin(Player.class)
public abstract class PlayerDimensionsMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerDimensionsMixin");
    
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void modifyDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!Config.Movement.enabled) return;
        
        Player player = (Player) (Object) this;
        PlayerState state = MovementStateManager.get(player.getUUID());
        
        if (state == null) return;
        
        EntityDimensions original = cir.getReturnValue();
        
        if (state.isCrawling()) {
            if (Config.debug) {
                LOGGER.debug("[DimensionsMixin] CRAWLING: setting height={}, eye={} (was h={}, e={})", Config.Movement.crawlHeight, Config.Movement.crawlEyeHeight, original.height(), original.eyeHeight());
            }
            cir.setReturnValue(EntityDimensions.scalable(original.width(), Config.Movement.crawlHeight).withEyeHeight(Config.Movement.crawlEyeHeight));
        } else if (state.isSitting()) {
            if (Config.debug) {
                LOGGER.debug("[DimensionsMixin] SITTING: setting height={}, eye={} (was h={}, e={})", Config.Movement.sitHeight, Config.Movement.sitEyeHeight, original.height(), original.eyeHeight());
            }
            cir.setReturnValue(EntityDimensions.scalable(original.width(), Config.Movement.sitHeight).withEyeHeight(Config.Movement.sitEyeHeight));
        }
    }
}
