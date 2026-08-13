package ru.liko.tacz_mechanics.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.tacz_mechanics.TaczMechanics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Particle types for bullet impact effects.
 *
 * <p>{@link #BULLET_IMPACT} is the only type the server ever sends. It carries the struck
 * {@link net.minecraft.world.level.block.state.BlockState}; the struck face normal rides in the
 * particle velocity (see the {@code count = 0} note in {@code TaczEventHandler}). The client
 * expands it into the layered burst described by {@code assets/tacz_mechanics/impact_fx/*.json}.
 *
 * <p>The {@link #SPRITE_FAMILIES} types are never spawned. They exist so the client can borrow a
 * {@link net.minecraft.client.particle.SpriteSet} per texture group — that is the only sanctioned
 * way to get atlas-stitched sprites, and it maps cleanly onto the MTS {@code textureList} /
 * {@code randomTexture} model each family reproduces.
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> TYPES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, TaczMechanics.MODID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<BlockParticleOption>> BULLET_IMPACT =
        TYPES.register("bullet_impact", () -> new ParticleType<BlockParticleOption>(true) {
            @Override
            public MapCodec<BlockParticleOption> codec() {
                return BlockParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, BlockParticleOption> streamCodec() {
                return BlockParticleOption.streamCodec(this);
            }
        });

    /** Sparks thrown along the direction the bullet bounced off to, not along the face normal. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RICOCHET_STREAK =
        TYPES.register("ricochet_streak", () -> new SimpleParticleType(true));

    /** Spawned by the bullet itself while it travels underwater; never crosses the network. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BUBBLE_TRAIL =
        TYPES.register("bubble_trail", () -> new SimpleParticleType(true));

    /** Short name used in {@code impact_fx} JSON -> the type whose sprite set backs it. */
    public static final Map<String, DeferredHolder<ParticleType<?>, SimpleParticleType>> SPRITE_FAMILIES =
        new LinkedHashMap<>();

    static {
        // Debris
        family("dirt");
        family("pebble");
        family("debris");
        family("splinter");
        family("sand");
        family("snow");
        family("glass");
        family("grass");
        family("leaves");
        // Sprays and clouds
        family("smoke");
        family("smokecluster");
        family("sanddust");
        family("snowdust");
        family("watermist");
        family("splash");
        // Light
        family("spark");
        family("sparkcluster");
        family("bang");
        family("flash");
        family("fire");
        family("sparkbig");
        family("tracer");
        family("bubble");
    }

    private static void family(String name) {
        SPRITE_FAMILIES.put(name, TYPES.register("impact_" + name, () -> new SimpleParticleType(true)));
    }

    private ModParticles() {
    }
}
