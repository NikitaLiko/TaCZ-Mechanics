package ru.liko.tacz_mechanics.data.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import ru.liko.tacz_mechanics.data.codec.CodecUtils;

import com.mojang.serialization.MapCodec;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public sealed interface Target permits Target.AllOf, Target.AnyOf, Target.Inverted, Target.Gun, Target.Ammo, Target.Damage, Target.RandomChance {
    
    boolean test(ResourceLocation weaponId, ResourceLocation ammoId, float damage);
    
    TargetType getType();
    
    enum TargetType {
        ALL_OF("all_of", () -> AllOf.CODEC),
        ANY_OF("any_of", () -> AnyOf.CODEC),
        INVERTED("inverted", () -> Inverted.CODEC),
        GUN("gun", () -> Gun.CODEC),
        AMMO("ammo", () -> Ammo.CODEC),
        DAMAGE("damage", () -> Damage.CODEC),
        RANDOM_CHANCE("random_chance", () -> RandomChance.CODEC);
        
        private final String key;
        private final Supplier<MapCodec<? extends Target>> codecSupplier;
        
        TargetType(String key, Supplier<MapCodec<? extends Target>> codecSupplier) {
            this.key = key;
            this.codecSupplier = codecSupplier;
        }
        
        public String getKey() {
            return key;
        }
        
        public MapCodec<? extends Target> getCodec() {
            return codecSupplier.get();
        }
        
        private static final Map<String, TargetType> BY_KEY = Map.of(
            "all_of", ALL_OF,
            "any_of", ANY_OF,
            "inverted", INVERTED,
            "gun", GUN,
            "ammo", AMMO,
            "damage", DAMAGE,
            "random_chance", RANDOM_CHANCE
        );
        
        public static TargetType byKey(String key) {
            return BY_KEY.get(key);
        }
        
        public static final Codec<TargetType> CODEC = Codec.STRING.xmap(TargetType::byKey, TargetType::getKey);
    }
    
    Codec<Target> CODEC = TargetType.CODEC.dispatch(Target::getType, TargetType::getCodec);
    
    record AllOf(List<Target> terms) implements Target {
        public static final MapCodec<AllOf> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.list(Target.CODEC).fieldOf("terms").forGetter(AllOf::terms)
        ).apply(instance, AllOf::new));
        
        @Override
        public boolean test(ResourceLocation weaponId, ResourceLocation ammoId, float damage) {
            return terms.stream().allMatch(t -> t.test(weaponId, ammoId, damage));
        }
        
        @Override
        public TargetType getType() {
            return TargetType.ALL_OF;
        }
    }
    
    record AnyOf(List<Target> terms) implements Target {
        public static final MapCodec<AnyOf> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.list(Target.CODEC).fieldOf("terms").forGetter(AnyOf::terms)
        ).apply(instance, AnyOf::new));
        
        @Override
        public boolean test(ResourceLocation weaponId, ResourceLocation ammoId, float damage) {
            return terms.stream().anyMatch(t -> t.test(weaponId, ammoId, damage));
        }
        
        @Override
        public TargetType getType() {
            return TargetType.ANY_OF;
        }
    }
    
    record Inverted(Target term) implements Target {
        public static final MapCodec<Inverted> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Target.CODEC.fieldOf("term").forGetter(Inverted::term)
        ).apply(instance, Inverted::new));
        
        @Override
        public boolean test(ResourceLocation weaponId, ResourceLocation ammoId, float damage) {
            return !term.test(weaponId, ammoId, damage);
        }
        
        @Override
        public TargetType getType() {
            return TargetType.INVERTED;
        }
    }
    
    record Gun(List<ResourceLocation> values) implements Target {
        public static final MapCodec<Gun> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(Codec.list(ResourceLocation.CODEC), "values", List.of()).forGetter(Gun::values)
        ).apply(instance, Gun::new));
        
        @Override
        public boolean test(ResourceLocation weaponId, ResourceLocation ammoId, float damage) {
            return values.contains(weaponId);
        }
        
        @Override
        public TargetType getType() {
            return TargetType.GUN;
        }
    }
    
    record Ammo(List<ResourceLocation> values) implements Target {
        public static final MapCodec<Ammo> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(Codec.list(ResourceLocation.CODEC), "values", List.of()).forGetter(Ammo::values)
        ).apply(instance, Ammo::new));
        
        @Override
        public boolean test(ResourceLocation weaponId, ResourceLocation ammoId, float damage) {
            if (ammoId == null) {
                return false;
            }
            return values.contains(ammoId);
        }
        
        @Override
        public TargetType getType() {
            return TargetType.AMMO;
        }
    }

    record Damage(List<ValueRange> values) implements Target {
        public static final MapCodec<Damage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(Codec.list(ValueRange.CODEC), "values", List.of()).forGetter(Damage::values)
        ).apply(instance, Damage::new));
        
        @Override
        public boolean test(ResourceLocation weaponId, ResourceLocation ammoId, float damage) {
            return values.stream().anyMatch(r -> r.contains(damage));
        }
        
        @Override
        public TargetType getType() {
            return TargetType.DAMAGE;
        }
    }
    
    record RandomChance(float chance) implements Target {
        public static final MapCodec<RandomChance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("chance").forGetter(RandomChance::chance)
        ).apply(instance, RandomChance::new));
        
        @Override
        public boolean test(ResourceLocation weaponId, ResourceLocation ammoId, float damage) {
            return Math.random() < chance;
        }
        
        @Override
        public TargetType getType() {
            return TargetType.RANDOM_CHANCE;
        }
    }
}
