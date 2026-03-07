package ru.liko.tacz_mechanics.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ru.liko.tacz_mechanics.data.codec.CodecUtils;
import ru.liko.tacz_mechanics.data.core.BlockTestable;
import ru.liko.tacz_mechanics.data.core.Target;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public sealed interface BulletInteractions permits BulletInteractions.BlockInteraction {
    
    List<Target> getTarget();
    int getPriority();
    InteractionType getType();
    
    enum InteractionType {
        BLOCK("block", () -> BlockInteraction.CODEC);
        
        private final String key;
        private final Supplier<MapCodec<? extends BulletInteractions>> codecSupplier;
        
        InteractionType(String key, Supplier<MapCodec<? extends BulletInteractions>> codecSupplier) {
            this.key = key;
            this.codecSupplier = codecSupplier;
        }
        
        public String getKey() {
            return key;
        }
        
        public MapCodec<? extends BulletInteractions> getCodec() {
            return codecSupplier.get();
        }
        
        private static final Map<String, InteractionType> BY_KEY = Map.of(
            "block", BLOCK
        );
        
        public static InteractionType byKey(String key) {
            return BY_KEY.get(key);
        }
        
        public static final Codec<InteractionType> CODEC = Codec.STRING.xmap(InteractionType::byKey, InteractionType::getKey);
    }
    
    Codec<BulletInteractions> CODEC = InteractionType.CODEC.dispatch(BulletInteractions::getType, InteractionType::getCodec);
    
    record PierceSettings(
        float chance,
        float minDamage,
        float maxDistance,
        float maxHardness,
        float damageMultiplier,
        float speedMultiplier,
        boolean spawnHole,
        boolean spawnExitHole,
        boolean spawnParticles,
        boolean spawnSounds
    ) {
        public static final Codec<PierceSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "chance", 1.0f).forGetter(PierceSettings::chance),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "min_damage", 0.0f).forGetter(PierceSettings::minDamage),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "max_distance", -1.0f).forGetter(PierceSettings::maxDistance),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "max_hardness", -1.0f).forGetter(PierceSettings::maxHardness),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "damage_multiplier", 0.7f).forGetter(PierceSettings::damageMultiplier),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "speed_multiplier", 0.7f).forGetter(PierceSettings::speedMultiplier),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "spawn_hole", true).forGetter(PierceSettings::spawnHole),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "spawn_exit_hole", true).forGetter(PierceSettings::spawnExitHole),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "spawn_particles", true).forGetter(PierceSettings::spawnParticles),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "spawn_sounds", true).forGetter(PierceSettings::spawnSounds)
        ).apply(instance, PierceSettings::new));
    }
    
    record BlockInteraction(
        List<Target> target,
        List<BlockTestable> blocks,
        PierceSettings pierce,
        int priority
    ) implements BulletInteractions {
        public static final MapCodec<BlockInteraction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(BlockInteraction::target),
            CodecUtils.strictOptionalFieldOf(Codec.list(BlockTestable.CODEC), "blocks", List.of()).forGetter(BlockInteraction::blocks),
            PierceSettings.CODEC.fieldOf("pierce").forGetter(BlockInteraction::pierce),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "priority", 0).forGetter(BlockInteraction::priority)
        ).apply(instance, BlockInteraction::new));
        
        @Override
        public List<Target> getTarget() {
            return target;
        }
        
        @Override
        public int getPriority() {
            return priority;
        }
        
        @Override
        public InteractionType getType() {
            return InteractionType.BLOCK;
        }
    }
}
