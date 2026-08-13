package ru.liko.tacz_mechanics.client.light;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.compat.GunAttachments;

/**
 * Emits Minecraft block light for muzzle flashes and tracer bullet trails.
 * Purely client-side visual; positions live in {@link GunLightMap}.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public final class GunLightHandler {

    private static final double MUZZLE_OFFSET = 0.8;
    private static final double MIN_TRAIL_STEP = 0.25;
    private static final int MAX_SPARKS_PER_TICK = 64;

    /** bullet entityId -> its position on the previous client tick */
    private static final Int2ObjectMap<Vec3> TRACKED = new Int2ObjectOpenHashMap<>();

    private GunLightHandler() {
    }

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.CLIENT) return;
        if (!Config.GunLights.enabled || !Config.GunLights.muzzleFlashEnabled) return;
        LivingEntity shooter = event.getShooter();
        if (shooter == null) return;

        int level = GunAttachments.isSilenced(event.getGunItemStack())
                ? Config.GunLights.silencedLightLevel
                : Config.GunLights.muzzleLightLevel;
        if (level <= 0) return;

        Vec3 muzzle = shooter.getEyePosition().add(shooter.getLookAngle().scale(MUZZLE_OFFSET));
        spark(BlockPos.asLong(
                (int) Math.floor(muzzle.x), (int) Math.floor(muzzle.y), (int) Math.floor(muzzle.z)),
                level, Config.GunLights.muzzleDurationTicks, Config.GunLights.muzzleFadePerTick);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        if (!Config.GunLights.enabled || !Config.GunLights.tracerEnabled) return;
        if (event.getEntity() instanceof EntityKineticBullet bullet) {
            // tracerAmmoOnly is re-checked each tick: spawn data (isTracerAmmo)
            // may not have arrived yet when the entity joins the level
            TRACKED.put(bullet.getId(), bullet.position());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        GunLightMap.tick();
        if (TRACKED.isEmpty()) return;
        if (!Config.GunLights.enabled || !Config.GunLights.tracerEnabled) {
            TRACKED.clear();
            return;
        }
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            TRACKED.clear();
            return;
        }
        var it = TRACKED.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Int2ObjectMap.Entry<Vec3> e = it.next();
            Entity ent = world.getEntity(e.getIntKey());
            if (!(ent instanceof EntityKineticBullet bullet) || !bullet.isAlive()) {
                it.remove();
                continue;
            }
            if (Config.GunLights.tracerAmmoOnly && !bullet.isTracerAmmo()) {
                it.remove();
                continue;
            }
            Vec3 cur = bullet.position();
            emitTrail(e.getValue(), cur);
            e.setValue(cur);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            reset();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        TRACKED.clear();
        GunLightMap.clear();
    }

    private static void emitTrail(Vec3 from, Vec3 to) {
        double dist = from.distanceTo(to);
        if (dist < MIN_TRAIL_STEP) return;
        int steps = (int) Math.clamp(
                (long) Math.ceil(dist / Config.GunLights.tracerStepBlocks), 1, MAX_SPARKS_PER_TICK);
        int level = Config.GunLights.tracerLightLevel;
        int ttl = Config.GunLights.tracerTtlTicks;
        int fade = Config.GunLights.tracerFadePerTick;
        long lastPacked = Long.MIN_VALUE;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            long packed = BlockPos.asLong(
                    (int) Math.floor(from.x + (to.x - from.x) * t),
                    (int) Math.floor(from.y + (to.y - from.y) * t),
                    (int) Math.floor(from.z + (to.z - from.z) * t));
            if (packed != lastPacked) {
                spark(packed, level, ttl, fade);
                lastPacked = packed;
            }
        }
    }

    private static void spark(long packedPos, int level, int ttlTicks, int fadePerTick) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world != null && GunLightMap.putSpark(packedPos, level, ttlTicks, fadePerTick)) {
            world.getLightEngine().checkBlock(BlockPos.of(packedPos));
        }
    }
}
