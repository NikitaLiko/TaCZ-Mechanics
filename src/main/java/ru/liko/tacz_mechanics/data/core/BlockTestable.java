package ru.liko.tacz_mechanics.data.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public sealed interface BlockTestable permits BlockTestable.BlockMatch, BlockTestable.BlockTagMatch {
    
    boolean test(ServerLevel level, BlockPos pos, BlockState state);
    
    record BlockMatch(Block block) implements BlockTestable {
        public static final Codec<BlockMatch> CODEC = BuiltInRegistries.BLOCK.byNameCodec()
            .xmap(BlockMatch::new, BlockMatch::block);
        
        @Override
        public boolean test(ServerLevel level, BlockPos pos, BlockState state) {
            return state.is(block);
        }
    }
    
    record BlockTagMatch(TagKey<Block> tag) implements BlockTestable {
        public static final Codec<BlockTagMatch> CODEC = ResourceLocation.CODEC
            .flatXmap(id -> DataResult.success(new BlockTagMatch(TagKey.create(Registries.BLOCK, id))), btm -> DataResult.success(btm.tag.location()));
        
        @Override
        public boolean test(ServerLevel level, BlockPos pos, BlockState state) {
            return state.is(tag);
        }
    }
    
    /** Parses block ID ("minecraft:stone") or tag ("#namespace:tag") */
    Codec<BlockTestable> CODEC = Codec.STRING.flatXmap(
        str -> {
            if (str.startsWith("#")) {
                try {
                    ResourceLocation id = ResourceLocation.parse(str.substring(1));
                    return DataResult.success(new BlockTagMatch(TagKey.create(Registries.BLOCK, id)));
                } catch (Exception e) {
                    return DataResult.error(() -> "Invalid tag: " + str);
                }
            } else {
                try {
                    ResourceLocation id = ResourceLocation.parse(str);
                    return BuiltInRegistries.BLOCK.getOptional(id)
                        .map(BlockMatch::new)
                        .map(b -> DataResult.<BlockTestable>success(b))
                        .orElse(DataResult.error(() -> "Unknown block: " + str));
                } catch (Exception e) {
                    return DataResult.error(() -> "Invalid block: " + str);
                }
            }
        },
        testable -> {
            if (testable instanceof BlockTagMatch btm) {
                return DataResult.success("#" + btm.tag.location());
            } else {
                return DataResult.success(BuiltInRegistries.BLOCK.getKey(((BlockMatch) testable).block()).toString());
            }
        }
    );
}
