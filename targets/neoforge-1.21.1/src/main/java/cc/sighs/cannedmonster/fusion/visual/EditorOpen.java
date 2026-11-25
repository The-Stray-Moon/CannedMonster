package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** Server to client: open the visual editor for a resolved target. */
public record EditorOpen(InteractionHand hand, long session, int revision, CompoundTag layout, boolean admin)
        implements CustomPacketPayload {
    public static final Type<EditorOpen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cannedmonster", "editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditorOpen> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeEnum(packet.hand == null ? InteractionHand.MAIN_HAND : packet.hand);
                buffer.writeLong(packet.session);
                buffer.writeVarInt(packet.revision);
                buffer.writeNbt(packet.layout == null ? new CompoundTag() : packet.layout);
                buffer.writeBoolean(packet.admin);
            },
            buffer -> new EditorOpen(buffer.readEnum(InteractionHand.class), buffer.readLong(), buffer.readVarInt(),
                    buffer.readNbt(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
