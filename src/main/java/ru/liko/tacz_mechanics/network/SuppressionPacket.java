package ru.liko.tacz_mechanics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.client.suppression.SuppressionHandler;

public record SuppressionPacket(float intensity) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SuppressionPacket> TYPE = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(TaczMechanics.MODID, "suppression")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SuppressionPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SuppressionPacket decode(RegistryFriendlyByteBuf buf) {
            return new SuppressionPacket(buf.readFloat());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SuppressionPacket packet) {
            buf.writeFloat(packet.intensity());
        }
    };

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SuppressionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SuppressionHandler.addSuppression(packet.intensity()));
    }
}
