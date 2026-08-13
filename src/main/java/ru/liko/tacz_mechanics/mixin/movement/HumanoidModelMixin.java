package ru.liko.tacz_mechanics.mixin.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;

/**
 * The legacy leg-bend lean, kept for {@code movement.leanBodyRoll = false}.
 *
 * <p>Only touches the legs' {@code zRot}, which {@code setupAnim} rewrites every frame, so there is
 * nothing here that can accumulate. Whole-body leaning lives in {@code LeanBodyRollMixin}.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {

    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart rightLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void adjustAnimations(T entity, float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!Config.Movement.enabled) return;
        if (!(entity instanceof Player player)) return;

        PlayerState state = MovementClientHandler.getStateForPlayer(player);
        if (state == null) {
            state = MovementStateManager.get(player.getUUID());
        }
        if (state == null) return;

        // Animation is advanced once per tick in the client/server tick handlers. Lerping between
        // the previous and current tick keeps the model smooth without overshooting the per-tick
        // collision clamp, which would jitter near walls.
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float probeOffset = Mth.lerp(partial, state.getProbeOffsetOld(), state.getProbeOffset());
        if (probeOffset == 0f) return;

        // Whole-body leaning is a rotation of the model, done in LeanBodyRollMixin. Nothing to pose
        // here, and nothing that could accumulate across frames.
        if (Config.Movement.leanBodyRoll) {
            return;
        }

        // Original look: only a leg bends, the body stays upright. Safe to add to, because setupAnim
        // rewrites both legs' zRot every frame.
        float tilt = probeOffset * 20f * Mth.DEG_TO_RAD;
        if (probeOffset >= 0) {
            rightLeg.zRot += tilt;
        } else {
            leftLeg.zRot += tilt;
        }
    }
}
