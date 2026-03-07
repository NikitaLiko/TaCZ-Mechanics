package ru.liko.tacz_mechanics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.server.FreeAimServerHandler;

/**
 * Packet sent from client to server to sync free aim offset.
 * Server uses this to adjust bullet direction when shooting.
 */
public record FreeAimSyncPacket(
    float pitchOffset,
    float yawOffset
) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<FreeAimSyncPacket> TYPE = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(TaczMechanics.MODID, "free_aim_sync")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeAimSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FreeAimSyncPacket decode(RegistryFriendlyByteBuf buf) {
            return new FreeAimSyncPacket(
                buf.readFloat(),
                buf.readFloat()
            );
        }
        
        @Override
        public void encode(RegistryFriendlyByteBuf buf, FreeAimSyncPacket packet) {
            buf.writeFloat(packet.pitchOffset());
            buf.writeFloat(packet.yawOffset());
        }
    };
    
    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(FreeAimSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FreeAimServerHandler.updatePlayerFreeAim(serverPlayer, packet.pitchOffset(), packet.yawOffset());
            }
        });
    }
}
