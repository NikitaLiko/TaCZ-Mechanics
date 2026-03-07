package ru.liko.tacz_mechanics.data.core;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

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
        public static final Codec<BlockTagMatch> CODEC = TagKey.hashedCodec(Registries.BLOCK)
            .xmap(BlockTagMatch::new, BlockTagMatch::tag);
        
        @Override
        public boolean test(ServerLevel level, BlockPos pos, BlockState state) {
            return state.is(tag);
        }
    }
    
    Codec<BlockTestable> CODEC = Codec.either(BlockMatch.CODEC, BlockTagMatch.CODEC)
        .xmap(
            either -> either.map(Function.identity(), Function.identity()),
            testable -> testable instanceof BlockMatch bm ? Either.left(bm) : Either.right((BlockTagMatch) testable)
        );
}
