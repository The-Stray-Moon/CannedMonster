package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Client to server: commit a layout for one of the four editor scopes. */
public record EditorSave(long session, int revision, int scope, CompoundTag layout) {
    public static void write(FriendlyByteBuf buffer, EditorSave packet) {
        buffer.writeLong(packet.session);
        buffer.writeVarInt(packet.revision);
        buffer.writeVarInt(packet.scope);
        buffer.writeNbt(packet.layout == null ? new CompoundTag() : packet.layout);
    }

    public static EditorSave read(FriendlyByteBuf buffer) {
        return new EditorSave(buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readNbt());
    }
}
