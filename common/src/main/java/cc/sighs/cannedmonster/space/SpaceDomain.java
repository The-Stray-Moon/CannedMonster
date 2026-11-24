package cc.sighs.cannedmonster.space;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** Thread-local context for an entity executing inside one virtual room. */
public final class SpaceDomain {
    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> RAW = ThreadLocal.withInitial(() -> 0);
    private static final Frame NONE = new Frame(null, null, null, null);

    private SpaceDomain() {}

    public record Frame(Level level, RoomGeometry room, UUID space, Entity owner) {
        public boolean active() { return level != null && room != null; }
    }

    public static void push(Entity entity) {
        SpaceManager.Space space = entity == null ? null : SpaceManager.spaceOf(entity);
        STACK.get().push(space == null ? NONE : new Frame(space.level(), space.internal(), space.id(), entity));
    }

    /**
     * Stop treating the given entity as inside its room for the rest of this tick.
     * Only frames pushed by that entity are invalidated: a resident that kills the
     * player during the same tick must keep its own room isolation.
     */
    public static void invalidate(Entity owner) {
        if (owner == null) return;
        Deque<Frame> stack = STACK.get();
        if (stack.isEmpty()) return;
        boolean changed = false;
        for (Frame frame : stack) {
            if (frame.active() && frame.owner() == owner) changed = true;
        }
        if (!changed) return;
        Deque<Frame> replaced = new ArrayDeque<>();
        for (Frame frame : stack) replaced.add(frame.active() && frame.owner() == owner ? NONE : frame);
        STACK.set(replaced);
    }

    public static void pop() {
        Deque<Frame> stack = STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }

    public static void reset() {
        STACK.remove();
        RAW.remove();
    }

    /** Runtime management still needs the live resident entity behind the external filter. */
    public static <T> T raw(java.util.function.Supplier<T> action) {
        pushRaw();
        try {
            return action.get();
        } finally {
            popRaw();
        }
    }

    public static void raw(Runnable action) {
        pushRaw();
        try {
            action.run();
        } finally {
            popRaw();
        }
    }

    private static void pushRaw() {
        RAW.set(RAW.get() + 1);
    }

    private static void popRaw() {
        int depth = RAW.get() - 1;
        if (depth <= 0) RAW.remove();
        else RAW.set(depth);
    }

    public static boolean rawLookup() {
        return RAW.get() > 0;
    }

    public static Frame frame() {
        Deque<Frame> stack = STACK.get();
        return stack.isEmpty() ? NONE : stack.peek();
    }

    public static boolean active(Level level) {
        Frame frame = frame();
        return frame.active() && frame.level() == level;
    }

    public static BlockState blockState(Level level, net.minecraft.core.BlockPos pos) {
        Frame frame = frame();
        if (!frame.active() || frame.level() != level) return null;
        return frame.room.blockAt(pos);
    }

    public static FluidState fluidState(Level level, net.minecraft.core.BlockPos pos) {
        Frame frame = frame();
        if (!frame.active() || frame.level() != level) return null;
        return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
    }

    public static boolean allowed(Entity entity) {
        Frame frame = frame();
        if (!frame.active()) return true;
        if (entity == null || entity.level() != frame.level) return false;
        return frame.room.contains(entity.position());
    }

    /** A resident executing inside its enclosed room has no sky light to read. */
    public static boolean isolated(Entity entity) {
        Frame frame = frame();
        return frame.active() && entity != null && frame.owner() == entity;
    }

    /** Room interiors are roofed: no sky exposure and no precipitation. */
    public static boolean roofed(Level level, net.minecraft.core.BlockPos pos) {
        Frame frame = frame();
        if (frame.active() && frame.level() == level && frame.room().contains(pos)) return true;
        return level instanceof net.minecraft.server.level.ServerLevel server
                && !SpaceManager.spacesAt(server, net.minecraft.world.phys.Vec3.atCenterOf(pos)).isEmpty();
    }

    public static boolean sameSpace(Entity entity) {
        return sameSpace(frame(), entity);
    }

    public static boolean sameSpace(Frame frame, Entity entity) {
        if (!frame.active()) return true;
        if (entity == null || entity.level() != frame.level) return false;
        SpaceManager.Space space = SpaceManager.spaceOf(entity);
        return space != null && space.id().equals(frame.space);
    }
}
