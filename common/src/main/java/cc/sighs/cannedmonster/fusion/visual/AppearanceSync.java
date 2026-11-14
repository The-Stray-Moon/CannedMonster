package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.fusion.FusionMotion;
import cc.sighs.cannedmonster.fusion.FusionRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public final class AppearanceSync {
    private static final Map<LivingEntity, Composition> COMPOSITIONS = new IdentityHashMap<>();
    private record Composition(List<Mob> members, List<UUID> ids) {}

    private AppearanceSync() {}

    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity body) || body.level().isClientSide || body.tickCount % 2 != 0) return;
        if (FusionRuntime.facets(body).isEmpty() && !COMPOSITIONS.containsKey(body)) return;
        boolean hadComposition = COMPOSITIONS.containsKey(body);
        FusionAppearance appearance = snapshot(body);
        PacketDistributor.sendToPlayersTrackingEntity(body, appearance);
        if (body instanceof ServerPlayer player && (hadComposition || !appearance.members().isEmpty())) {
            PacketDistributor.sendToPlayer(player, appearance);
        }
    }

    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity body && event.getEntity() instanceof ServerPlayer player
                && !FusionRuntime.facets(body).isEmpty()) {
            PacketDistributor.sendToPlayer(player, snapshot(body));
        }
    }

    public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide) COMPOSITIONS.remove(event.getEntity());
    }

    public static void stop(ServerStoppedEvent event) {
        COMPOSITIONS.clear();
    }

    private static FusionAppearance snapshot(LivingEntity body) {
        List<Mob> facets = FusionRuntime.facets(body);
        if (facets.isEmpty()) {
            COMPOSITIONS.remove(body);
            return new FusionAppearance(body.getId(), body.getUUID(), -1, List.of());
        }
        Composition composition = COMPOSITIONS.get(body);
        if (composition == null || !composition.members.equals(facets)) {
            composition = new Composition(List.copyOf(facets), facets.stream().map(ignored -> UUID.randomUUID()).toList());
            COMPOSITIONS.put(body, composition);
        }
        List<FusionAppearance.Member> members = new ArrayList<>();
        for (int i = 0; i < facets.size(); i++) {
            Mob facet = facets.get(i);
            List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values = new ArrayList<>();
            for (var item : ((AppearanceData) facet.getEntityData()).cannedmonster$items()) values.add(value(facet, item));
            members.add(new FusionAppearance.Member(composition.ids.get(i), BuiltInRegistries.ENTITY_TYPE.getKey(facet.getType()), List.copyOf(values)));
        }
        int control = body instanceof Mob mob ? FusionMotion.controlIndex(mob) : -1;
        return new FusionAppearance(body.getId(), body.getUUID(), control, List.copyOf(members));
    }

    private static <T> net.minecraft.network.syncher.SynchedEntityData.DataValue<T> value(
            Mob facet, net.minecraft.network.syncher.SynchedEntityData.DataItem<T> item) {
        return net.minecraft.network.syncher.SynchedEntityData.DataValue.create(item.getAccessor(), facet.getEntityData().get(item.getAccessor()));
    }
}
