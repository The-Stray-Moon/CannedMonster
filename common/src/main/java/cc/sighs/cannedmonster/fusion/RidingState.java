package cc.sighs.cannedmonster.fusion;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public record RidingState(int entityId, UUID bodyId, UUID generation, boolean fusion, int controller, long sequence) implements CustomPacketPayload {
    public static final Type<RidingState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cannedmonster", "riding"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RidingState> CODEC = StreamCodec.of(
            (buffer, state) -> {
                buffer.writeVarInt(state.entityId);
                buffer.writeUUID(state.bodyId);
                buffer.writeUUID(state.generation);
                buffer.writeBoolean(state.fusion);
                buffer.writeInt(state.controller);
                buffer.writeLong(state.sequence);
            }, buffer -> new RidingState(buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(), buffer.readBoolean(), buffer.readInt(), buffer.readLong()));
    private static final Map<Mob, RidingState> SENT = new IdentityHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final UUID EMPTY = new UUID(0, 0);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static RidingState snapshot(Mob body) {
        FusionMotion motion = FusionMotion.of(body);
        boolean fusion = motion != null;
        var source = fusion ? FusionRiding.source(body) : null;
        return new RidingState(body.getId(), body.getUUID(), fusion ? motion.generation : EMPTY, fusion,
                source == null ? -1 : body.getFirstPassenger().getId(), SEQUENCE.incrementAndGet());
    }

    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob body) || body.level().isClientSide) return;
        RidingState old = SENT.get(body);
        if (!FusionMotion.isFusionBody(body) && old == null) return;
        RidingState state = snapshot(body);
        if (old == null || old.fusion != state.fusion || old.controller != state.controller || !old.generation.equals(state.generation)) {
            PacketDistributor.sendToPlayersTrackingEntity(body, state);
            if (state.fusion) SENT.put(body, state); else SENT.remove(body);
        }
    }

    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Mob body && FusionMotion.isFusionBody(body)
                && event.getEntity() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, snapshot(body));
    }

    public static void leave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide) SENT.remove(event.getEntity());
    }

    public static void clear() { SENT.clear(); }
}
