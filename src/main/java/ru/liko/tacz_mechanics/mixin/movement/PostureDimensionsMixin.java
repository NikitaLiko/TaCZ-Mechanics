package ru.liko.tacz_mechanics.mixin.movement;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementPosture;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;

/**
 * Shrinks the player's box while sitting or prone, at the one method every size query funnels
 * through: {@code Player.getDefaultDimensions}, which {@code LivingEntity.getDimensions} scales and
 * returns.
 *
 * <p>Why here and not {@code EntityEvent.Size}: that event only fires from {@code refreshDimensions},
 * but {@code Player.updatePlayerPose} measures fit with {@code getDimensions(pose)} directly. With the
 * un-shrunk box it decides a standing player can't fit under a low ceiling and forces CROUCHING — the
 * "squished into sneak while crawling through a 1-block gap" bug. Overriding the shared method makes
 * every caller — the fit checks, {@code refreshDimensions}, collision — agree on the posture box.
 *
 * <p>Runs on both sides (client for prediction and camera, server for the authoritative box and the
 * hitbox anchor). Returns the box unscaled; {@code getDimensions} applies the entity scale after.
 */
@Mixin(Player.class)
public abstract class PostureDimensionsMixin {

    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void taczMechanics$posture(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!Config.Movement.enabled) {
            return;
        }
        Player self = (Player) (Object) this;
        PlayerState state = MovementStateManager.get(self.getUUID());
        if (state == null || state.isStanding()) {
            return;
        }
        float width = state.isSitting() ? MovementPosture.SIT_WIDTH : MovementPosture.PRONE_WIDTH;
        float height = state.isSitting() ? MovementPosture.SIT_HEIGHT : MovementPosture.PRONE_HEIGHT;
        float eye = state.isSitting() ? MovementPosture.SIT_EYE : MovementPosture.PRONE_EYE;
        cir.setReturnValue(EntityDimensions.scalable(width, height).withEyeHeight(eye));
    }
}
