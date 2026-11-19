package cc.sighs.cannedmonster.fusion;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RidingState(int entityId, UUID bodyId, UUID generation, boolean fusion, int controller, long sequence) implements CustomPacketPayload {
    public static final Type<RidingState> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cannedmonster", "riding"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RidingState> CODEC = StreamCodec.of(
            (buffer, state) -> {
                buffer.writeVarInt(state.entityId);
                buffer.writeUUID(state.bodyId);
                buffer.writeUUID(state.generation);
                buffer.writeBoolean(state.fusion);
                buffer.writeInt(state.controller);
                buffer.writeLong(state.sequence);
            }, buffer -> new RidingState(buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(), buffer.readBoolean(), buffer.readInt(), buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
