package cc.sighs.cannedmonster.fusion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lets a player-bound member's own teleport code move the body. Position writes stay blocked
 * outside these native teleport entry points; a frame validates the final body box once and
 * commits the move through the player connection.
 */
public final class TeleportCalls {
    private static final int COOLDOWN = 20;
    private static final Map<Player, Integer> LAST = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Player> RESTORING = new ThreadLocal<>();

    public enum Status { NONE, ACTIVE, DENIED }

    private TeleportCalls() {
    }

    public static Status begin(Entity source) {
        if (!(BodyLinks.body(source) instanceof Player player) || BodyLinks.body(source) == source) {
            return Status.NONE;
        }
        Deque<Frame> frames = FRAMES.get();
        Frame top = frames.peek();
        if (top != null && top.tick != player.tickCount) {
            try {
                restore(top);
            } finally {
                frames.clear();
            }
            top = null;
        }
        if (top != null) {
            if (top.source == source) {
                top.depth++;
                return Status.ACTIVE;
            }
            return Status.NONE;
        }
        if (cc.sighs.cannedmonster.platform.EntityAccess.clientSide(player.level()) || !player.isAlive() || player.isRemoved()
                || player.isPassenger() || player.isVehicle()) {
            return Status.DENIED;
        }
        Integer last = LAST.get(player);
        if (last != null && player.tickCount - last < COOLDOWN) return Status.DENIED;
        frames.push(new Frame(source, player, player.position(), player.getYRot(), player.getXRot(), player.tickCount));
        return Status.ACTIVE;
    }

    public static void end(Entity source) {
        Deque<Frame> frames = FRAMES.get();
        Frame top = frames.peek();
        if (top == null || top.source != source) return;
        if (top.tick != top.player.tickCount) {
            try {
                restore(top);
            } finally {
                frames.clear();
            }
            return;
        }
        if (--top.depth > 0) return;
        try {
            commit(top);
        } finally {
            frames.pop();
            if (frames.isEmpty()) FRAMES.remove();
        }
    }

    public static boolean allows(Entity entity) {
        Frame top = FRAMES.get().peek();
        if (top != null && (top.source == entity || top.player == entity)
                && top.tick == top.player.tickCount) {
            return true;
        }
        return RESTORING.get() == entity;
    }

    public static void clear() {
        LAST.clear();
        FRAMES.remove();
        RESTORING.remove();
    }

    private static void commit(Frame frame) {
        Player player = frame.player;
        player.refreshDimensions();
        Vec3 destination = player.position();
        if (destination.distanceToSqr(frame.position) <= 1.0E-8) return;
        LAST.put(player, player.tickCount);
        if (!valid(player)) {
            restore(frame);
            return;
        }
        if (player instanceof ServerPlayer server && server.connection != null) {
            server.teleportTo(destination.x, destination.y, destination.z);
        }
        player.resetFallDistance();
    }

    private static boolean valid(Player player) {
        if (player.isPassenger() || player.isVehicle()) return false;
        return player.level().hasChunkAt(player.blockPosition())
                && player.level().getWorldBorder().isWithinBounds(player.getBoundingBox())
                && player.level().noCollision(player);
    }

    private static void restore(Frame frame) {
        Player player = frame.player;
        Player previous = RESTORING.get();
        RESTORING.set(player);
        try {
            player.setPos(frame.position.x, frame.position.y, frame.position.z);
            player.setYRot(frame.yRot);
            player.setXRot(frame.xRot);
            player.setOldPosAndRot();
            player.refreshDimensions();
        } finally {
            if (previous == null) RESTORING.remove();
            else RESTORING.set(previous);
        }
    }

    private static final class Frame {
        final Entity source;
        final Player player;
        final Vec3 position;
        final float yRot;
        final float xRot;
        final int tick;
        int depth = 1;

        Frame(Entity source, Player player, Vec3 position, float yRot, float xRot, int tick) {
            this.source = source;
            this.player = player;
            this.position = position;
            this.yRot = yRot;
            this.xRot = xRot;
            this.tick = tick;
        }
    }
}
