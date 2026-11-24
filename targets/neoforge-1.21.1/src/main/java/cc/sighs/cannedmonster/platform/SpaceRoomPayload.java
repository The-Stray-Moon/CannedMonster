package cc.sighs.cannedmonster.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Activates/deactivates the client-side space view for one session. The room itself
 * is derived from the common {@link RoomGeometry} on the client, so the payload only
 * carries the session identity and the switch; no real chunk coordinates travel.
 */
public record SpaceRoomPayload(UUID session, long generation, boolean active) implements CustomPacketPayload {
    public static final Type<SpaceRoomPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("cannedmonster", "space_room"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceRoomPayload> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUUID(packet.session);
                buffer.writeLong(packet.generation);
                buffer.writeBoolean(packet.active);
            },
            buffer -> new SpaceRoomPayload(buffer.readUUID(), buffer.readLong(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
