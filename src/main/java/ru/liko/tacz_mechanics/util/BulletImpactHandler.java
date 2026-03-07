package ru.liko.tacz_mechanics.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.liko.tacz_mechanics.data.manager.BulletParticlesManager;
import ru.liko.tacz_mechanics.data.manager.BulletSoundsManager;

/**
 * Utility class for handling bullet impact effects (particles and sounds).
 * Call these methods when a bullet hits a block or entity.
 */
public class BulletImpactHandler {
    
    /**
     * Handle bullet hitting a block - plays sounds and spawns particles.
     * 
     * @param level The server level
     * @param weaponId The resource location of the weapon that fired the bullet
     * @param damage The damage of the bullet
     * @param result The block hit result
     * @param state The block state that was hit
     * @param pierce Whether the bullet pierced through
     * @param destroyed Whether the block was destroyed
     */
    public static void handleBlockHit(ServerLevel level, ResourceLocation weaponId, ResourceLocation ammoId, float damage, 
                                       BlockHitResult result, BlockState state, 
                                       boolean pierce, boolean destroyed) {
        BulletSoundsManager.BlockSoundType soundType;
        BulletParticlesManager.BlockParticleType particleType;
        
        if (destroyed) {
            soundType = BulletSoundsManager.BlockSoundType.BREAK;
            particleType = BulletParticlesManager.BlockParticleType.BREAK;
        } else if (pierce) {
            soundType = BulletSoundsManager.BlockSoundType.PIERCE;
            particleType = BulletParticlesManager.BlockParticleType.PIERCE;
        } else {
            soundType = BulletSoundsManager.BlockSoundType.HIT;
            particleType = BulletParticlesManager.BlockParticleType.HIT;
        }
        
        BulletSoundsManager.INSTANCE.handleBlockSound(soundType, level, weaponId, ammoId, damage, result, state);
        BulletParticlesManager.INSTANCE.handleBlockParticle(particleType, level, weaponId, ammoId, damage, result, state);
    }
    
    /**
     * Handle bullet hitting an entity - plays sounds and spawns particles.
     * 
     * @param level The server level
     * @param weaponId The resource location of the weapon that fired the bullet
     * @param damage The damage of the bullet
     * @param location The hit location
     * @param target The entity that was hit
     * @param pierce Whether the bullet pierced through
     * @param killed Whether the entity was killed
     */
    public static void handleEntityHit(ServerLevel level, ResourceLocation weaponId, ResourceLocation ammoId, float damage,
                                        Vec3 location, Entity target,
                                        boolean pierce, boolean killed) {
        BulletSoundsManager.EntitySoundType soundType;
        BulletParticlesManager.EntityParticleType particleType;
        
        if (killed) {
            soundType = BulletSoundsManager.EntitySoundType.KILL;
            particleType = BulletParticlesManager.EntityParticleType.KILL;
        } else if (pierce) {
            soundType = BulletSoundsManager.EntitySoundType.PIERCE;
            particleType = BulletParticlesManager.EntityParticleType.PIERCE;
        } else {
            soundType = BulletSoundsManager.EntitySoundType.HIT;
            particleType = BulletParticlesManager.EntityParticleType.HIT;
        }
        
        BulletSoundsManager.INSTANCE.handleEntitySound(soundType, level, weaponId, ammoId, damage, location, target);
        BulletParticlesManager.INSTANCE.handleEntityParticle(particleType, level, weaponId, ammoId, damage, location, target);
    }
    
    /**
     * Simple block hit handler without pierce/destroy info (defaults to HIT type).
     */
    public static void handleBlockHit(ServerLevel level, ResourceLocation weaponId, ResourceLocation ammoId, float damage,
                                       BlockHitResult result, BlockState state) {
        handleBlockHit(level, weaponId, ammoId, damage, result, state, false, false);
    }
    
    /**
     * Simple entity hit handler without pierce/kill info (defaults to HIT type).
     */
    public static void handleEntityHit(ServerLevel level, ResourceLocation weaponId, ResourceLocation ammoId, float damage,
                                        Vec3 location, Entity target) {
        handleEntityHit(level, weaponId, ammoId, damage, location, target, false, false);
    }
}
