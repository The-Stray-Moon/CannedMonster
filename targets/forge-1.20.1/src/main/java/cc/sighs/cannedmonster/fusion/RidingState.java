package cc.sighs.cannedmonster.fusion;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public record RidingState(int entityId, UUID bodyId, UUID generation, boolean fusion, int controller, long sequence) {
    private static final Map<Mob, RidingState> SENT = new IdentityHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final UUID EMPTY = new UUID(0, 0);

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

    private static RidingState snapshot(Mob body) {
        FusionMotion motion = FusionMotion.of(body);
        boolean fusion = motion != null;
        var source = fusion ? FusionRiding.source(body) : null;
        return new RidingState(body.getId(), body.getUUID(), fusion ? motion.generation : EMPTY, fusion,
                source == null ? -1 : body.getFirstPassenger().getId(), SEQUENCE.incrementAndGet());
    }

    public static void tick(Entity entity) {
        if (!(entity instanceof Mob body) || body.level().isClientSide) return;
        RidingState old = SENT.get(body);
        if (!FusionMotion.isFusionBody(body) && old == null) return;
        RidingState state = snapshot(body);
        if (old == null || old.fusion != state.fusion || old.controller != state.controller || !old.generation.equals(state.generation)) {
            FusionNetwork.sendToTracking(body, state);
            if (state.fusion) SENT.put(body, state); else SENT.remove(body);
        }
    }

    public static void tracking(Entity target, ServerPlayer player) {
        if (target instanceof Mob body && FusionMotion.isFusionBody(body)) {
            FusionNetwork.sendToPlayer(player, snapshot(body));
        }
    }

    public static void leave(Entity entity) {
        if (!entity.level().isClientSide) SENT.remove(entity);
    }

    public static void clear() { SENT.clear(); }
}
