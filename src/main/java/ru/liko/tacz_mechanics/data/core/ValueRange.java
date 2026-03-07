package ru.liko.tacz_mechanics.data.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ru.liko.tacz_mechanics.data.codec.CodecUtils;

public record ValueRange(double min, double max) {
    
    public static final ValueRange DEFAULT = new ValueRange(Double.MIN_VALUE, Double.MAX_VALUE);
    
    public static final Codec<ValueRange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "min", DEFAULT.min()).forGetter(ValueRange::min),
        CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "max", DEFAULT.max()).forGetter(ValueRange::max)
    ).apply(instance, ValueRange::new));
    
    public boolean contains(double value) {
        return value >= min && value <= max;
    }
    
    public boolean contains(float value) {
        return contains((double) value);
    }
}
