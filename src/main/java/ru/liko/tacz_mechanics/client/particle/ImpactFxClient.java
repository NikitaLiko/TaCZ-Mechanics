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
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.particle.ModParticles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client half of the bullet impact effects: owns the sprite sets, loads the material definitions,
 * and expands a single server-sent {@code bullet_impact} particle into the layered burst.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ImpactFxClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, SpriteSet> SPRITES = new HashMap<>();

    /** Sorted by descending priority; the first match wins. */
    private static volatile List<ImpactFx> definitions = List.of();

    @SubscribeEvent
    static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        // The trigger spawns the layers as a side effect and adds no particle of its own. Returning
        // null from a provider is supported (vanilla's TerrainParticle.Provider does the same).
        event.registerSpecial(ModParticles.BULLET_IMPACT.get(), (options, level, x, y, z, nx, ny, nz) -> {
            // The sender packs the face normal scaled by the burst intensity into the velocity:
            // direction says which way the debris leaves, length says how much of it there is.
            spawn(level, x, y, z, new Vec3(nx, ny, nz), options.getState());
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
                loaded.sort(Comparator.comparingInt(ImpactFx::priority).reversed());
                definitions = List.copyOf(loaded);
                LOGGER.info("[TaczMechanics] impact_fx: loaded {} material definitions", loaded.size());
            }
        });
    }

    private static void spawn(ClientLevel level, double x, double y, double z, Vec3 scaledNormal, BlockState state) {
        double intensity = scaledNormal.length();
        if (intensity < 1.0e-6) return;
        Vec3 n = scaledNormal.scale(1.0 / intensity);
        // Step back along the normal so the lookup lands inside the struck block, not the air in front.
        BlockPos pos = BlockPos.containing(x - n.x * 0.1, y - n.y * 0.1, z - n.z * 0.1);

        ImpactFx fx = null;
        for (ImpactFx candidate : definitions) {
            if (candidate.matches(level, pos, state)) {
                fx = candidate;
                break;
            }
        }
        if (fx == null) return;

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
            // A grazing ricochet or a through-and-through throws less material than a solid hit.
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
