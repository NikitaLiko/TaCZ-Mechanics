package ru.liko.tacz_mechanics.data.distant_fire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Datapack-loaded distant fire configuration for a caliber.
 *
 * Pure data: only sounds and volumes per layer. Distance thresholds and crossfade transition
 * width live in {@link DistantFireBands} and are sent from server config; resolution logic
 * is in {@link DistantFireResolver}.
 */
public record DistantFireSound(String caliberId, List<DistantFireLayer> layers, List<DistantFireLayer> suppressedLayers) {
    private static final Codec<DistantFireSound> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("caliber_id").forGetter(DistantFireSound::caliberId),
        DistantFireLayer.CODEC.listOf().fieldOf("layers").forGetter(DistantFireSound::layers),
        DistantFireLayer.CODEC.listOf().optionalFieldOf("suppressed_layers", List.of()).forGetter(DistantFireSound::suppressedLayers)
    ).apply(instance, DistantFireSound::new));

    public static final Codec<DistantFireSound> CODEC = RAW_CODEC.validate(s -> s.layers().isEmpty()
        ? DataResult.error(() -> "DistantFireSound '" + s.caliberId() + "' must have at least one layer")
        : DataResult.success(s));

    public DistantFireSound {
        layers = List.copyOf(layers);
        suppressedLayers = List.copyOf(suppressedLayers);
    }

    /** Effective layer list for the given fire mode; falls back to normal layers when no suppressed set is defined. */
    public List<DistantFireLayer> layers(boolean suppressed) {
        return suppressed && !suppressedLayers.isEmpty() ? suppressedLayers : layers;
    }

    public DistantFireLayer layer(int index, boolean suppressed) {
        return layers(suppressed).get(index);
    }

    public int layerCount(boolean suppressed) {
        return layers(suppressed).size();
    }
}
