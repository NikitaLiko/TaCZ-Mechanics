package ru.liko.tacz_mechanics.mixin.hitbox;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.hitbox.BoneEntityResult;
import ru.liko.tacz_mechanics.hitbox.LimbDamageHandler;

/**
 * {@code TacHitResult} drops everything but entity/position/headshot, so the bone would be lost on
 * the way into {@code onHitEntity}. TaCZ constructs it immediately before that call, which makes the
 * constructor the right place to hand the bone to the damage handler.
 */
@Mixin(value = TacHitResult.class, remap = false)
public abstract class TacHitResultBoneMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void taczMechanics$carryBone(EntityKineticBullet.EntityResult result, CallbackInfo ci) {
        LimbDamageHandler.setPending(result instanceof BoneEntityResult bone ? bone.getPart() : null);
    }
}
