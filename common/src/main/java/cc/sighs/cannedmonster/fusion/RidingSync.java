package cc.sighs.cannedmonster.fusion;

import cc.sighs.cannedmonster.platform.AppearanceTransport;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class RidingSync {
    private static final Map<Mob, RidingState> SENT = new IdentityHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final UUID EMPTY = new UUID(0, 0);

    private RidingSync() {}

    private static RidingState snapshot(Mob body) {
        FusionMotion motion = FusionMotion.of(body);
        boolean fusion = motion != null;
        var source = fusion ? FusionRiding.source(body) : null;
        return new RidingState(body.getId(), body.getUUID(), fusion ? motion.generation : EMPTY, fusion,
                source == null ? -1 : body.getFirstPassenger().getId(), SEQUENCE.incrementAndGet());
    }

    public static void tick(Entity entity) {
        if (!(entity instanceof Mob body) || body.level().isClientSide()) return;
        RidingState old = SENT.get(body);
        if (!FusionMotion.isFusionBody(body) && old == null) return;
        RidingState state = snapshot(body);
        if (old == null || old.fusion() != state.fusion() || old.controller() != state.controller() || !old.generation().equals(state.generation())) {
            AppearanceTransport.tracking(body, state);
            if (state.fusion()) SENT.put(body, state); else SENT.remove(body);
        }
    }

    public static void tracking(Entity target, ServerPlayer player) {
        if (target instanceof Mob body && FusionMotion.isFusionBody(body)) AppearanceTransport.send(player, snapshot(body));
    }

    public static void leave(Entity entity) {
        if (!entity.level().isClientSide()) SENT.remove(entity);
    }

    public static void clear() { SENT.clear(); }
}
