package ru.liko.tacz_mechanics.mixin.movement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementPosture;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;

/**
 * Drops (sit) or lays flat (prone) the whole rendered body, at the return of {@code setupRotations} —
 * the same chain point where {@code PlayerSkeleton.baseTransform} applies the identical transform, so
 * the model and the hitbox cannot drift apart. Posture excludes lean, so this never fights
 * {@code LeanBodyRollMixin} (which no-ops when the probe is zero).
 */
@Mixin(PlayerRenderer.class)
public abstract class PostureBodyMixin {

    @Inject(method = "setupRotations", at = @At("RETURN"))
    private void taczMechanics$posture(AbstractClientPlayer player, PoseStack poseStack, float bob,
                                       float bodyYRot, float partialTick, float scale, CallbackInfo ci) {
        if (!Config.Movement.enabled) return;
        if (player.isPassenger() || player.isSleeping()) return;

        PlayerState state = MovementClientHandler.getStateForPlayer(player);
        if (state == null) {
            state = MovementStateManager.get(player.getUUID());
        }
        if (state == null || state.isStanding()) return;

        if (state.isProne()) {
            poseStack.mulPose(Axis.XP.rotation(MovementPosture.PRONE_PITCH));
            poseStack.translate(MovementPosture.PRONE_TX, MovementPosture.PRONE_TY, MovementPosture.PRONE_TZ);
        } else {
            poseStack.translate(0f, MovementPosture.SIT_DROP, 0f);
        }
    }
}
