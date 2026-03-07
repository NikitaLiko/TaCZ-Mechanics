package ru.liko.tacz_mechanics.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import ru.liko.tacz_mechanics.data.codec.CodecUtils;
import ru.liko.tacz_mechanics.data.core.BlockTestable;
import ru.liko.tacz_mechanics.data.core.EntityTestable;
import ru.liko.tacz_mechanics.data.core.Target;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public sealed interface BulletSounds permits BulletSounds.BlockSound, BulletSounds.EntitySound {
    
    List<Target> getTarget();
    int getPriority();
    SoundType getType();
    
    enum SoundType {
        BLOCK("block", () -> BlockSound.CODEC),
        ENTITY("entity", () -> EntitySound.CODEC);
        
        private final String key;
        private final Supplier<MapCodec<? extends BulletSounds>> codecSupplier;
        
        SoundType(String key, Supplier<MapCodec<? extends BulletSounds>> codecSupplier) {
            this.key = key;
            this.codecSupplier = codecSupplier;
        }
        
        public String getKey() {
            return key;
        }
        
        public MapCodec<? extends BulletSounds> getCodec() {
            return codecSupplier.get();
        }
        
        private static final Map<String, SoundType> BY_KEY = Map.of(
            "block", BLOCK,
            "entity", ENTITY
        );
        
        public static SoundType byKey(String key) {
            return BY_KEY.get(key);
        }
        
        public static final Codec<SoundType> CODEC = Codec.STRING.xmap(SoundType::byKey, SoundType::getKey);
    }
    
    Codec<BulletSounds> CODEC = SoundType.CODEC.dispatch(BulletSounds::getType, SoundType::getCodec);
    
    record Sound(
        List<Target> target,
        ResourceLocation sound,
        float volume,
        float pitch,
        Optional<Float> range
    ) {
        public static final Codec<Sound> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(Sound::target),
            ResourceLocation.CODEC.fieldOf("sound").forGetter(Sound::sound),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "volume", 1.0f).forGetter(Sound::volume),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "pitch", 1.0f).forGetter(Sound::pitch),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "range").forGetter(Sound::range)
        ).apply(instance, Sound::new));
    }
    
    record BlockSoundEntry(
        List<Target> target,
        List<BlockTestable> blocks,
        ResourceLocation sound,
        float volume,
        float pitch,
        Optional<Float> range
    ) {
        public static final Codec<BlockSoundEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(BlockSoundEntry::target),
            CodecUtils.strictOptionalFieldOf(Codec.list(BlockTestable.CODEC), "blocks", List.of()).forGetter(BlockSoundEntry::blocks),
            ResourceLocation.CODEC.fieldOf("sound").forGetter(BlockSoundEntry::sound),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "volume", 1.0f).forGetter(BlockSoundEntry::volume),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "pitch", 1.0f).forGetter(BlockSoundEntry::pitch),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "range").forGetter(BlockSoundEntry::range)
        ).apply(instance, BlockSoundEntry::new));
    }
    
    record EntitySoundEntry(
        List<Target> target,
        List<EntityTestable> entities,
        ResourceLocation sound,
        float volume,
        float pitch,
        Optional<Float> range
    ) {
        public static final Codec<EntitySoundEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(EntitySoundEntry::target),
            CodecUtils.strictOptionalFieldOf(Codec.list(EntityTestable.CODEC), "entities", List.of()).forGetter(EntitySoundEntry::entities),
            ResourceLocation.CODEC.fieldOf("sound").forGetter(EntitySoundEntry::sound),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "volume", 1.0f).forGetter(EntitySoundEntry::volume),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "pitch", 1.0f).forGetter(EntitySoundEntry::pitch),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "range").forGetter(EntitySoundEntry::range)
        ).apply(instance, EntitySoundEntry::new));
    }
    
    record BlockSound(
        List<Target> target,
        List<BlockTestable> blocks,
        List<BlockSoundEntry> hit,
        List<BlockSoundEntry> ricochet,
        List<BlockSoundEntry> pierce,
        List<BlockSoundEntry> breakSound,
        int priority
    ) implements BulletSounds {
        public static final MapCodec<BlockSound> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(BlockSound::target),
            CodecUtils.strictOptionalFieldOf(Codec.list(BlockTestable.CODEC), "blocks", List.of()).forGetter(BlockSound::blocks),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(BlockSoundEntry.CODEC), "hit", List.of()).forGetter(BlockSound::hit),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(BlockSoundEntry.CODEC), "ricochet", List.of()).forGetter(BlockSound::ricochet),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(BlockSoundEntry.CODEC), "pierce", List.of()).forGetter(BlockSound::pierce),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(BlockSoundEntry.CODEC), "break", List.of()).forGetter(BlockSound::breakSound),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "priority", 0).forGetter(BlockSound::priority)
        ).apply(instance, BlockSound::new));
        
        @Override
        public List<Target> getTarget() {
            return target;
        }
        
        @Override
        public int getPriority() {
            return priority;
        }
        
        @Override
        public SoundType getType() {
            return SoundType.BLOCK;
        }
    }
    
    record EntitySound(
        List<Target> target,
        List<EntityTestable> entities,
        List<EntitySoundEntry> hit,
        List<EntitySoundEntry> pierce,
        List<EntitySoundEntry> kill,
        int priority
    ) implements BulletSounds {
        public static final MapCodec<EntitySound> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(Target.CODEC), "target", List.of()).forGetter(EntitySound::target),
            CodecUtils.strictOptionalFieldOf(Codec.list(EntityTestable.CODEC), "entities", List.of()).forGetter(EntitySound::entities),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(EntitySoundEntry.CODEC), "hit", List.of()).forGetter(EntitySound::hit),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(EntitySoundEntry.CODEC), "pierce", List.of()).forGetter(EntitySound::pierce),
            CodecUtils.strictOptionalFieldOf(CodecUtils.singleOrListCodec(EntitySoundEntry.CODEC), "kill", List.of()).forGetter(EntitySound::kill),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "priority", 0).forGetter(EntitySound::priority)
        ).apply(instance, EntitySound::new));
        
        @Override
        public List<Target> getTarget() {
            return target;
        }
        
        @Override
        public int getPriority() {
            return priority;
        }
        
        @Override
        public SoundType getType() {
            return SoundType.ENTITY;
        }
    }
}
