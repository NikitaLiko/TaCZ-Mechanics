package ru.liko.tacz_mechanics.client.particle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.particle.ModParticles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client half of the bullet impact effects: owns the sprite sets, loads the effect definitions,
 * and expands a single server-sent particle into the layered burst it describes.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ImpactFxClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, SpriteSet> SPRITES = new HashMap<>();

    /** Material effects, sorted by descending priority; the first block match wins. */
    private static volatile List<ImpactFx> materials = List.of();

    /** Effects tied to a moment instead of a surface, looked up by their {@code event} name. */
    private static volatile Map<String, ImpactFx> events = Map.of();

    @SubscribeEvent
    static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        // All three providers spawn their layers as a side effect and add no particle of their own.
        // Returning null from a provider is supported (vanilla's TerrainParticle.Provider does it).

        // Impact: the sender packs the face normal scaled by burst intensity into the velocity.
        event.registerSpecial(ModParticles.BULLET_IMPACT.get(), (options, level, x, y, z, nx, ny, nz) -> {
            Vec3 outward = new Vec3(nx, ny, nz);
            spawn(level, resolveMaterial(level, x, y, z, outward, options.getState()), x, y, z, outward);
            return null;
        });

        // Ricochet streak: direction is where the bullet bounced off to, not the face normal.
        event.registerSpecial(ModParticles.RICOCHET_STREAK.get(), (options, level, x, y, z, dx, dy, dz) -> {
            spawn(level, events.get("ricochet"), x, y, z, new Vec3(dx, dy, dz));
            return null;
        });

        // Underwater trail, spawned client-side by the bullet itself rather than sent.
        event.registerSpecial(ModParticles.BUBBLE_TRAIL.get(), (options, level, x, y, z, dx, dy, dz) -> {
            spawn(level, events.get("bubble_trail"), x, y, z, new Vec3(dx, dy, dz));
            return null;
        });

        ModParticles.SPRITE_FAMILIES.forEach((name, holder) ->
            event.registerSpriteSet(holder.get(), set -> {
                // The set is a MutableSpriteSet that the engine rebinds on every resource reload,
                // so holding this reference stays correct across resource pack changes.
                SPRITES.put(name, set);
                return (options, level, x, y, z, xd, yd, zd) -> null;
            }));
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimpleJsonResourceReloadListener(new Gson(), "impact_fx") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                List<ImpactFx> loaded = new ArrayList<>();
                files.forEach((id, json) -> ImpactFx.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> LOGGER.error("[TaczMechanics] bad impact_fx {}: {}", id, error))
                    .ifPresent(loaded::add));

                Map<String, ImpactFx> byEvent = new HashMap<>();
                List<ImpactFx> byBlock = new ArrayList<>();
                for (ImpactFx fx : loaded) {
                    if (fx.isEvent()) {
                        byEvent.put(fx.event(), fx);
                    } else {
                        byBlock.add(fx);
                    }
                }
                byBlock.sort(Comparator.comparingInt(ImpactFx::priority).reversed());
                materials = List.copyOf(byBlock);
                events = Map.copyOf(byEvent);
                LOGGER.info("[TaczMechanics] impact_fx: {} materials, {} event effects",
                    materials.size(), events.size());
            }
        });
    }

    @Nullable
    private static ImpactFx resolveMaterial(ClientLevel level, double x, double y, double z,
                                            Vec3 outward, BlockState state) {
        // The hit point sits exactly on the surface, so step back along the normal to land inside
        // the block that was struck rather than in the air in front of it.
        Vec3 back = outward.lengthSqr() < 1.0e-8 ? Vec3.ZERO : outward.normalize().scale(0.1);
        BlockPos pos = BlockPos.containing(x - back.x, y - back.y, z - back.z);
        for (ImpactFx candidate : materials) {
            if (candidate.matches(level, pos, state)) return candidate;
        }
        return null;
    }

    /**
     * @param scaledDirection outward direction of the burst, its length carrying the intensity that
     *                        scales every layer's particle count
     */
    private static void spawn(ClientLevel level, @Nullable ImpactFx fx,
                              double x, double y, double z, Vec3 scaledDirection) {
        if (fx == null) return;
        double intensity = scaledDirection.length();
        if (intensity < 1.0e-6) return;
        Vec3 n = scaledDirection.scale(1.0 / intensity);

        RandomSource random = level.random;
        for (ImpactFx.Layer layer : fx.layers()) {
            SpriteSet sprites = SPRITES.get(layer.sprites());
            if (sprites == null) {
                LOGGER.warn("[TaczMechanics] impact_fx layer references unknown sprite family '{}'", layer.sprites());
                continue;
            }
            double ox = x + n.x * layer.offset();
            double oy = y + n.y * layer.offset();
            double oz = z + n.z * layer.offset();
            // A grazing ricochet or a small calibre throws less material than a solid heavy hit.
            int count = Math.max(1, (int) Math.round(layer.count() * intensity));
            for (int i = 0; i < count; i++) {
                Vec3 velocity = layer.motion().sample(n, random);
                Minecraft.getInstance().particleEngine.add(
                    new ImpactFxParticle(level, ox, oy, oz, velocity, layer, sprites));
            }
        }
    }

    private ImpactFxClient() {
    }
}
