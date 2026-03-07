package ru.liko.tacz_mechanics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.tacz_mechanics.TaczMechanics;

/**
 * Packet sent from server to client for distant fire sounds.
 * Contains shooter position, caliber ID, and sound info for playing distant gunfire.
 */
public record DistantFireSoundPacket(
    double x,
    double y, 
    double z,
    String caliberId,
    String soundId,
    float volume,
    float pitch
) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<DistantFireSoundPacket> TYPE = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(TaczMechanics.MODID, "distant_fire_sound")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, DistantFireSoundPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DistantFireSoundPacket decode(RegistryFriendlyByteBuf buf) {
            return new DistantFireSoundPacket(
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readFloat(),
                buf.readFloat()
            );
        }
        
        @Override
        public void encode(RegistryFriendlyByteBuf buf, DistantFireSoundPacket packet) {
            buf.writeDouble(packet.x());
            buf.writeDouble(packet.y());
            buf.writeDouble(packet.z());
            buf.writeUtf(packet.caliberId());
            buf.writeUtf(packet.soundId());
            buf.writeFloat(packet.volume());
            buf.writeFloat(packet.pitch());
        }
    };
    
    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(DistantFireSoundPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ru.liko.tacz_mechanics.client.sound.DistantFireClientHandler.handleDistantFireSound(packet));
    }
}
