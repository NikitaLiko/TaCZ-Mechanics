package ru.liko.tacz_mechanics.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ru.liko.tacz_mechanics.data.codec.CodecUtils;
import ru.liko.tacz_mechanics.data.core.BlockTestable;
import ru.liko.tacz_mechanics.data.core.EntityTestable;
import ru.liko.tacz_mechanics.data.core.Target;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public sealed interface BulletParticles permits BulletParticles.BlockParticles, BulletParticles.EntityParticles {
    
    List<Target> getTarget();
    int getPriority();
    ParticleType getType();
    
    enum ParticleType {
        BLOCK("block", () -> BlockParticles.CODEC),
        ENTITY("entity", () -> EntityParticles.CODEC);
        
        private final String key;
        private final Supplier<MapCodec<? extends BulletParticles>> codecSupplier;
        
        ParticleType(String key, Supplier<MapCodec<? extends BulletParticles>> codecSupplier) {
            this.key = key;
            this.codecSupplier = codecSupplier;
        }
        
        public String getKey() {
            return key;
        }
        
        public MapCodec<? extends BulletParticles> getCodec() {
            return codecSupplier.get();
        }
        
        private static final Map<String, ParticleType> BY_KEY = Map.of(
            "block", BLOCK,
            "entity", ENTITY
        );
        
        public static ParticleType byKey(String key) {
            return BY_KEY.get(key);
        }
        
        public static final Codec<ParticleType> CODEC = Codec.STRING.xmap(ParticleType::byKey, ParticleType::getKey);
    }
    
    Codec<BulletParticles> CODEC = ParticleType.CODEC.dispatch(BulletParticles::getType, ParticleType::getCodec);
    
    enum CoordinatesType {
        ABSOLUTE("absolute"),
        RELATIVE("relative"),
        LOCAL("local");
        
        private final String key;
        
        CoordinatesType(String key) {
            this.key = key;
        }
        
        public String getKey() {
            return key;
        }
        
        private static final Map<String, CoordinatesType> BY_KEY = Map.of(
            "absolute", ABSOLUTE,
            "relative", RELATIVE,
            "local", LOCAL
        );
        
        public static CoordinatesType byKey(String key) {
            return BY_KEY.get(key);
        }
        
        public static final Codec<CoordinatesType> CODEC = Codec.STRING.xmap(CoordinatesType::byKey, CoordinatesType::getKey);
    }
    
    record Coordinates(CoordinatesType type, double x, double y, double z) {
        public static final Coordinates RELATIVE_ZERO = new Coordinates(CoordinatesType.RELATIVE, 0, 0, 0);
        public static final Coordinates ABSOLUTE_ZERO = new Coordinates(CoordinatesType.ABSOLUTE, 0, 0, 0);
        
        public static final Codec<Coordinates> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CoordinatesType.CODEC, "type", CoordinatesType.RELATIVE).forGetter(Coordinates::type),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "x", 0.0).forGetter(Coordinates::x),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "y", 0.0).forGetter(Coordinates::y),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "z", 0.0).forGetter(Coordinates::z)
        ).apply(instance, Coordinates::new));
    }
    
    record Particle(
        List<Target> target,
        String particle,
        Coordinates position,
        Coordinates delta,
        double speed,
        int count,
        boolean force,
        int duration
    ) {
        public static final Codec<Particle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(Particle::target),
            Codec.STRING.fieldOf("particle").forGetter(Particle::particle),
            CodecUtils.strictOptionalFieldOf(Coordinates.CODEC, "position", Coordinates.RELATIVE_ZERO).forGetter(Particle::position),
            CodecUtils.strictOptionalFieldOf(Coordinates.CODEC, "delta", Coordinates.ABSOLUTE_ZERO).forGetter(Particle::delta),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "speed", 0.0).forGetter(Particle::speed),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "count", 1).forGetter(Particle::count),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "force", false).forGetter(Particle::force),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "duration", 1).forGetter(Particle::duration)
        ).apply(instance, Particle::new));
    }
    
    record BlockParticleEntry(
        List<Target> target,
        List<BlockTestable> blocks,
        String particle,
        Coordinates position,
        Coordinates delta,
        double speed,
        int count,
        boolean force,
        int duration
    ) {
        public static final Codec<BlockParticleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(BlockParticleEntry::target),
            CodecUtils.strictOptionalFieldOf(Codec.list(BlockTestable.CODEC), "blocks", List.of()).forGetter(BlockParticleEntry::blocks),
            Codec.STRING.fieldOf("particle").forGetter(BlockParticleEntry::particle),
            CodecUtils.strictOptionalFieldOf(Coordinates.CODEC, "position", Coordinates.RELATIVE_ZERO).forGetter(BlockParticleEntry::position),
            CodecUtils.strictOptionalFieldOf(Coordinates.CODEC, "delta", Coordinates.ABSOLUTE_ZERO).forGetter(BlockParticleEntry::delta),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "speed", 0.0).forGetter(BlockParticleEntry::speed),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "count", 1).forGetter(BlockParticleEntry::count),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "force", false).forGetter(BlockParticleEntry::force),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "duration", 1).forGetter(BlockParticleEntry::duration)
        ).apply(instance, BlockParticleEntry::new));
    }
    
    record EntityParticleEntry(
        List<Target> target,
        List<EntityTestable> entities,
        String particle,
        Coordinates position,
        Coordinates delta,
        double speed,
        int count,
        boolean force,
        int duration
    ) {
        public static final Codec<EntityParticleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(EntityParticleEntry::target),
            CodecUtils.strictOptionalFieldOf(Codec.list(EntityTestable.CODEC), "entities", List.of()).forGetter(EntityParticleEntry::entities),
            Codec.STRING.fieldOf("particle").forGetter(EntityParticleEntry::particle),
            CodecUtils.strictOptionalFieldOf(Coordinates.CODEC, "position", Coordinates.RELATIVE_ZERO).forGetter(EntityParticleEntry::position),
            CodecUtils.strictOptionalFieldOf(Coordinates.CODEC, "delta", Coordinates.ABSOLUTE_ZERO).forGetter(EntityParticleEntry::delta),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "speed", 0.0).forGetter(EntityParticleEntry::speed),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "count", 1).forGetter(EntityParticleEntry::count),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "force", false).forGetter(EntityParticleEntry::force),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "duration", 1).forGetter(EntityParticleEntry::duration)
        ).apply(instance, EntityParticleEntry::new));
    }
    
    record BlockParticles(
        List<Target> target,
        List<BlockTestable> blocks,
        List<BlockParticleEntry> hit,
        List<BlockParticleEntry> pierce,
        List<BlockParticleEntry> breakParticle,
        List<BlockParticleEntry> ricochet,
        int priority
    ) implements BulletParticles {
        public static final MapCodec<BlockParticles> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(BlockParticles::target),
            CodecUtils.strictOptionalFieldOf(Codec.list(BlockTestable.CODEC), "blocks", List.of()).forGetter(BlockParticles::blocks),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(BlockParticleEntry.CODEC), "hit", List.of()).forGetter(BlockParticles::hit),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(BlockParticleEntry.CODEC), "pierce", List.of()).forGetter(BlockParticles::pierce),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(BlockParticleEntry.CODEC), "break", List.of()).forGetter(BlockParticles::breakParticle),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(BlockParticleEntry.CODEC), "ricochet", List.of()).forGetter(BlockParticles::ricochet),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "priority", 0).forGetter(BlockParticles::priority)
        ).apply(instance, BlockParticles::new));
        
        @Override
        public List<Target> getTarget() {
            return target;
        }
        
        @Override
        public int getPriority() {
            return priority;
        }
        
        @Override
        public ParticleType getType() {
            return ParticleType.BLOCK;
        }
    }
    
    record EntityParticles(
        List<Target> target,
        List<EntityTestable> entities,
        List<EntityParticleEntry> hit,
        List<EntityParticleEntry> pierce,
        List<EntityParticleEntry> kill,
        int priority
    ) implements BulletParticles {
        public static final MapCodec<EntityParticles> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(EntityParticles::target),
            CodecUtils.strictOptionalFieldOf(Codec.list(EntityTestable.CODEC), "entities", List.of()).forGetter(EntityParticles::entities),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(EntityParticleEntry.CODEC), "hit", List.of()).forGetter(EntityParticles::hit),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(EntityParticleEntry.CODEC), "pierce", List.of()).forGetter(EntityParticles::pierce),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(EntityParticleEntry.CODEC), "kill", List.of()).forGetter(EntityParticles::kill),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "priority", 0).forGetter(EntityParticles::priority)
        ).apply(instance, EntityParticles::new));
        
        @Override
        public List<Target> getTarget() {
            return target;
        }
        
        @Override
        public int getPriority() {
            return priority;
        }
        
        @Override
        public ParticleType getType() {
            return ParticleType.ENTITY;
        }
    }
}
