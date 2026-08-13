package ru.liko.tacz_mechanics.compat;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.data.distant_fire.DistantFireBands;
import ru.liko.tacz_mechanics.data.distant_fire.DistantFireRegistry;
import ru.liko.tacz_mechanics.data.distant_fire.DistantFireResolver;
import ru.liko.tacz_mechanics.data.distant_fire.DistantFireResolver.Resolution;
import ru.liko.tacz_mechanics.data.distant_fire.DistantFireSound;
import ru.liko.tacz_mechanics.network.DistantFireSoundPacket;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves distant-fire layers per-listener on the server and dispatches a
 * {@link DistantFireSoundPacket} with the already-mixed primary/secondary sounds.
 * The client is purely a player; this avoids the dedicated-server-without-datapack
 * scenario where clients have no caliber configs of their own.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID)
public class DistantFireEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private record ListenerShooter(UUID listener, UUID shooter) {
    }

    private static final Map<ListenerShooter, Integer> LAST_SENT_TICK = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || event.isCanceled()) {
            return;
        }
        if (!Config.DistantFire.enabled) {
            return;
        }
        LivingEntity shooter = event.getShooter();
        if (shooter.level().isClientSide() || !(shooter.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack gunStack = event.getGunItemStack();
        if (!(gunStack.getItem() instanceof IGun iGun)) {
            return;
        }
        var ammoId = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack))
            .map(gi -> gi.getGunData().getAmmoId())
            .orElse(null);
        if (ammoId == null) {
            return;
        }

        String caliberId = ammoId.toString();
        boolean suppressed = GunAttachments.isSilenced(gunStack);
        Optional<DistantFireSound> maybeSound = DistantFireRegistry.INSTANCE.resolve(caliberId);
        if (maybeSound.isEmpty()) {
            if (Config.debug) {
                LOGGER.info("[DistantFire] No config for caliber {} (and no default loaded); skipping.", caliberId);
            }
            return;
        }
        DistantFireSound sound = maybeSound.get();
        DistantFireBands bands = currentBands();

        Vec3 shotPos = shooter.getEyePosition(1.0f);
        float master = (float) Config.DistantFire.volumeMultiplier;
        int tick = level.getServer().getTickCount();
        int antiSpam = Config.DistantFire.antiSpamTicks;
        int nearR = bands.taczNearRange();
        float soundRange = Math.max(256f, bands.maxBound() + 100f);

        int sent = 0;
        int skippedOwner = 0;
        int tooClose = 0;
        int tooFar = 0;
        int wrongDim = 0;
        int spamSkip = 0;
        int silentSkip = 0;

        for (ServerPlayer player : level.players()) {
            if (player.level().dimension() != level.dimension()) {
                wrongDim++;
                continue;
            }
            if (!Config.DistantFire.includeShooter && player == shooter) {
                skippedOwner++;
                continue;
            }

            double distance = player.position().distanceTo(shotPos);
            if (distance < Config.DistantFire.minDistance) {
                tooClose++;
                continue;
            }
            if (distance > Config.DistantFire.maxDistance) {
                tooFar++;
                continue;
            }
            if (distance <= nearR) {
                tooClose++;
                continue;
            }

            Resolution resolution = DistantFireResolver.resolve(sound, bands, distance, suppressed);
            if (resolution.isSilent()) {
                silentSkip++;
                continue;
            }

            if (antiSpam > 0) {
                var key = new ListenerShooter(player.getUUID(), shooter.getUUID());
                if (isSpamSuppressed(key, tick, antiSpam)) {
                    spamSkip++;
                    continue;
                }
                LAST_SENT_TICK.put(key, tick);
            }

            DistantFireSoundPacket packet = buildPacket(sound, resolution, shotPos, master, soundRange, suppressed);
            if (packet == null) {
                silentSkip++;
                continue;
            }
            PacketDistributor.sendToPlayer(player, packet);
            sent++;
        }

        if (Config.debug) {
            LOGGER.info(
                "[DistantFire] GunFire shooter={} ammo={} pos=({}, {}, {}) sent={} (skipOwner={}, tooClose={}, tooFar={}, wrongDim={}, spamSkip={}, silent={})",
                shooter.getUUID(),
                caliberId,
                String.format("%.1f", shotPos.x),
                String.format("%.1f", shotPos.y),
                String.format("%.1f", shotPos.z),
                sent, skippedOwner, tooClose, tooFar, wrongDim, spamSkip, silentSkip
            );
        }
    }

    private static DistantFireSoundPacket buildPacket(DistantFireSound sound, Resolution resolution,
                                                      Vec3 pos, float master, float soundRange, boolean suppressed) {
        ResourceLocation primary;
        float primaryVol;
        if (resolution.hasPrimary()) {
            primary = sound.layer(resolution.primaryIndex(), suppressed).sound();
            primaryVol = resolution.primaryVolume() * master;
        } else if (resolution.hasSecondary()) {
            primary = sound.layer(resolution.secondaryIndex(), suppressed).sound();
            primaryVol = resolution.secondaryVolume() * master;
            return new DistantFireSoundPacket(pos.x, pos.y, pos.z, 1.0f, primary, primaryVol,
                Optional.empty(), 0f, soundRange);
        } else {
            return null;
        }

        Optional<ResourceLocation> secondary = Optional.empty();
        float secondaryVol = 0f;
        if (resolution.hasPrimary() && resolution.hasSecondary()) {
            secondary = Optional.of(sound.layer(resolution.secondaryIndex(), suppressed).sound());
            secondaryVol = resolution.secondaryVolume() * master;
        }
        return new DistantFireSoundPacket(pos.x, pos.y, pos.z, 1.0f,
            primary, primaryVol, secondary, secondaryVol, soundRange);
    }

    /**
     * Returns {@code true} when the listener+shooter pair was sent a packet within the last
     * {@code antiSpamTicks}. Treats a backwards-moving server tick (i.e. fresh
     * {@link net.minecraft.server.MinecraftServer} after singleplayer world reload) as
     * "no previous send" so distant fire is not silently suppressed for the entire ramp-up
     * to the previous session's tick count.
     */
    private static boolean isSpamSuppressed(ListenerShooter key, int tick, int antiSpamTicks) {
        Integer last = LAST_SENT_TICK.get(key);
        if (last == null) {
            return false;
        }
        if (tick < last) {
            LAST_SENT_TICK.remove(key, last);
            return false;
        }
        return tick - last < antiSpamTicks;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT_TICK.clear();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        java.util.UUID id = sp.getUUID();
        LAST_SENT_TICK.keySet().removeIf(k -> k.listener().equals(id) || k.shooter().equals(id));
    }

    /** Authoritative band partition built from {@link Config.DistantFire}. */
    public static DistantFireBands currentBands() {
        int[] bounds = {
            Config.DistantFire.closeMaxDistance,
            Config.DistantFire.midMaxDistance,
            Config.DistantFire.farMaxDistance,
        };
        return new DistantFireBands(
            Config.DistantFire.nearSoundRange,
            bounds,
            Config.DistantFire.transitionBlocks);
    }
}
