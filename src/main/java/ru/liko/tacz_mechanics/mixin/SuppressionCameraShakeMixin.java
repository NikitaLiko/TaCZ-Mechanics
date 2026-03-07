package ru.liko.tacz_mechanics.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.client.suppression.SuppressionHandler;

/**
 * Applies camera kick (aim punch) when player is suppressed.
 * Resets view direction on suppression hit - like Squad's suppression effect.
 */
@Mixin(Camera.class)
public class SuppressionCameraShakeMixin {

    @Shadow private float yRot;
    @Shadow private float xRot;

    @Inject(method = "setup", at = @At("TAIL"))
    private void applySuppressionKick(BlockGetter level, Entity entity, boolean detached,
                                       boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!Config.Suppression.enabled) return;
        if (!(entity instanceof Player)) return;
        if (detached) return;

        // Get camera kick offsets (aim punch)
        float kickYaw = SuppressionHandler.getKickYaw(partialTick);
        float kickPitch = SuppressionHandler.getKickPitch(partialTick);

        if (Math.abs(kickYaw) > 0.001f || Math.abs(kickPitch) > 0.001f) {
            // Apply kick to camera rotation
            yRot += kickYaw;
            xRot += kickPitch;
        }
    }
}
