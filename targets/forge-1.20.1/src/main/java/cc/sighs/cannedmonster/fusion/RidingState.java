package cc.sighs.cannedmonster.fusion;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record RidingState(int entityId, UUID bodyId, UUID generation, boolean fusion, int controller, long sequence) {
    public static void write(FriendlyByteBuf buffer, RidingState state) {
        buffer.writeVarInt(state.entityId);
        buffer.writeUUID(state.bodyId);
        buffer.writeUUID(state.generation);
        buffer.writeBoolean(state.fusion);
        buffer.writeInt(state.controller);
        buffer.writeLong(state.sequence);
    }

    public static RidingState read(FriendlyByteBuf buffer) {
        return new RidingState(buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(), buffer.readBoolean(),
                buffer.readInt(), buffer.readLong());
    }
}
