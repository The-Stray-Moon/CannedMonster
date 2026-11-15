package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;

import java.util.*;

public final class ClientMemberEvents {
    private static final Map<Entity, Set<SoundInstance>> SOUNDS = new IdentityHashMap<>();
    private static Entity eventEntity;

    public static void continuous(Entity member, int event) {
        if (event < 0) {
            Set<SoundInstance> sounds = SOUNDS.remove(member);
            if (sounds != null) sounds.forEach(Minecraft.getInstance().getSoundManager()::stop);
        } else if (!SOUNDS.containsKey(member)) {
            dispatch(member, (byte) event);
        }
    }

    public static List<SoundInstance> sounds(Entity member) {
        return List.copyOf(SOUNDS.getOrDefault(member, Set.of()));
    }

    private ClientMemberEvents() {}

    public static Entity eventEntity(Entity fallback) { return eventEntity == null ? fallback : eventEntity; }

    public static void dispatch(Entity entity, byte event) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        Entity previous = eventEntity;
        eventEntity = entity;
        try {
            DisplayExecution.run(entity, () -> connection.handleEntityEvent(new ClientboundEntityEventPacket(entity, event)));
        } finally { eventEntity = previous; }
    }

    public static void sound(SoundInstance sound) {
        Entity owner = DisplayExecution.current();
        if (owner != null) SOUNDS.computeIfAbsent(owner, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(sound);
    }

    public static void dispose(Entity member) {
        Set<SoundInstance> sounds = SOUNDS.remove(member);
        if (sounds != null) sounds.forEach(Minecraft.getInstance().getSoundManager()::stop);
        member.setRemoved(Entity.RemovalReason.DISCARDED);
    }

    public static void prune() {
        SOUNDS.values().forEach(sounds -> sounds.removeIf(sound -> !Minecraft.getInstance().getSoundManager().isActive(sound)));
        SOUNDS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
