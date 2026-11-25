package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.FriendlyByteBuf;

/** Client to server: the editor closed, with the last revision it saw. */
public record EditorAck(long session, int revision, boolean ok, String message) {
    public static void write(FriendlyByteBuf buffer, EditorAck packet) {
        buffer.writeLong(packet.session);
        buffer.writeVarInt(packet.revision);
        buffer.writeBoolean(packet.ok);
        buffer.writeUtf(packet.message == null ? "" : packet.message, 32767);
    }

    public static EditorAck read(FriendlyByteBuf buffer) {
        return new EditorAck(buffer.readLong(), buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(32767));
    }
}
