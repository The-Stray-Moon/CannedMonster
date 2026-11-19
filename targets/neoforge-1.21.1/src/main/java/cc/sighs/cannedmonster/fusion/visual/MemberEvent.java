package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record MemberEvent(FusionAppearance appearance, UUID member, long sequence, byte event) implements CustomPacketPayload {
    public static final Type<MemberEvent> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cannedmonster", "member_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MemberEvent> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                FusionAppearance.CODEC.encode(buffer, packet.appearance);
                buffer.writeUUID(packet.member);
                buffer.writeVarLong(packet.sequence);
                buffer.writeByte(packet.event);
            }, buffer -> new MemberEvent(FusionAppearance.CODEC.decode(buffer), buffer.readUUID(), buffer.readVarLong(), buffer.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
