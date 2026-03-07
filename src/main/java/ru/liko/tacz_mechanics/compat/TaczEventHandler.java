package ru.liko.tacz_mechanics.compat;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.data.manager.BulletParticlesManager;
import ru.liko.tacz_mechanics.data.manager.BulletSoundsManager;
import ru.liko.tacz_mechanics.network.SuppressionPacket;

/**
 * Event handler for TACZ gun events integration.
 * Handles bullet impact particles and sounds.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID)
public class TaczEventHandler {

    /**
     * Handle bullet hitting a block - triggers particles and sounds.
     */
    @SubscribeEvent
    public static void onAmmoHitBlock(AmmoHitBlockEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        
        EntityKineticBullet bullet = event.getAmmo();
        float damage = bullet.getDamage(event.getHitResult().getLocation());
        
        // Play block hit sounds
        BulletSoundsManager.INSTANCE.handleBlockSound(
            BulletSoundsManager.BlockSoundType.HIT,
            serverLevel,
            bullet.getGunId(),
            bullet.getAmmoId(),
            damage,
            event.getHitResult(),
            event.getState()
        );
        
        // Spawn block hit particles
        BulletParticlesManager.INSTANCE.handleBlockParticle(
            BulletParticlesManager.BlockParticleType.HIT,
            serverLevel,
            bullet.getGunId(),
            bullet.getAmmoId(),
            damage,
            event.getHitResult(),
            event.getState()
        );

        sendImpactSuppression(serverLevel, event.getHitResult().getLocation(), bullet.getOwner());
    }

    /**
     * Handle bullet hitting an entity (after damage applied, entity survived).
     */
    @SubscribeEvent
    public static void onEntityHurtByGun(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide().isClient()) return;
        if (event.getHurtEntity() == null) return;
        if (!(event.getHurtEntity().level() instanceof ServerLevel serverLevel)) return;
        
        Vec3 hitLocation = event.getHurtEntity().position().add(0, event.getHurtEntity().getBbHeight() / 2, 0);
        float damage = event.getBaseAmount();
        
        // Play entity hit sounds
        BulletSoundsManager.INSTANCE.handleEntitySound(
            BulletSoundsManager.EntitySoundType.HIT,
            serverLevel,
            event.getGunId(),
            null,
            damage,
            hitLocation,
            event.getHurtEntity()
        );
        
        // Spawn entity hit particles
        BulletParticlesManager.INSTANCE.handleEntityParticle(
            BulletParticlesManager.EntityParticleType.HIT,
            serverLevel,
            event.getGunId(),
            null,
            damage,
            hitLocation,
            event.getHurtEntity()
        );

        Entity attacker = event.getAttacker();
        sendImpactSuppression(serverLevel, hitLocation, attacker, event.getHurtEntity());
    }

    /**
     * Handle bullet killing an entity.
     */
    @SubscribeEvent
    public static void onEntityKillByGun(EntityKillByGunEvent event) {
        if (event.getLogicalSide().isClient()) return;
        if (event.getKilledEntity() == null) return;
        if (!(event.getKilledEntity().level() instanceof ServerLevel serverLevel)) return;
        
        Vec3 hitLocation = event.getKilledEntity().position().add(0, event.getKilledEntity().getBbHeight() / 2, 0);
        float damage = event.getBaseDamage();
        
        // Play entity kill sounds
        BulletSoundsManager.INSTANCE.handleEntitySound(
            BulletSoundsManager.EntitySoundType.KILL,
            serverLevel,
            event.getGunId(),
            null,
            damage,
            hitLocation,
            event.getKilledEntity()
        );
        
        // Spawn entity kill particles
        BulletParticlesManager.INSTANCE.handleEntityParticle(
            BulletParticlesManager.EntityParticleType.KILL,
            serverLevel,
            event.getGunId(),
            null,
            damage,
            hitLocation,
            event.getKilledEntity()
        );
    }

    private static void sendImpactSuppression(ServerLevel level, Vec3 impactPos, Entity shooter, Entity... excludeExtra) {
        if (!Config.Suppression.enabled) return;

        double maxRadius = Config.Suppression.detectionRadius;
        double multiplier = Config.Suppression.impactIntensityMultiplier;

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player == shooter) continue;
            if (player.level().dimension() != level.dimension()) continue;

            boolean excluded = false;
            for (Entity ex : excludeExtra) {
                if (player == ex) { excluded = true; break; }
            }
            if (excluded) continue;

            double distance = player.getEyePosition().distanceTo(impactPos);
            if (distance > maxRadius) continue;

            double normalizedDist = 1.0 - (distance / maxRadius);
            float intensity = (float) (normalizedDist * Config.Suppression.flybyIntensity * multiplier);
            if (intensity > 0.001f) {
                PacketDistributor.sendToPlayer(player, new SuppressionPacket(intensity));
            }
        }
    }
}
