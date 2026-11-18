package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FusionAppearance(int entityId, UUID bodyId, int control, boolean flying, long revision, boolean full, List<Member> members) {
    public record Member(UUID id, ResourceLocation type, List<SynchedEntityData.DataValue<?>> data, CompoundTag presentation) {}

    public static void write(FriendlyByteBuf buffer, FusionAppearance appearance) {
        buffer.writeVarInt(appearance.entityId);
        buffer.writeUUID(appearance.bodyId);
        buffer.writeVarInt(appearance.control);
        buffer.writeBoolean(appearance.flying);
        buffer.writeVarLong(appearance.revision);
        buffer.writeBoolean(appearance.full);
        buffer.writeVarInt(appearance.members.size());
        for (Member member : appearance.members) {
            buffer.writeUUID(member.id);
            buffer.writeResourceLocation(member.type);
            buffer.writeVarInt(member.data.size());
            member.data.forEach(value -> value.write(buffer));
            buffer.writeNbt(member.presentation);
        }
    }

    public static FusionAppearance read(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        UUID bodyId = buffer.readUUID();
        int control = buffer.readVarInt();
        boolean flying = buffer.readBoolean();
        long revision = buffer.readVarLong();
        boolean full = buffer.readBoolean();
        int count = bounded(buffer.readVarInt(), 4096);
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buffer.readUUID();
            ResourceLocation type = buffer.readResourceLocation();
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(type)) throw new IllegalArgumentException("Unknown appearance type: " + type);
            int size = bounded(buffer.readVarInt(), 255);
            List<SynchedEntityData.DataValue<?>> data = new ArrayList<>(size);
            for (int j = 0; j < size; j++) data.add(SynchedEntityData.DataValue.read(buffer, buffer.readUnsignedByte()));
            CompoundTag presentation = buffer.readNbt();
            members.add(new Member(id, type, List.copyOf(data), presentation == null ? new CompoundTag() : presentation));
        }
        return new FusionAppearance(entityId, bodyId, control >= count ? -1 : control, flying, revision, full, List.copyOf(members));
    }

    private static int bounded(int value, int max) {
        if (value < 0 || value > max) throw new IllegalArgumentException("Invalid appearance count: " + value);
        return value;
    }
}
