package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.fusion.RidingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientRiding {
    private static final Map<UUID, RidingState> STATES = new HashMap<>();
    private static ClientLevel level;

    private ClientRiding() {}

    public static void receive(RidingState state) {
        tick();
        RidingState old = STATES.get(state.bodyId());
        if (old == null || state.sequence() >= old.sequence()) STATES.put(state.bodyId(), state);
    }

    public static void leave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) STATES.remove(event.getEntity().getUUID());
    }

    public static void tick() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            STATES.clear();
            level = current;
        }
        if (level != null) STATES.entrySet().removeIf(entry -> {
            var entity = level.getEntity(entry.getValue().entityId());
            return entity != null && (!entity.getUUID().equals(entry.getKey()) || entity.isRemoved());
        });
    }

    public static boolean fusion(Mob body) {
        RidingState state = STATES.get(body.getUUID());
        return state != null && state.entityId() == body.getId() && state.fusion();
    }

    public static int controller(Mob body) {
        RidingState state = STATES.get(body.getUUID());
        return state == null || state.entityId() != body.getId() ? -1 : state.controller();
    }
}
