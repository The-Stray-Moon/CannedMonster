package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import cc.sighs.cannedmonster.fusion.FlightRules;
import cc.sighs.cannedmonster.fusion.FusionMotion;
import cc.sighs.cannedmonster.fusion.FusionRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
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
    private static final class Composition {
        final List<Mob> members;
        final List<UUID> ids;
        FusionAppearance sent;
        boolean full = true;

        Composition(List<Mob> members, List<UUID> ids) { this.members = members; this.ids = ids; }
    }
    private static long revision;

    private AppearanceSync() {}

    public static boolean broadcast(net.minecraft.world.entity.Entity source, byte event) {
        Object linked = BodyLinks.body(source);
        if (linked == source || !(linked instanceof LivingEntity body) || source.level().isClientSide()) return false;
        int index = FusionRuntime.facets(body).indexOf(source);
        if (index < 0) return true;
        FusionAppearance state = snapshot(body);
        MemberEvent packet = new MemberEvent(state, state.members().get(index).id(), state.revision(), event);
        PacketDistributor.sendToPlayersTrackingEntity(body, packet);
        if (body instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, packet);
        Composition composition = COMPOSITIONS.get(body);
        if (composition != null) composition.sent = state;
        return true;
    }

    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity body) || body.level().isClientSide() || body.tickCount % 2 != 0) return;
        if (FusionRuntime.facets(body).isEmpty() && !COMPOSITIONS.containsKey(body)) return;
        FusionAppearance state = snapshot(body);
        Composition composition = COMPOSITIONS.get(body);
        FusionAppearance packet = state;
        if (composition != null) {
            if (!composition.full && composition.sent != null && body.tickCount % 20 != 0) {
                packet = delta(composition.sent, state);
                if (packet == null) return;
            }
            composition.sent = state;
            composition.full = false;
        }
        PacketDistributor.sendToPlayersTrackingEntity(body, packet);
        if (body instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, packet);
    }

    static FusionAppearance delta(FusionAppearance previous, FusionAppearance current) {
        if (previous.members().size() != current.members().size()) return current;
        boolean changed = previous.control() != current.control() || previous.flying() != current.flying();
        List<FusionAppearance.Member> members = new ArrayList<>(current.members().size());
        for (int i = 0; i < current.members().size(); i++) {
            var member = current.members().get(i);
            var before = previous.members().get(i);
            if (!before.id().equals(member.id()) || !before.type().equals(member.type())) return current;
            var values = AppearanceDelta.changed(before.data(), member.data());
            changed |= !values.isEmpty() || !before.presentation().equals(member.presentation());
            members.add(new FusionAppearance.Member(member.id(), member.type(), values, member.presentation()));
        }
        return changed ? new FusionAppearance(current.entityId(), current.bodyId(), current.control(), current.flying(),
                current.revision(), false, List.copyOf(members)) : null;
    }

    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity body && event.getEntity() instanceof ServerPlayer player
                && !FusionRuntime.facets(body).isEmpty()) {
            PacketDistributor.sendToPlayer(player, snapshot(body));
            COMPOSITIONS.get(body).full = true;
        }
    }

    public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) COMPOSITIONS.remove(event.getEntity());
    }

    public static void stop(ServerStoppedEvent event) { COMPOSITIONS.clear(); }

    private static FusionAppearance snapshot(LivingEntity body) {
        List<Mob> facets = FusionRuntime.facets(body);
        if (facets.isEmpty()) {
            COMPOSITIONS.remove(body);
            return new FusionAppearance(body.getId(), body.getUUID(), -1, false, ++revision, true, List.of());
        }
        Composition composition = COMPOSITIONS.get(body);
        if (composition == null || composition.members != facets) {
            Map<Mob, UUID> previous = new IdentityHashMap<>();
            if (composition != null) {
                for (int i = 0; i < composition.members.size(); i++) previous.put(composition.members.get(i), composition.ids.get(i));
            }
            List<UUID> ids = new ArrayList<>(facets.size());
            for (Mob facet : facets) {
                UUID id = previous.get(facet);
                ids.add(id == null ? UUID.randomUUID() : id);
            }
            composition = new Composition(facets, List.copyOf(ids));
            COMPOSITIONS.put(body, composition);
        }
        List<FusionAppearance.Member> members = new ArrayList<>(facets.size());
        for (int i = 0; i < facets.size(); i++) {
            Mob facet = facets.get(i);
            var before = composition.sent != null ? composition.sent.members().get(i) : null;
            List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
            for (var item : ((AppearanceData) facet.getEntityData()).cannedmonster$items()) {
                var previous = before != null && values.size() < before.data().size() ? before.data().get(values.size()) : null;
                values.add(value(facet, item, previous));
            }
            CompoundTag presentation = facet instanceof PresentationState state ? state.cannedmonster$capturePresentation() : new CompoundTag();
            members.add(new FusionAppearance.Member(composition.ids.get(i), BuiltInRegistries.ENTITY_TYPE.getKey(facet.getType()),
                    List.copyOf(values), presentation));
        }
        int control = body instanceof Mob mob ? FusionMotion.controlIndex(mob) : -1;
        return new FusionAppearance(body.getId(), body.getUUID(), control, FlightRules.hasFlight(body), ++revision, true, List.copyOf(members));
    }

    private static <T> SynchedEntityData.DataValue<?> value(Mob facet, SynchedEntityData.DataItem<T> item,
                                                           SynchedEntityData.DataValue<?> previous) {
        T current = facet.getEntityData().get(item.getAccessor());
        if (previous != null && previous.id() == item.getAccessor().id() && previous.serializer() == item.getAccessor().serializer()
                && Objects.equals(previous.value(), current)) return previous;
        return SynchedEntityData.DataValue.create(item.getAccessor(), current);
    }
}
