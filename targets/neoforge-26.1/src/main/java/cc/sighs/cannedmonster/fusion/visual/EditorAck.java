package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: the editor closed, with the last revision it saw. */
public record EditorAck(long session, int revision, boolean ok, String message) implements CustomPacketPayload {
    public static final Type<EditorAck> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cannedmonster", "editor_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditorAck> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeLong(packet.session);
                buffer.writeVarInt(packet.revision);
                buffer.writeBoolean(packet.ok);
                buffer.writeUtf(packet.message == null ? "" : packet.message, 32767);
            },
            buffer -> new EditorAck(buffer.readLong(), buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(32767)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
