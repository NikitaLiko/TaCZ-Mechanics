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
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;

/**
 * Mixin to adjust player model rotation angles based on movement state.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    
    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart head;
    
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
        
        // Update offset animation for this state
        state.updateOffset();
        
        float probeOffset = state.getProbeOffset();
        
        // Sitting pose
        if (state.isSitting()) {
            rightLeg.xRot = -1.4137167F;
            rightLeg.yRot = (float) Math.PI / 10F;
            rightLeg.zRot = 0.07853982F;
            leftLeg.xRot = -1.4137167F;
            leftLeg.yRot = -(float) Math.PI / 10F;
            leftLeg.zRot = -0.07853982F;
        }
        
        // Crawling pose
        if (state.isCrawling()) {
            head.xRot -= (float) (70 * Math.PI / 180);
            rightArm.xRot *= 0.2f;
            leftArm.xRot *= 0.2f;
            rightArm.xRot += (float) (180 * Math.PI / 180);
            leftArm.xRot += (float) (180 * Math.PI / 180);
            rightLeg.xRot *= 0.2f;
            leftLeg.xRot *= 0.2f;
        }
        
        // Leaning animation - only legs, like original ModularMovements
        // Body tilt is done via camera roll, not model rotation
        if (probeOffset >= 0) {
            rightLeg.zRot += (float) (probeOffset * 20 * Math.PI / 180);
        } else {
            leftLeg.zRot += (float) (probeOffset * 20 * Math.PI / 180);
        }
    }
}
