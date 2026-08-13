package ru.liko.tacz_mechanics.mixin.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;

/**
 * Limits how far the local player can turn while prone, clamping the look yaw to a cone around the
 * direction they went prone in.
 */
@Mixin(Entity.class)
public abstract class ProneLookClampMixin {

    @Inject(method = "turn", at = @At("TAIL"))
    private void taczMechanics$proneClamp(double yaw, double pitch, CallbackInfo ci) {
        if (!Config.Movement.enabled || !Config.Movement.proneViewClamp) return;
        Entity self = (Entity) (Object) this;
        if (self != Minecraft.getInstance().player) return;
        if (!MovementClientHandler.isLocalProne()) return;

        float rel = Mth.wrapDegrees(self.getYRot() - MovementClientHandler.getProneAnchorYaw());
        float max = (float) Config.Movement.proneViewAngle;
        float clamped = Mth.clamp(rel, -max, max);
        if (clamped != rel) {
            self.setYRot(MovementClientHandler.getProneAnchorYaw() + clamped);
        }
    }
}
