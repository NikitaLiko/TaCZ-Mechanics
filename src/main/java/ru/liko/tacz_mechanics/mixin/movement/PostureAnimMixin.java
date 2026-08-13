package ru.liko.tacz_mechanics.mixin.movement;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementPosture;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;

/**
 * Bends the rendered model into the sit / prone posture, mirroring {@code PlayerSkeleton.applyPosture}
 * bone-for-bone so the model and the hitbox stay on the same pose. The whole-body drop / flat is done
 * in {@code PostureBodyMixin}; this only touches bones {@code setupAnim} rewrites every frame.
 */
@Mixin(HumanoidModel.class)
public abstract class PostureAnimMixin<T extends LivingEntity> {

    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void taczMechanics$posture(T entity, float limbSwing, float limbSwingAmount,
                                       float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!Config.Movement.enabled) return;
        if (!(entity instanceof Player player)) return;
        if (player.isPassenger() || player.isSleeping()) return;

        PlayerState state = MovementClientHandler.getStateForPlayer(player);
        if (state == null) {
            state = MovementStateManager.get(player.getUUID());
        }
        if (state == null || state.isStanding()) return;

        if (state.isSitting()) {
            rightLeg.xRot = MovementPosture.SIT_LEG_X;
            rightLeg.yRot = MovementPosture.SIT_LEG_Y;
            rightLeg.zRot = MovementPosture.SIT_LEG_Z;
            leftLeg.xRot = MovementPosture.SIT_LEG_X;
            leftLeg.yRot = -MovementPosture.SIT_LEG_Y;
            leftLeg.zRot = -MovementPosture.SIT_LEG_Z;
        } else {
            head.xRot += MovementPosture.PRONE_HEAD_X;
            rightArm.xRot = rightArm.xRot * MovementPosture.PRONE_ARM_FACTOR + MovementPosture.PRONE_ARM_X;
            leftArm.xRot = leftArm.xRot * MovementPosture.PRONE_ARM_FACTOR + MovementPosture.PRONE_ARM_X;
            rightArm.yRot = 0f;
            leftArm.yRot = 0f;
            rightLeg.xRot *= MovementPosture.PRONE_LEG_FACTOR;
            leftLeg.xRot *= MovementPosture.PRONE_LEG_FACTOR;
        }
    }
}
