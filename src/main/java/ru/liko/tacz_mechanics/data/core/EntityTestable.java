package ru.liko.tacz_mechanics.data.core;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Function;

public sealed interface EntityTestable permits EntityTestable.EntityMatch, EntityTestable.EntityTagMatch {
    
    boolean test(Entity entity);
    
    record EntityMatch(EntityType<?> type) implements EntityTestable {
        public static final Codec<EntityMatch> CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec()
            .xmap(EntityMatch::new, EntityMatch::type);
        
        @Override
        public boolean test(Entity entity) {
            return entity.getType() == type;
        }
    }
    
    record EntityTagMatch(TagKey<EntityType<?>> tag) implements EntityTestable {
        public static final Codec<EntityTagMatch> CODEC = TagKey.hashedCodec(Registries.ENTITY_TYPE)
            .xmap(EntityTagMatch::new, EntityTagMatch::tag);
        
        @Override
        public boolean test(Entity entity) {
            return entity.getType().is(tag);
        }
    }
    
    Codec<EntityTestable> CODEC = Codec.either(EntityMatch.CODEC, EntityTagMatch.CODEC)
        .xmap(
            either -> either.map(Function.identity(), Function.identity()),
            testable -> testable instanceof EntityMatch em ? Either.left(em) : Either.right((EntityTagMatch) testable)
        );
}
