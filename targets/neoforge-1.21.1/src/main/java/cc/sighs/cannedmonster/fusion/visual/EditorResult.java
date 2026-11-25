package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: result of a save, including the new revision. */
public record EditorResult(long session, int revision, boolean ok, String message) implements CustomPacketPayload {
    public static final Type<EditorResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cannedmonster", "editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditorResult> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeLong(packet.session);
                buffer.writeVarInt(packet.revision);
                buffer.writeBoolean(packet.ok);
                buffer.writeUtf(packet.message == null ? "" : packet.message, 32767);
            },
            buffer -> new EditorResult(buffer.readLong(), buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(32767)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
