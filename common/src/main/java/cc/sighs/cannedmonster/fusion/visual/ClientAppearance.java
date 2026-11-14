package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.*;

public final class ClientAppearance {
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static ClientLevel level;
    private static long clock;

    private record Member(UUID id, Mob entity) {}
    private record View(int entityId, List<Member> members, int control, long received) {}

    private ClientAppearance() {}

    public static void receive(FusionAppearance packet) {
        checkLevel();
        if (level == null) return;
        if (packet.members().isEmpty()) {
            VIEWS.remove(packet.bodyId());
            return;
        }
        View old = VIEWS.get(packet.bodyId());
        Map<UUID, Mob> previous = new HashMap<>();
        if (old != null) old.members.forEach(member -> previous.put(member.id, member.entity));
        List<Member> members = new ArrayList<>();
        for (FusionAppearance.Member member : packet.members()) {
            Mob entity = previous.get(member.id());
            if (entity == null || !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(member.type())) {
                if (!(BuiltInRegistries.ENTITY_TYPE.get(member.type()).create(level) instanceof Mob created)) continue;
                entity = created;
                entity.setUUID(member.id());
            }
            entity.getEntityData().assignValues(member.data());
            members.add(new Member(member.id(), entity));
        }
        VIEWS.put(packet.bodyId(), new View(packet.entityId(), List.copyOf(members),
                packet.control() >= 0 && packet.control() < members.size() ? packet.control() : -1, clock));
    }

    public static int controlIndex(Mob body) {
        checkLevel();
        View view = VIEWS.get(body.getUUID());
        return view == null || view.entityId != body.getId() ? -1 : view.control();
    }

    public static void tick() {
        checkLevel();
        clock++;
        if (level == null) return;
        VIEWS.entrySet().removeIf(entry -> {
            View view = entry.getValue();
            var body = level.getEntity(view.entityId);
            return body == null ? clock - view.received > 100 : body.isRemoved() || !body.getUUID().equals(entry.getKey());
        });
    }

    private static void checkLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) {
            VIEWS.clear();
            level = current;
        }
    }

    public static List<Mob> members(LivingEntity body) {
        checkLevel();
        View view = VIEWS.get(body.getUUID());
        if (view == null || view.entityId != body.getId()) return List.of();
        return view.members.stream().map(Member::entity).toList();
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
        member.setPose(body.getPose());
        member.setInvisible(body.isInvisible());
        member.setGlowingTag(body.isCurrentlyGlowing());
        for (EquipmentSlot slot : EquipmentSlot.values()) member.setItemSlot(slot, body.getItemBySlot(slot).copy());
    }
}
