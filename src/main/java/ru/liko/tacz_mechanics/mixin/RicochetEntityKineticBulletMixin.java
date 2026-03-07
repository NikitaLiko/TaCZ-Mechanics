package ru.liko.tacz_mechanics.mixin;

import com.mojang.logging.LogUtils;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.particles.BulletHoleOption;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.data.manager.BulletParticlesManager;
import ru.liko.tacz_mechanics.data.manager.BulletSoundsManager;
import ru.liko.tacz_mechanics.mixininterface.EntityKineticBulletImpactState;
import ru.liko.tacz_mechanics.mixininterface.EntityKineticBulletStartPosAccessor;

@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class RicochetEntityKineticBulletMixin implements EntityKineticBulletImpactState {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private int taczMechanics$ricochetCount = 0;
    
    @Unique
    private Vec3 taczMechanics$pendingVelocity = null;
    
    @Unique
    private boolean taczMechanics$skipRicochet = false;

    @Shadow
    private boolean explosion;

    @Shadow
    private net.minecraft.resources.ResourceLocation ammoId;

    @Shadow
    private net.minecraft.resources.ResourceLocation gunId;

    @Shadow
    private net.minecraft.resources.ResourceLocation gunDisplayId;

    @Inject(method = "tick", at = @At("HEAD"))
    private void taczMechanics$tick$applyPendingVelocity(CallbackInfo ci) {
        if (taczMechanics$pendingVelocity != null) {
            EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
            bullet.setDeltaMovement(taczMechanics$pendingVelocity);
            taczMechanics$pendingVelocity = null;
        }
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void taczMechanics$onHitBlockRicochet(BlockHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        if (taczMechanics$consumeSkipRicochet()) {
            return;
        }
        if (!Config.Ricochet.enabled) {
            taczMechanics$debug("skip: disabled");
            return;
        }

        if (this.explosion || result.getType() == HitResult.Type.MISS) {
            taczMechanics$debug("skip: explosion=%s hitType=%s", this.explosion, result.getType());
            return;
        }

        int maxBounces = Config.Ricochet.demoPreset ? 999 : Config.Ricochet.maxBounces;
        if (maxBounces <= 0 || taczMechanics$ricochetCount >= maxBounces) {
            taczMechanics$debug("skip: bounceLimit=%s count=%s", maxBounces, taczMechanics$ricochetCount);
            return;
        }

        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        Vec3 velocity = bullet.getDeltaMovement();
        double speed = velocity.length();
        double minSpeed = Config.Ricochet.demoPreset ? 0.0 : Config.Ricochet.minSpeed;
        if (speed < minSpeed) {
            taczMechanics$debug("skip: speed=%.3f min=%.3f", speed, minSpeed);
            return;
        }

        Direction face = result.getDirection();
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal()).normalize();
        Vec3 incoming = velocity.normalize().scale(-1.0);
        double dot = incoming.dot(normal);
        if (dot <= 0.0) {
            taczMechanics$debug("skip: dot=%.3f (invalid hit normal)", dot);
            return;
        }

        double incidenceDeg = Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0)));
        double minAngle = Config.Ricochet.demoPreset ? 0.0 : Config.Ricochet.minAngle;
        if (incidenceDeg < minAngle) {
            taczMechanics$debug("skip: angle=%.2f min=%.2f", incidenceDeg, minAngle);
            return;
        }

        double chance = Config.Ricochet.demoPreset ? 1.0 : Config.Ricochet.chance;
        double chanceRoll = bullet.getRandom().nextDouble();
        if (chanceRoll > chance) {
            taczMechanics$debug("skip: chanceRoll=%.3f chance=%.3f", chanceRoll, chance);
            return;
        }

        Vec3 reflected = velocity.subtract(normal.scale(2.0 * velocity.dot(normal)));
        double speedMultiplier = Config.Ricochet.demoPreset ? 0.9 : Config.Ricochet.speedMultiplier;
        Vec3 newVelocity = reflected.scale(speedMultiplier);
        if (newVelocity.lengthSqr() < 1.0E-4) {
            taczMechanics$debug("skip: newSpeed=%.6f", newVelocity.length());
            return;
        }

        taczMechanics$ricochetCount++;
        taczMechanics$pendingVelocity = newVelocity;
        bullet.setDeltaMovement(Vec3.ZERO);
        taczMechanics$debug("ricochet: count=%s speed=%.3f->%.3f angle=%.2f chanceRoll=%.3f",
            taczMechanics$ricochetCount, speed, newVelocity.length(), incidenceDeg, chanceRoll);

        Vec3 hitVec = result.getLocation();
        Vec3 offset = normal.scale(0.05);
        Vec3 newStartPos = new Vec3(hitVec.x + offset.x, hitVec.y + offset.y, hitVec.z + offset.z);
        bullet.setPos(newStartPos.x, newStartPos.y, newStartPos.z);

        // Обновляем startPos трассера в мировых координатах, чтобы линия рисовалась от точки рикошета, а не от игрока
        if (bullet instanceof EntityKineticBulletStartPosAccessor startPosAccessor) {
            startPosAccessor.taczMechanics$setStartPos(newStartPos);
        }

        if (bullet.level() instanceof ServerLevel serverLevel) {
            BulletHoleOption bulletHoleOption = new BulletHoleOption(
                result.getDirection(),
                result.getBlockPos(),
                this.ammoId.toString(),
                this.gunId.toString(),
                this.gunDisplayId.toString()
            );
            serverLevel.sendParticles(bulletHoleOption, hitVec.x, hitVec.y, hitVec.z, 1, 0, 0, 0, 0);
            var state = serverLevel.getBlockState(result.getBlockPos());
            float damage = bullet.getDamage(hitVec);
            BulletSoundsManager.INSTANCE.handleBlockSound(BulletSoundsManager.BlockSoundType.RICOCHET, serverLevel, this.gunId, this.ammoId, damage, result, state);
            // Спавним эффект искр при рикошете
            BulletParticlesManager.INSTANCE.handleBlockParticle(BulletParticlesManager.BlockParticleType.RICOCHET, serverLevel, this.gunId, this.ammoId, damage, result, state);
        }

        ci.cancel();
    }

    @Unique
    private void taczMechanics$debug(String format, Object... args) {
        if (!Config.debug && !Config.Ricochet.debug) {
            return;
        }
        LOGGER.debug("[Ricochet] " + format, args);
    }
    
    @Override
    public void taczMechanics$setSkipRicochet(boolean skip) {
        taczMechanics$skipRicochet = skip;
    }
    
    @Override
    public boolean taczMechanics$consumeSkipRicochet() {
        if (!taczMechanics$skipRicochet) {
            return false;
        }
        taczMechanics$skipRicochet = false;
        return true;
    }
}
