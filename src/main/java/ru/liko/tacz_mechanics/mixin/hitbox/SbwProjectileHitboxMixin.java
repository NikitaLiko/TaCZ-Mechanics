package ru.liko.tacz_mechanics.mixin.hitbox;

import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.atsuishio.superbwarfare.tools.HitboxHelper;
import com.atsuishio.superbwarfare.world.phys.EntityResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.pjmragdoll.api.BoneHit;
import ru.liko.pjmragdoll.api.BoneHittable;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.hitbox.LimbDamageHandler;
import ru.liko.tacz_mechanics.hitbox.PlayerSkeleton;
import ru.liko.tacz_mechanics.hitbox.RagdollBoneMapping;
import ru.liko.tacz_mechanics.hitbox.SkeletonPose;

/**
 * The SuperbWarfare half of the OBB skeleton, mirroring {@link EntityUtilHitboxMixin}.
 *
 * <p>Every gun in that mod fires a {@code ProjectileEntity}, and its subclasses inherit this
 * {@code getHitResult}, so one injection covers the lot. Their vertical-band headshot test is
 * replaced by the actual head bone; the leg flag keeps feeding their own slowdown effect, and the
 * remaining bones are scaled in {@code performDamage} because their damage never passes through a
 * cancellable event that carries the amount.
 *
 * <p>ponytail: {@code FastThrowableProjectile} (rockets, shells) keeps the single AABB — it detonates
 * on contact, so which limb it clipped changes nothing. Mixin the same method on
 * {@code IAdvancedHitDetection$DefaultImpls} if that ever stops being true.
 */
@Mixin(value = ProjectileEntity.class, remap = false)
public abstract class SbwProjectileHitboxMixin {

    @Inject(method = "getHitResult", at = @At("HEAD"), cancellable = true)
    private void taczMechanics$obbHitResult(Entity entity, Vec3 startVec, Vec3 endVec,
                                            CallbackInfoReturnable<EntityResult> cir) {
        if (!Config.Hitbox.enabled) {
            return;
        }
        // Physics bodies carry their own world-space bones — no anchor to
        // reconstruct and no ping rewind to apply.
        if (entity instanceof BoneHittable body) {
            BoneHit hit = body.raycastBones(startVec, endVec);
            if (hit == null) {
                cir.setReturnValue(null);
                return;
            }
            body.setPendingBone(hit.part());
            PlayerSkeleton.Part part = RagdollBoneMapping.toPart(hit.part());
            cir.setReturnValue(new EntityResult(entity, hit.pos(),
                    part == PlayerSkeleton.Part.HEAD, part == PlayerSkeleton.Part.LEG));
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        ProjectileEntity self = (ProjectileEntity) (Object) this;

        SkeletonPose pose = SkeletonPose.of(player);
        Vec3 anchor;
        if (pose.seatRotation != null) {
            // Riding: their EntityMixin deflates the bounding box about its centre while the renderer
            // scales the model about its origin, so the box bottom is not the feet. positionRider put
            // the player exactly on the model origin, which is.
            anchor = player.position();
        } else {
            AABB box = self.getOwner() instanceof ServerPlayer shooter
                    ? HitboxHelper.getBoundingBox(player, Mth.floor(shooter.connection.latency() / 1000.0 * 20.0 + 0.5))
                    : player.getBoundingBox();
            anchor = new Vec3((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
        }

        PlayerSkeleton.Hit hit = PlayerSkeleton.raycast(pose, anchor, startVec, endVec);
        if (hit == null) {
            cir.setReturnValue(null);
            return;
        }
        LimbDamageHandler.setSbwPending(entity, hit.part());
        cir.setReturnValue(new EntityResult(entity, hit.pos(),
                hit.part() == PlayerSkeleton.Part.HEAD, hit.part() == PlayerSkeleton.Part.LEG));
    }

    @ModifyVariable(method = "performDamage", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float taczMechanics$scaleByBone(float damage, Entity entity, float unusedDamage, boolean headshot) {
        return LimbDamageHandler.sbwScale(entity, damage);
    }
}
