package ru.liko.tacz_mechanics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.client.freeaim.FreeAimHandler;

/**
 * Applies free-aim rotation to the first-person gun model.
 * Injects at HEAD and TAIL: pushPose before transforms, popPose after, so TacZ rendering is not broken.
 */
@Mixin(value = GunItemRendererWrapper.class, remap = false)
public class FreeAimGunModelMixin {

    @Unique
    private static boolean taczMechanics$pushed;

    @Inject(method = "renderFirstPerson", at = @At("HEAD"), require = 0)
    private void taczMechanics$pushAndRotate(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx,
                                               PoseStack poseStack, MultiBufferSource bufferSource,
                                               int light, float partialTick, CallbackInfo ci) {
        taczMechanics$pushed = false;
        if (!Config.FreeAim.enabled || !(stack.getItem() instanceof AbstractGunItem)) {
            return;
        }
        try {
            FreeAimHandler handler = FreeAimHandler.getInstance();
            float pitchOffset = handler.getInterpolatedPitch(partialTick);
            float yawOffset = handler.getInterpolatedYaw(partialTick);
            if (Math.abs(pitchOffset) < 0.001f && Math.abs(yawOffset) < 0.001f) {
                return;
            }

            float aimingProgress = 0f;
            try {
                aimingProgress = IClientPlayerGunOperator.fromLocalPlayer(player).getClientAimingProgress(partialTick);
            } catch (Exception ignored) {}

            float aimFactor = 1.0f - (aimingProgress * 0.7f);
            // Коэффициенты чувствительности
            float pitchSens = 0.5f;
            float yawSens = 0.4f;
            float pivotZ = 0.3f;
            float pivotY = -0.1f;

            poseStack.pushPose();
            taczMechanics$pushed = true;
            poseStack.translate(0, pivotY, pivotZ);
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitchOffset * pitchSens * aimFactor));
            poseStack.mulPose(Axis.YP.rotationDegrees(yawOffset * yawSens * aimFactor));
            poseStack.translate(0, -pivotY, -pivotZ);
        } catch (Exception ignored) {
            if (taczMechanics$pushed) {
                try { poseStack.popPose(); } catch (Exception e2) {}
                taczMechanics$pushed = false;
            }
        }
    }

    @Inject(method = "renderFirstPerson", at = @At("TAIL"), require = 0)
    private void taczMechanics$popPose(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx,
                                        PoseStack poseStack, MultiBufferSource bufferSource,
                                        int light, float partialTick, CallbackInfo ci) {
        if (taczMechanics$pushed) {
            taczMechanics$pushed = false;
            try {
                poseStack.popPose();
            } catch (Exception ignored) {}
        }
    }
}
