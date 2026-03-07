package ru.liko.tacz_mechanics.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.particles.BulletHoleOption;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.data.BulletInteractions;
import ru.liko.tacz_mechanics.data.manager.BulletInteractionsManager;
import ru.liko.tacz_mechanics.data.manager.BulletParticlesManager;
import ru.liko.tacz_mechanics.data.manager.BulletSoundsManager;
import ru.liko.tacz_mechanics.mixininterface.EntityKineticBulletImpactState;
import ru.liko.tacz_mechanics.mixininterface.EntityKineticBulletStartPosAccessor;

@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class PierceEntityKineticBulletMixin {
    @Shadow
    private net.minecraft.resources.ResourceLocation ammoId;
    
    @Shadow
    private net.minecraft.resources.ResourceLocation gunId;
    
    @Shadow
    private net.minecraft.resources.ResourceLocation gunDisplayId;
    
    @Shadow
    private float damageModifier;
    
    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void taczMechanics$onHitBlockPierce(BlockHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        if (result.getType() == HitResult.Type.MISS) {
            return;
        }
        
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (!(bullet.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        
        BlockState state = serverLevel.getBlockState(result.getBlockPos());
        Vec3 hitVec = result.getLocation();
        float damage = bullet.getDamage(hitVec);
        
        double distance = taczMechanics$getDistanceFromStart(bullet, hitVec);
        BulletInteractions.PierceSettings pierce = BulletInteractionsManager.INSTANCE.findBlockPierce(
            serverLevel, this.gunId, this.ammoId, damage, result, state, distance, bullet.getRandom()
        );
        if (pierce == null) {
            return;
        }
        
        // Apply damage/speed modifiers
        this.damageModifier = Math.max(0.0f, this.damageModifier * pierce.damageMultiplier());
        Vec3 velocity = bullet.getDeltaMovement();
        Vec3 newVelocity = velocity.scale(pierce.speedMultiplier());
        if (newVelocity.lengthSqr() < 1.0E-6) {
            return;
        }
        bullet.setDeltaMovement(newVelocity);
        
        // Spawn entry effects
        if (pierce.spawnHole()) {
            taczMechanics$spawnBulletHole(serverLevel, result, hitVec, result.getDirection());
        }
        if (pierce.spawnParticles()) {
            BulletParticlesManager.INSTANCE.handleBlockParticle(
                BulletParticlesManager.BlockParticleType.PIERCE,
                serverLevel,
                this.gunId,
                this.ammoId,
                damage,
                result,
                state
            );
        }
        if (pierce.spawnSounds()) {
            BulletSoundsManager.INSTANCE.handleBlockSound(
                BulletSoundsManager.BlockSoundType.PIERCE,
                serverLevel,
                this.gunId,
                this.ammoId,
                damage,
                result,
                state
            );
        }
        
        // Move bullet to exit position
        Direction exitDir = result.getDirection().getOpposite();
        Vec3 exitPos = Vec3.atCenterOf(result.getBlockPos())
            .add(Vec3.atLowerCornerOf(exitDir.getNormal()).scale(0.501));
        bullet.setPos(exitPos.x, exitPos.y, exitPos.z);
        
        if (pierce.spawnExitHole()) {
            taczMechanics$spawnBulletHole(serverLevel, result, exitPos, exitDir);
        }
        
        if (bullet instanceof EntityKineticBulletImpactState impactState) {
            impactState.taczMechanics$setSkipRicochet(true);
        }
        
        ci.cancel();
    }
    
    @Unique
    private double taczMechanics$getDistanceFromStart(EntityKineticBullet bullet, Vec3 hitVec) {
        if (bullet instanceof EntityKineticBulletStartPosAccessor accessor) {
            Vec3 start = accessor.taczMechanics$getStartPos();
            if (start != null) {
                return start.distanceTo(hitVec);
            }
        }
        return 0.0;
    }
    
    @Unique
    private void taczMechanics$spawnBulletHole(ServerLevel level, BlockHitResult result, Vec3 hitVec, Direction direction) {
        BulletHoleOption bulletHoleOption = new BulletHoleOption(
            direction,
            result.getBlockPos(),
            this.ammoId.toString(),
            this.gunId.toString(),
            this.gunDisplayId.toString()
        );
        level.sendParticles(bulletHoleOption, hitVec.x, hitVec.y, hitVec.z, 1, 0, 0, 0, 0);
    }
}
