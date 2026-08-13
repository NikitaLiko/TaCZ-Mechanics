package ru.liko.tacz_mechanics.mixin.movement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;

/**
 * Rolls the first-person hand/gun while leaning.
 * The lean roll from {@link MovementClientHandler#onCameraSetup} only reaches the world
 * camera — GameRenderer resets the pose stack before rendering the hand, so the arm would
 * otherwise stay locked to the camera and look static while leaning.
 */
@Mixin(ItemInHandRenderer.class)
public class LeanHandRollMixin {

    @Unique
    private static boolean taczMechanics$rolled;

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void taczMechanics$pushRoll(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                        LocalPlayer player, int light, CallbackInfo ci) {
        taczMechanics$rolled = false;
        if (!Config.Movement.enabled) {
            return;
        }
        float lean = MovementClientHandler.cameraProbeOffset;
        float roll = lean * (float) Config.Movement.leanHandRoll;
        float shift = lean * (float) Config.Movement.leanHandOffset;
        if (Math.abs(roll) < 0.001f && Math.abs(shift) < 0.0001f) {
            return;
        }
        // Camera space: origin is the eye, so rolling about Z needs no pivot.
        // Shift is opposite the lean — the body drags the gun behind the head.
        poseStack.pushPose();
        taczMechanics$rolled = true;
        poseStack.translate(-shift, 0.0f, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void taczMechanics$popRoll(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                       LocalPlayer player, int light, CallbackInfo ci) {
        if (taczMechanics$rolled) {
            taczMechanics$rolled = false;
            poseStack.popPose();
        }
    }
}
