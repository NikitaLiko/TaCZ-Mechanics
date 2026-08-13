package ru.liko.tacz_mechanics.mixin.movement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.hitbox.SkeletonPose;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;

/**
 * Leans the rendered player: one rotation of the whole model about its feet.
 *
 * <p>Injected at the return of {@code setupRotations}, so it lands after the body yaw and after the
 * elytra/swim tilt — the exact point where {@code PlayerSkeleton.baseTransform} applies the same
 * {@code rotateZ}. Matching positions in a matching chain is what keeps the model and the hitbox
 * together, with no shared state and no per-bone bookkeeping to get wrong.
 *
 * <p>Rotating the pose stack touches nothing that outlives the frame, which is why this cannot
 * accumulate the way posing individual {@code ModelPart}s did.
 */
@Mixin(PlayerRenderer.class)
public abstract class LeanBodyRollMixin {

    @Inject(method = "setupRotations", at = @At("RETURN"))
    private void taczMechanics$leanBodyRoll(AbstractClientPlayer player, PoseStack poseStack, float bob,
                                            float bodyYRot, float partialTick, float scale, CallbackInfo ci) {
        if (!Config.Movement.enabled || !Config.Movement.leanBodyRoll) {
            return;
        }
        PlayerState state = MovementClientHandler.getStateForPlayer(player);
        if (state == null) {
            state = MovementStateManager.get(player.getUUID());
        }
        if (state == null) {
            return;
        }

        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float probeOffset = Mth.lerp(partial, state.getProbeOffsetOld(), state.getProbeOffset());
        float roll = SkeletonPose.leanRoll(player, probeOffset);
        if (roll != 0f) {
            poseStack.mulPose(Axis.ZP.rotation(roll));
        }
    }
}
