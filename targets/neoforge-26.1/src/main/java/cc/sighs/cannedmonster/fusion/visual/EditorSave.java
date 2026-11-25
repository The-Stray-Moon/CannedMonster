package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: commit a layout for one of the four editor scopes. */
public record EditorSave(long session, int revision, int scope, CompoundTag layout) implements CustomPacketPayload {
    public static final Type<EditorSave> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cannedmonster", "editor_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditorSave> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeLong(packet.session);
                buffer.writeVarInt(packet.revision);
                buffer.writeVarInt(packet.scope);
                buffer.writeNbt(packet.layout == null ? new CompoundTag() : packet.layout);
            },
            buffer -> new EditorSave(buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
