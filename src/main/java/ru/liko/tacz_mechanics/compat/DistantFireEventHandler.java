package ru.liko.tacz_mechanics.compat;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.network.DistantFireSoundPacket;

/**
 * Sends DistantFireSoundPacket to players when a bullet is fired (for distant gunshot sounds).
 */
@EventBusSubscriber(modid = TaczMechanics.MODID)
public class DistantFireEventHandler {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!Config.DistantFire.enabled) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof EntityKineticBullet bullet)) return;

        Entity owner = bullet.getOwner();
        if (owner == null) return;

        Vec3 bulletPos = bullet.position();
        String caliberId = bullet.getAmmoId().toString();

        // Only send to players beyond minDistance (TACZ handles close range)
        for (ServerPlayer player : level.players()) {
            if (player == owner) continue;
            if (player.level().dimension() != level.dimension()) continue;

            double distance = player.position().distanceTo(bulletPos);
            if (distance < Config.DistantFire.minDistance) continue;
            if (distance > Config.DistantFire.maxDistance) continue;

            PacketDistributor.sendToPlayer(player, new DistantFireSoundPacket(
                bulletPos.x, bulletPos.y, bulletPos.z,
                caliberId,
                "",
                1.0f,
                1.0f
            ));
        }
    }
}
