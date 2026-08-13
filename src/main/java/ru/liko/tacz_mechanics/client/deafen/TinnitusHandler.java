package ru.liko.tacz_mechanics.client.deafen;

import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.client.freeaim.RecoilSource;
import ru.liko.pjmapi.api.client.SoundFilters;
import ru.liko.tacz_mechanics.compat.GunAttachments;

/**
 * Ringing ears from your own muzzle blast in an enclosed space.
 *
 * <p>Firing an unsuppressed gun inside a room raises a whine and muffles everything else for a few
 * seconds — the price of clearing a building without a suppressor or ear protection. Outdoors the
 * blast has nowhere to bounce, so nothing happens.
 *
 * <p>Reuses what the mod already has: the low-pass path from {@link SoundFilters} for the
 * deafness, and the suppression post-chain (see {@code SuppressionRenderer}) for the visual haze.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public final class TinnitusHandler {

    /** Distance a probe ray looks for a wall when judging how enclosed the shooter is. */
    private static final double PROBE_DISTANCE = 6.0;
    /** Four sideways + one up. The floor is not probed: standing on ground is not "indoors". */
    private static final Vec3[] PROBES = {
        new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
        new Vec3(0, 0, 1), new Vec3(0, 0, -1),
        new Vec3(0, 1, 0)
    };
    /** Below this the ringing is inaudible; stop bothering the sound engine. */
    private static final float SILENT = 0.01f;

    private static float level;
    private static float prevLevel;
    private static TinnitusSoundInstance ringing;

    private TinnitusHandler() {
    }

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (!Config.Tinnitus.enabled) {
            return;
        }
        if (event.getLogicalSide() != LogicalSide.CLIENT) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player != event.getShooter()) {
            return;
        }
        if (GunAttachments.isSilenced(event.getGunItemStack())) {
            return;
        }

        float enclosure = enclosure(player);
        if (enclosure < Config.Tinnitus.enclosureThreshold) {
            return;
        }
        // Loudness proxy: the gun's own recoil curve. A .50 deafens, a .22 does not.
        float loudness = Mth.clamp(RecoilSource.factors(player.getMainHandItem())[0], 0.5f, 2f);
        add((float) Config.Tinnitus.intensityPerShot * enclosure * loudness);
    }

    private static void add(float amount) {
        level = Math.min(1f, level + amount);
        if (level < SILENT) {
            return;
        }
        if (ringing == null || ringing.isStopped()) {
            ringing = new TinnitusSoundInstance();
            Minecraft.getInstance().getSoundManager().play(ringing);
        }
    }

    /** Fraction of the probe rays that hit a wall — 1 means fully boxed in. */
    private static float enclosure(LocalPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        int hits = 0;
        for (Vec3 dir : PROBES) {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(PROBE_DISTANCE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                hits++;
            }
        }
        return hits / (float) PROBES.length;
    }

    public static void tick() {
        prevLevel = level;
        if (level <= 0f) {
            return;
        }
        float decayPerTick = 1f / (float) (Config.Tinnitus.decaySeconds * 20.0);
        level = Math.max(0f, level - decayPerTick);
    }

    /**
     * Low-passes and quietens a sound that started while the ears are still ringing. Called from
     * the sound engine for every new sound, including those started by other players.
     */
    public static void muffleNewSound(SoundInstance instance) {
        if (level < SILENT || instance instanceof TinnitusSoundInstance) {
            return;
        }
        SoundFilters.attach(instance, SoundFilters.muffle(TinnitusHandler::currentMuffle));
    }

    /**
     * Текущая степень приглушения. Читается фильтром каждый тик, поэтому длинный звук,
     * начавшийся при звоне в ушах, проясняется по мере того как слух возвращается.
     * Раньше значение замораживалось на старте звука и висело до самого его конца.
     */
    private static double currentMuffle() {
        return level < SILENT ? 0d : level * Config.Tinnitus.muffleStrength;
    }

    /** Raw ringing level, for the looping whine's own volume. */
    public static float getRawLevel() {
        return level;
    }

    /** Interpolated level for the shader pass. */
    public static float getLevel(float partialTick) {
        return Mth.lerp(partialTick, prevLevel, level);
    }

    public static boolean isActive() {
        return level > SILENT || prevLevel > SILENT;
    }

    public static void reset() {
        level = 0f;
        prevLevel = 0f;
        if (ringing != null) {
            Minecraft.getInstance().getSoundManager().stop(ringing);
            ringing = null;
        }
    }
}
