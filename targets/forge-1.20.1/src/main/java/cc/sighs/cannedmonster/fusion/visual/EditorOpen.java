package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;

/** Server to client: open the visual editor for a resolved target. */
public record EditorOpen(InteractionHand hand, long session, int revision, CompoundTag layout, boolean admin) {
    public static void write(FriendlyByteBuf buffer, EditorOpen packet) {
        buffer.writeEnum(packet.hand == null ? InteractionHand.MAIN_HAND : packet.hand);
        buffer.writeLong(packet.session);
        buffer.writeVarInt(packet.revision);
        buffer.writeNbt(packet.layout == null ? new CompoundTag() : packet.layout);
        buffer.writeBoolean(packet.admin);
    }

    public static EditorOpen read(FriendlyByteBuf buffer) {
        return new EditorOpen(buffer.readEnum(InteractionHand.class), buffer.readLong(), buffer.readVarInt(),
                buffer.readNbt(), buffer.readBoolean());
    }
}
