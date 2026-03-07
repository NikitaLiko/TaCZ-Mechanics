package ru.liko.tacz_mechanics.movement.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.tacz_mechanics.TaczMechanics;

/**
 * Network payload for synchronizing player movement state.
 */
public record MovementStatePayload(int stateCode) implements CustomPacketPayload {
    public static final Type<MovementStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TaczMechanics.MODID, "movement_state")
    );
    
    public static final StreamCodec<ByteBuf, MovementStatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        MovementStatePayload::stateCode,
        MovementStatePayload::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
