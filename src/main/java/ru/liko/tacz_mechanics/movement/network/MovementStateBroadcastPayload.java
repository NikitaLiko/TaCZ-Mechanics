package ru.liko.tacz_mechanics.movement.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.tacz_mechanics.TaczMechanics;

import java.util.UUID;

/**
 * Network payload for broadcasting a player's movement state to all clients.
 */
public record MovementStateBroadcastPayload(UUID playerId, int stateCode) implements CustomPacketPayload {
    public static final Type<MovementStateBroadcastPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TaczMechanics.MODID, "movement_state_broadcast")
    );
    
    public static final StreamCodec<ByteBuf, MovementStateBroadcastPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString),
        MovementStateBroadcastPayload::playerId,
        ByteBufCodecs.VAR_INT,
        MovementStateBroadcastPayload::stateCode,
        MovementStateBroadcastPayload::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
