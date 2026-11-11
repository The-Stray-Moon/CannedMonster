package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FusionAppearance(int entityId, UUID bodyId, List<Member> members) implements CustomPacketPayload {
    public static final Type<FusionAppearance> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cannedmonster", "appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FusionAppearance> CODEC = StreamCodec.of(
            FusionAppearance::write, FusionAppearance::read);

    public record Member(UUID id, ResourceLocation type, List<SynchedEntityData.DataValue<?>> data) {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, FusionAppearance appearance) {
        buffer.writeVarInt(appearance.entityId);
        buffer.writeUUID(appearance.bodyId);
        buffer.writeVarInt(appearance.members.size());
        for (Member member : appearance.members) {
            buffer.writeUUID(member.id);
            buffer.writeResourceLocation(member.type);
            buffer.writeVarInt(member.data.size());
            member.data.forEach(value -> value.write(buffer));
        }
    }

    private static FusionAppearance read(RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        UUID bodyId = buffer.readUUID();
        int count = bounded(buffer.readVarInt(), 4096);
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buffer.readUUID();
            ResourceLocation type = buffer.readResourceLocation();
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(type)) throw new IllegalArgumentException("Unknown appearance type: " + type);
            int size = bounded(buffer.readVarInt(), 255);
            List<SynchedEntityData.DataValue<?>> data = new ArrayList<>(size);
            for (int j = 0; j < size; j++) data.add(SynchedEntityData.DataValue.read(buffer, buffer.readUnsignedByte()));
            members.add(new Member(id, type, List.copyOf(data)));
        }
        return new FusionAppearance(entityId, bodyId, List.copyOf(members));
    }

    private static int bounded(int value, int max) {
        if (value < 0 || value > max) throw new IllegalArgumentException("Invalid appearance count: " + value);
        return value;
    }
}
