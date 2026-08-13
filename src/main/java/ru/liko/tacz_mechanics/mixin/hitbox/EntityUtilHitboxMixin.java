package ru.liko.tacz_mechanics.mixin.hitbox;

import com.mojang.logging.LogUtils;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.HitboxHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.pjmragdoll.api.BoneHit;
import ru.liko.pjmragdoll.api.BoneHittable;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.hitbox.BoneEntityResult;
import ru.liko.tacz_mechanics.hitbox.PlayerSkeleton;
import ru.liko.tacz_mechanics.hitbox.RagdollBoneMapping;
import ru.liko.tacz_mechanics.hitbox.SkeletonPose;

/**
 * Replaces TaCZ's single-AABB hit test with the OBB skeleton for players.
 *
 * <p>This is the one choke point: both {@code findEntityOnPath} and {@code findEntitiesOnPath}
 * route through it, so piercing rounds get per-bone resolution too. Everything else — mobs,
 * vehicles, target minecarts — falls through to the original AABB clip.
 */
@Mixin(value = EntityUtil.class, remap = false)
public abstract class EntityUtilHitboxMixin {

    private static final Logger TACZMECHANICS$LOGGER = LogUtils.getLogger();

    @Inject(method = "getHitResult", at = @At("HEAD"), cancellable = true)
    private static void taczMechanics$obbHitResult(Projectile bulletEntity, Entity entity, Vec3 startVec, Vec3 endVec,
                                                   CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
        if (!Config.Hitbox.enabled) {
            return;
        }
        // A physics body already knows where its bones are in world space, so
        // HitboxHelper is skipped entirely: its ping rewind exists for live
        // players and is meaningless for a server-simulated corpse.
        if (entity instanceof BoneHittable body) {
            BoneHit hit = body.raycastBones(startVec, endVec);
            if (hit == null) {
                cir.setReturnValue(null);
                return;
            }
            body.setPendingBone(hit.part());
            cir.setReturnValue(new BoneEntityResult(entity, hit.pos(),
                    RagdollBoneMapping.toPart(hit.part())));
            return;
        }

        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        // ponytail: anchoring on TaCZ's compensated box inherits its ping rewind and velocity offset
        // for free. The box is also expanded towards the velocity, which skews the centre by roughly
        // half a tick of movement (~0.05 blocks walking); re-derive the translation here if that shows.
        AABB box = HitboxHelper.getFixedBoundingBox(entity, bulletEntity.getOwner());
        Vec3 anchor = new Vec3((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);

        PlayerSkeleton.Hit hit = PlayerSkeleton.raycast(SkeletonPose.of(player), anchor, startVec, endVec);
        if (Config.Hitbox.debugRender) {
            TACZMECHANICS$LOGGER.info("[Hitbox] OBB test on {}: seg {}->{} anchor {} -> {}",
                    player.getGameProfile().getName(), startVec, endVec, anchor,
                    hit == null ? "MISS (skeleton)" : "HIT " + hit.part() + " @ " + hit.pos());
        }
        cir.setReturnValue(hit == null ? null : new BoneEntityResult(entity, hit.pos(), hit.part()));
    }
}
