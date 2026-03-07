package ru.liko.tacz_mechanics.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import ru.liko.tacz_mechanics.data.codec.CodecUtils;
import ru.liko.tacz_mechanics.data.core.ValueRange;

import java.util.List;
import java.util.Optional;

/**
 * Distant fire sound configuration.
 * Configured per ammo type (caliber), not per weapon.
 */
public record DistantFire(
    List<ResourceLocation> ammoTypes,
    List<SoundLayer> layers,
    int priority
) {
    
    public static final Codec<DistantFire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        CodecUtils.singleOrListCodec(ResourceLocation.CODEC).fieldOf("ammo").forGetter(DistantFire::ammoTypes),
        Codec.list(SoundLayer.CODEC).fieldOf("layers").forGetter(DistantFire::layers),
        CodecUtils.strictOptionalFieldOf(Codec.INT, "priority", 0).forGetter(DistantFire::priority)
    ).apply(instance, DistantFire::new));
    
    /**
     * A sound layer that plays at a specific distance range.
     * 
     * @param range Distance range in blocks [min, max]
     * @param sound Sound to play
     * @param volume Volume multiplier (0.0 - 1.0)
     * @param pitch Pitch multiplier
     * @param delayPerBlock Delay in ticks per block of distance (for sound travel simulation)
     *                      Real sound speed is ~343 m/s ≈ 17 blocks/second ≈ 0.05 ticks/block
     */
    public record SoundLayer(
        ValueRange range,
        ResourceLocation sound,
        float volume,
        float pitch,
        float delayPerBlock
    ) {
        public static final Codec<SoundLayer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ValueRange.CODEC.fieldOf("range").forGetter(SoundLayer::range),
            ResourceLocation.CODEC.fieldOf("sound").forGetter(SoundLayer::sound),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "volume", 1.0f).forGetter(SoundLayer::volume),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "pitch", 1.0f).forGetter(SoundLayer::pitch),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "delay_per_block", 0.0f).forGetter(SoundLayer::delayPerBlock)
        ).apply(instance, SoundLayer::new));
        
        /**
         * Check if this layer applies to the given distance.
         */
        public boolean matchesDistance(double distance) {
            return range.contains((float) distance);
        }
        
        /**
         * Calculate delay in ticks for the given distance.
         */
        public int calculateDelayTicks(double distance) {
            return (int) (distance * delayPerBlock);
        }
    }
    
    /**
     * Find the appropriate sound layer for the given distance.
     */
    public Optional<SoundLayer> getLayerForDistance(double distance) {
        for (SoundLayer layer : layers) {
            if (layer.matchesDistance(distance)) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Check if this config applies to the given ammo type.
     */
    public boolean matchesAmmo(ResourceLocation ammoId) {
        if (ammoTypes.isEmpty()) {
            return true; // Empty list means match all
        }
        return ammoTypes.contains(ammoId);
    }
}
