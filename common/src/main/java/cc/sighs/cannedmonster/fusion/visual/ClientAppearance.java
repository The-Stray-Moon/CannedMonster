package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.*;

public final class ClientAppearance {
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static final LinkedHashMap<UUID, Long> TOMBSTONES = new LinkedHashMap<>();
    private static final EquipmentSlot[] EQUIPMENT = EquipmentSlot.values();
    private static final Deque<MemberEvent> PENDING = new ArrayDeque<>();
    private static ClientLevel level;
    private static long clock;
    private static LocalPlayer localPlayer;
    private static boolean localFlightApplied;

    private static final class Member {
        final UUID id;
        final Mob entity;
        final Map<Integer, net.minecraft.network.syncher.SynchedEntityData.DataItem<?>> dataById;
        long eventSequence = -1;

        Member(UUID id, Mob entity) {
            this.id = id;
            this.entity = entity;
            this.dataById = new HashMap<>();
            AppearanceData data = (AppearanceData) entity.getEntityData();
            for (var item : data.cannedmonster$items()) dataById.put(data.cannedmonster$id(item.getAccessor()), item);
        }
    }
    private record View(int entityId, List<Member> members, List<Mob> entities, int control, boolean flying, long received) {
        View(int entityId, List<Member> members, int control, boolean flying, long received) {
            this(entityId, members, members.stream().map(member -> member.entity).toList(), control, flying, received);
        }
    }
    private record Preview(List<Member> members, List<Mob> entities) {
        Preview(List<Mob> entities) {
            this(entities.stream().map(member -> new Member(UUID.randomUUID(), member)).toList(),
                    List.copyOf(entities));
        }
    }
    private static final Map<LivingEntity, Preview> PREVIEWS = new IdentityHashMap<>();

    private ClientAppearance() {}

    public static void receive(FusionAppearance packet) {
        checkLevel();
        if (level == null || packet.revision() <= REVISIONS.getOrDefault(packet.bodyId(), -1L)) return;
        View old = VIEWS.get(packet.bodyId());
        if (!packet.full() && (old == null || old.entityId != packet.entityId()
                || packet.members().size() != old.members.size())) return;
        if (!packet.full()) {
            for (int i = 0; i < old.members.size(); i++) {
                if (!old.members.get(i).id.equals(packet.members().get(i).id())) return;
            }
        }
        REVISIONS.put(packet.bodyId(), packet.revision());
        TOMBSTONES.remove(packet.bodyId());
        if (packet.full() || packet.layout() != null) VisualViews.accept(packet.bodyId(), packet.layout());
        if (packet.members().isEmpty()) {
            if (old != null) dispose(old);
            VIEWS.remove(packet.bodyId());
            retire(packet.bodyId());
            return;
        }
        Map<UUID, Member> previous = new HashMap<>();
        if (old != null) old.members.forEach(member -> previous.put(member.id, member));
        List<Member> members = new ArrayList<>();
        for (FusionAppearance.Member state : packet.members()) {
            Member member = previous.remove(state.id());
            if (member != null && !BuiltInRegistries.ENTITY_TYPE.getKey(member.entity.getType()).equals(state.type())) {
                ClientMemberEvents.dispose(member.entity);
                member = null;
            }
            boolean fresh = member == null;
            if (fresh) {
                if (!(cc.sighs.cannedmonster.platform.EntityStorage.create(state.type().toString(), level) instanceof Mob created)) continue;
                created.setUUID(state.id());
                DisplayExecution.register(created);
                member = new Member(state.id(), created);
            }
            apply(member, state.data(), fresh);
            if (member.entity instanceof PresentationState presentation) presentation.cannedmonster$applyPresentation(state.presentation(), fresh);
            members.add(member);
        }
        previous.values().forEach(member -> ClientMemberEvents.dispose(member.entity));
        VIEWS.put(packet.bodyId(), new View(packet.entityId(), List.copyOf(members),
                packet.control() >= 0 && packet.control() < members.size() ? packet.control() : -1,
                packet.flying(), clock));
    }

    private static void apply(Member member, List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values, boolean fresh) {
        Mob entity = member.entity;
        var items = member.dataById;
        AppearanceData data = (AppearanceData) entity.getEntityData();
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> changed = new ArrayList<>();
        for (var value : values) {
            var item = items.get(value.id());
            if (item == null) throw new IllegalArgumentException("Unknown display data accessor " + value.id());
            if (!Objects.equals(data.cannedmonster$serializer(item.getAccessor()), value.serializer())) {
                throw new IllegalArgumentException("Mismatched display data serializer " + value.id());
            }
            if (fresh || !Objects.equals(item.getValue(), value.value())) changed.add(value);
        }
        if (!changed.isEmpty()) DisplayExecution.run(entity, () -> entity.getEntityData().assignValues(changed));
    }

    public static void receive(MemberEvent packet) {
        receive(packet.appearance());
        if (!dispatch(packet)) {
            if (PENDING.size() >= 256) PENDING.removeFirst();
            PENDING.addLast(packet);
        }
    }

    private static boolean dispatch(MemberEvent packet) {
        View view = VIEWS.get(packet.appearance().bodyId());
        if (view == null || view.entityId != packet.appearance().entityId()) return true;
        var body = level.getEntity(view.entityId);
        if (body == null) return false;
        if (!(body instanceof LivingEntity living) || !body.getUUID().equals(packet.appearance().bodyId())) return true;
        for (Member member : view.members) {
            if (!member.id.equals(packet.member())) continue;
            if (packet.sequence() <= member.eventSequence) return true;
            member.eventSequence = packet.sequence();
            pose(member.entity, living);
            if (member.entity instanceof PresentationState state && state.cannedmonster$continuousEvent() == Byte.toUnsignedInt(packet.event())) {
                ClientMemberEvents.continuous(member.entity, Byte.toUnsignedInt(packet.event()));
            } else ClientMemberEvents.dispatch(member.entity, packet.event());
            return true;
        }
        return true;
    }

    public static int controlIndex(Mob body) {
        checkLevel();
        View view = VIEWS.get(body.getUUID());
        return view == null || view.entityId != body.getId() ? -1 : view.control();
    }

    public static boolean flying(LivingEntity body) {
        checkLevel();
        View view = VIEWS.get(body.getUUID());
        return view != null && view.entityId == body.getId() && view.flying;
    }

    public static void tick() {
        checkLevel();
        clock++;
        if (level == null) return;
        VIEWS.entrySet().removeIf(entry -> {
            View view = entry.getValue();
            var body = level.getEntity(view.entityId);
            boolean expired = body == null ? clock - view.received > 100 : body.isRemoved() || !body.getUUID().equals(entry.getKey());
            if (expired) { dispose(view); retire(entry.getKey()); VisualViews.forget(entry.getKey()); }
            return expired;
        });
        TOMBSTONES.entrySet().removeIf(entry -> {
            if (clock - entry.getValue() <= 200) return false;
            REVISIONS.remove(entry.getKey());
            return true;
        });
        PENDING.removeIf(ClientAppearance::dispatch);
        for (var entry : VIEWS.entrySet()) {
            View view = entry.getValue();
            if (!(level.getEntity(view.entityId) instanceof LivingEntity body) || !body.getUUID().equals(entry.getKey())) continue;
            for (Member member : view.members) {
                pose(member.entity, body);
                DisplayExecution.run(member.entity, () -> {
                    member.entity.tick();
                    member.entity.aiStep();
                });
                pose(member.entity, body);
                if (member.entity instanceof PresentationState state) ClientMemberEvents.continuous(member.entity, state.cannedmonster$continuousEvent());
            }
        }
        ClientMemberEvents.prune();
        applyLocalGravity();
    }

    private static void retire(UUID id) {
        TOMBSTONES.remove(id);
        TOMBSTONES.put(id, clock);
        while (TOMBSTONES.size() > 4096) {
            UUID oldest = TOMBSTONES.keySet().iterator().next();
            TOMBSTONES.remove(oldest);
            REVISIONS.remove(oldest);
        }
    }

    private static void dispose(View view) {
        view.members.forEach(member -> ClientMemberEvents.dispose(member.entity));
    }

    private static void dispose(Preview preview) {
        preview.members.forEach(member -> ClientMemberEvents.dispose(member.entity));
    }

    private static void applyLocalGravity() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            localFlightApplied = false;
            localPlayer = null;
            return;
        }
        if (player != localPlayer) {
            if (localPlayer != null && localFlightApplied) localPlayer.setNoGravity(false);
            localPlayer = player;
            localFlightApplied = false;
        }
        if (flying(player)) {
            localFlightApplied = true;
            player.setNoGravity(true);
        } else if (localFlightApplied) {
            player.setNoGravity(false);
            localFlightApplied = false;
        }
    }

    private static void checkLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) {
            if (localPlayer != null && localFlightApplied) localPlayer.setNoGravity(false);
            localPlayer = null;
            localFlightApplied = false;
            VIEWS.values().forEach(ClientAppearance::dispose);
            VIEWS.clear();
            PREVIEWS.values().forEach(ClientAppearance::dispose);
            PREVIEWS.clear();
            REVISIONS.clear();
            TOMBSTONES.clear();
            PENDING.clear();
            VisualViews.clearClient();
            level = current;
        }
    }

    public static List<LivingEntity> bodies() {
        checkLevel();
        if (level == null) return List.of();
        List<LivingEntity> bodies = new ArrayList<>();
        VIEWS.forEach((id, view) -> {
            if (level.getEntity(view.entityId) instanceof LivingEntity body && body.getUUID().equals(id)) bodies.add(body);
        });
        return bodies;
    }

    public static LivingEntity bodyOf(Mob member) {
        if (level == null) return null;
        for (View view : VIEWS.values()) {
            if (view.members.stream().anyMatch(value -> value.entity == member)
                    && level.getEntity(view.entityId) instanceof LivingEntity body) return body;
        }
        return null;
    }

    public static List<Mob> members(LivingEntity body) {
        checkLevel();
        Preview preview = PREVIEWS.get(body);
        if (preview != null) return preview.entities();
        View view = VIEWS.get(body.getUUID());
        if (view == null || view.entityId != body.getId()) return List.of();
        return view.entities;
    }

    /**
     * Hydrates members of a detached preview body. These entities never join the level and are
     * only posed and rendered through the same slice path as tracked fusion members.
     */
    public static void preview(LivingEntity body, List<Mob> members) {
        clearPreview(body);
        if (members.isEmpty()) return;
        PREVIEWS.put(body, new Preview(members));
    }

    public static boolean preview(LivingEntity body) {
        return PREVIEWS.containsKey(body);
    }

    public static void clearPreview(LivingEntity body) {
        Preview preview = PREVIEWS.remove(body);
        if (preview != null) dispose(preview);
    }

    public static void pose(Mob member, LivingEntity body) {
        member.setPos(body.position());
        member.xo = body.xo;
        member.yo = body.yo;
        member.zo = body.zo;
        member.xOld = body.xOld;
        member.yOld = body.yOld;
        member.zOld = body.zOld;
        member.setYRot(body.getYRot());
        member.setXRot(body.getXRot());
        member.yRotO = body.yRotO;
        member.xRotO = body.xRotO;
        member.yBodyRot = body.yBodyRot;
        member.yBodyRotO = body.yBodyRotO;
        member.yHeadRot = body.yHeadRot;
        member.yHeadRotO = body.yHeadRotO;
        member.tickCount = body.tickCount;
        member.hurtTime = body.hurtTime;
        member.deathTime = body.deathTime;
        member.attackAnim = body.attackAnim;
        member.oAttackAnim = body.oAttackAnim;
        ((AppearanceWalk) member.walkAnimation).cannedmonster$copy(body.walkAnimation);
        member.setDeltaMovement(body.getDeltaMovement());
        member.setOnGround(body.onGround());
        for (EquipmentSlot slot : EQUIPMENT) {
            var equipment = body.getItemBySlot(slot);
            if (!net.minecraft.world.item.ItemStack.matches(member.getItemBySlot(slot), equipment)) {
                member.setItemSlot(slot, equipment.copy());
            }
        }
    }
}
