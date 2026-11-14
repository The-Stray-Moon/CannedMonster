package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RemovalCalls {
    private static final ThreadLocal<Deque<Frame>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

    private RemovalCalls() {
    }

    public static boolean active(Entity entity) {
        Object host = BodyLinks.body(entity);
        return ACTIVE.get().stream().anyMatch(frame -> frame.host == host);
    }

    public static boolean dispatch(Entity entity, Entity.RemovalReason reason, boolean boundary) {
        if (FusionTraits.playerMember(entity) || FusionTraits.autonomous(entity)) return true;
        if (!(BodyLinks.body(entity) instanceof Mob host)) return false;
        Deque<Frame> stack = ACTIVE.get();
        Frame frame = stack.peek();
        if (frame != null && frame.host == host) {
            if (frame.current != entity) return true;
            return boundary && entity != host;
        }
        List<Mob> facets = FusionRuntime.facets(host);
        if (facets.isEmpty() || host.level().isClientSide) return false;
        List<Mob> members = new ArrayList<>(facets);
        members.add(host);
        frame = new Frame(host, List.copyOf(members), reason);
        stack.push(frame);
        try {
            for (Mob member : frame.members) {
                frame.current = member;
                member.remove(reason);
            }
        } finally {
            stack.pop();
            if (stack.isEmpty()) ACTIVE.remove();
            if (host.isRemoved()) FusionRuntime.detach(host);
        }
        return true;
    }

    public static Entity create(EntityType<?> type, Level level, Entity source) {
        Entity child = type.create(level);
        Frame frame = ACTIVE.get().peek();
        if (frame != null && frame.reason == Entity.RemovalReason.KILLED && frame.current == source
                && source.getType() == type && source.level() == level) {
            frame.origins.record(child, source);
        }
        return child;
    }

    public static Entity inherit(Entity entity) {
        Frame frame = ACTIVE.get().peek();
        if (frame == null || frame.reason != Entity.RemovalReason.KILLED || !(entity instanceof Mob child)
                || frame.current == null || child.getType() != frame.current.getType()
                || child.level() != frame.host.level() || child.isAddedToLevel() || child.isRemoved()
                || BodyLinks.isFacet(child) || !FusionRuntime.facets(child).isEmpty()
                || !frame.origins.claim(child, frame.current)) return entity;
        List<Mob> members = new ArrayList<>();
        for (Mob source : frame.members) {
            if (source == frame.current) {
                members.add(child);
            } else {
                Entity created = source.getType().create(child.level());
                if (!(created instanceof Mob copy)) throw new IllegalStateException("Cannot recreate fusion member " + source.getType());
                members.add(copy);
            }
        }
        Mob host = members.getLast();
        host.moveTo(child.getX(), child.getY(), child.getZ(), child.getYRot(), child.getXRot());
        host.setDeltaMovement(child.getDeltaMovement());
        FusionRuntime.attach(host, members.subList(0, members.size() - 1));
        return host;
    }

    private static final class Frame {
        final Mob host;
        final List<Mob> members;
        final Entity.RemovalReason reason;
        final CreationTickets origins = new CreationTickets();
        Mob current;

        Frame(Mob host, List<Mob> members, Entity.RemovalReason reason) {
            this.host = host;
            this.members = members;
            this.reason = reason;
        }
    }
}
