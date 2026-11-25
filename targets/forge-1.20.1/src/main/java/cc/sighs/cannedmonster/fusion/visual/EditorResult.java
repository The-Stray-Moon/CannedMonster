package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.FriendlyByteBuf;

/** Server to client: result of a save, including the new revision. */
public record EditorResult(long session, int revision, boolean ok, String message) {
    public static void write(FriendlyByteBuf buffer, EditorResult packet) {
        buffer.writeLong(packet.session);
        buffer.writeVarInt(packet.revision);
        buffer.writeBoolean(packet.ok);
        buffer.writeUtf(packet.message == null ? "" : packet.message, 32767);
    }

    public static EditorResult read(FriendlyByteBuf buffer) {
        return new EditorResult(buffer.readLong(), buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(32767));
    }
}
