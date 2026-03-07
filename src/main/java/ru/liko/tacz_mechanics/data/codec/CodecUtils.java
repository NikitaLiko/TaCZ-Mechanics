package ru.liko.tacz_mechanics.data.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class CodecUtils {

    /**
     * Creates a codec that accepts either a single element or a list of elements.
     */
    public static <T> Codec<List<T>> singleOrListCodec(Codec<T> codec) {
        return Codec.either(codec, Codec.list(codec))
            .xmap(
                either -> either.map(List::of, Function.identity()),
                list -> list.size() == 1 ? Either.left(list.getFirst()) : Either.right(list)
            );
    }

    /**
     * Creates a strict optional field codec that fails on parse errors instead of returning empty.
     */
    public static <A> MapCodec<Optional<A>> strictOptionalFieldOf(Codec<A> codec, String name) {
        return new StrictOptionalFieldCodec<>(name, codec);
    }

    /**
     * Creates a strict optional field codec with a default value.
     */
    public static <A> MapCodec<A> strictOptionalFieldOf(Codec<A> codec, String name, A defaultValue) {
        return new StrictOptionalFieldCodec<>(name, codec).xmap(
            opt -> opt.orElse(defaultValue),
            value -> Objects.equals(value, defaultValue) ? Optional.empty() : Optional.of(value)
        );
    }

    private static class StrictOptionalFieldCodec<A> extends MapCodec<Optional<A>> {
        private final String name;
        private final Codec<A> elementCodec;

        public StrictOptionalFieldCodec(String name, Codec<A> elementCodec) {
            this.name = name;
            this.elementCodec = elementCodec;
        }

        @Override
        public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
            T value = input.get(name);
            if (value == null) {
                return DataResult.success(Optional.empty());
            }
            DataResult<A> parsed = elementCodec.parse(ops, value);
            return parsed.map(Optional::of);
        }

        @Override
        public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            if (input.isPresent()) {
                return prefix.add(name, elementCodec.encodeStart(ops, input.get()));
            }
            return prefix;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(ops.createString(name));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StrictOptionalFieldCodec<?> that)) return false;
            return Objects.equals(name, that.name) && Objects.equals(elementCodec, that.elementCodec);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, elementCodec);
        }

        @Override
        public String toString() {
            return "StrictOptionalFieldCodec[" + name + ": " + elementCodec + "]";
        }
    }
}
