package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FusionRiding {
    private static final ThreadLocal<Boolean> QUERY = ThreadLocal.withInitial(() -> false);
    private static final ExecutionSlot<Frame> EXECUTION = new ExecutionSlot<>();

    private record Frame(Mob body, Mob source, Player rider, FusionMotion session) {
        boolean valid() {
            return FusionMotion.of(body) == session && body.isAlive() && !body.isRemoved()
                    && body.getFirstPassenger() == rider && source.isAlive() && !source.isRemoved();
        }
    }

    private FusionRiding() {}

    public static boolean querying() {
        return QUERY.get();
    }

    public static Mob source(Mob body) {
        if (!FusionMotion.isFusionBody(body) || !(body.getFirstPassenger() instanceof Player rider)
                || !body.isAlive() || body.isRemoved()) return null;
        boolean previous = QUERY.get();
        QUERY.set(true);
        try {
            List<Mob> candidates = new ArrayList<>(FusionMotion.members(body));
            candidates.add(body);
            for (Mob candidate : candidates) {
                if (candidate.isAlive() && !candidate.isRemoved() && candidate.getControllingPassenger() == rider) return candidate;
            }
            return null;
        } finally {
            if (previous) QUERY.set(true); else QUERY.remove();
        }
    }

    public static LivingEntity controller(Entity entity, LivingEntity original) {
        if (querying() || !(entity instanceof Mob body) || !FusionMotion.isFusionBody(body)) return original;
        return source(body) == null ? original : (LivingEntity) body.getFirstPassenger();
    }

    public static boolean authoritative(Entity entity) {
        return BodyLinks.body(entity) instanceof Mob body && (FusionMotion.isFusionBody(body)
                || body.level().isClientSide && FusionMotion.clientFusion(body));
    }

    public static boolean travel(LivingEntity entity, Player rider, Vec3 originalInput) {
        if (!(entity instanceof Mob body) || !authoritative(body)) return false;
        if (body.level().isClientSide) return true;
        Mob source = source(body);
        if (source == null || body.getFirstPassenger() != rider) return true;
        Frame frame = new Frame(body, source, rider, FusionMotion.of(body));
        return EXECUTION.run(frame, () -> {
            NativeRideAccess access = (NativeRideAccess) source;
            Vec3 input = access.cannedmonster$riddenInput(rider, originalInput);
            if (!frame.valid()) return true;
            access.cannedmonster$tickRidden(rider, input);
            if (!frame.valid()) return true;
            body.setYRot(source.getYRot());
            body.setXRot(source.getXRot());
            body.yBodyRot = source.yBodyRot;
            body.yHeadRot = source.yHeadRot;
            float speed = access.cannedmonster$riddenSpeed(rider);
            if (!frame.valid()) return true;
            source.setSpeed(speed);
            source.travel(input);
            return true;
        });
    }

    public static boolean blocked(Entity entity) {
        Frame frame = EXECUTION.get();
        return BodyLinks.isFacet(entity) && (frame == null || !frame.valid() || frame.source != entity);
    }

    public static boolean move(Entity entity, MoverType type, Vec3 delta) {
        if (entity.level().isClientSide && authoritative(entity)) return true;
        if (!BodyLinks.isFacet(entity)) return false;
        Frame frame = EXECUTION.get();
        if (frame != null && frame.valid() && frame.source == entity) {
            frame.body.move(type, delta);
        }
        return true;
    }

    public static Object velocityTarget(Object entity) {
        if (entity instanceof Entity value && FusionTraits.playerMember(value)) return entity;
        Frame frame = EXECUTION.get();
        if (frame != null && (frame.source == entity || frame.body == entity)) return frame.body;
        return BodyLinks.body(entity);
    }

    public static void autonomousVelocity(Entity entity, Vec3 velocity, Entity origin) {
        Frame frame = EXECUTION.get();
        if (frame == null && BodyLinks.body(entity) instanceof Mob body) {
            if (BodyLinks.body(entity) != entity) {
                return;
            }
            if (BodyLinks.same(entity, origin) && authoritative(body)
                    && (source(body) != null || FusionPursuit.direct(body))) return;
        }
        entity.setDeltaMovement(velocity);
    }

    public static void autonomousVelocity(Entity entity, double x, double y, double z, Entity origin) {
        autonomousVelocity(entity, new Vec3(x, y, z), origin);
    }

    public static Entity itemVehicle(Player player) {
        Entity vehicle = player.getControlledVehicle();
        if (vehicle instanceof Mob body) {
            Mob source = source(body);
            if (source != null) return source;
        }
        return vehicle;
    }
}
