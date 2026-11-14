package cc.sighs.cannedmonster.fusion;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import javax.annotation.Nullable;

/**
 * Entity-target pursuit for fusion bodies. A member asking its navigation for a path to a living
 * entity registers intent; when the body does not close the distance, the session drives the body
 * toward the target itself so bodies whose native travel ignores path input can still pursue.
 */
public final class FusionPursuit {
    static final class Pursuit {
        final PursuitTiming timing = new PursuitTiming();
        @Nullable
        Mob owner;
        @Nullable
        LivingEntity target;
    }

    private FusionPursuit() {}

    public static void note(PathNavigation navigation, Mob mob, Entity entity) {
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isRemoved()) {
            return;
        }
        FusionMotion motion = FusionMotion.of(mob);
        if (motion == null || !(BodyLinks.body(mob) instanceof Mob body) || !body.isAlive() || body.isRemoved()) {
            return;
        }
        if (body.level().isClientSide || body.level() != target.level() || body.getControllingPassenger() != null) {
            return;
        }
        Pursuit pursuit = motion.pursuit();
        boolean same = pursuit.target == target && pursuit.owner == caller(motion);
        pursuit.owner = caller(motion);
        pursuit.target = target;
        pursuit.timing.refresh(body.tickCount, body.distanceTo(target), same);
    }

    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob body) || body.level().isClientSide || BodyLinks.body(body) != body) {
            return;
        }
        FusionMotion motion = FusionMotion.of(body);
        if (motion == null) {
            return;
        }
        Pursuit pursuit = motion.pursuitIfPresent();
        if (pursuit == null) {
            return;
        }
        LivingEntity target = pursuit.target;
        if (target == null || !target.isAlive() || target.isRemoved() || target.level() != body.level()
                || body.getControllingPassenger() != null) {
            motion.clearPursuit();
            return;
        }
        if (!pursuit.timing.update(body.tickCount, body.distanceTo(target))) {
            motion.clearPursuit();
        }
    }

    /**
     * @return true when this call replaced the native travel
     */
    public static boolean travel(LivingEntity entity, Vec3 input) {
        if (!(entity instanceof Mob body) || BodyLinks.body(body) != body) {
            return false;
        }
        FusionMotion motion = FusionMotion.of(body);
        if (motion == null) {
            return false;
        }
        Pursuit pursuit = motion.pursuitIfPresent();
        if (pursuit == null || !pursuit.timing.direct()) {
            return false;
        }
        LivingEntity target = pursuit.target;
        if (target == null || !target.isAlive() || target.level() != body.level()) {
            return false;
        }
        if (body.distanceTo(target) < 1.0) {
            return false;
        }
        steer(body, target);
        return true;
    }

    public static boolean direct(Mob body) {
        FusionMotion motion = FusionMotion.of(body);
        if (motion == null) {
            return false;
        }
        Pursuit pursuit = motion.pursuitIfPresent();
        return pursuit != null && pursuit.timing.direct();
    }

    public static String describe(Mob body) {
        FusionMotion motion = FusionMotion.of(body);
        if (motion == null) {
            return "none";
        }
        Pursuit pursuit = motion.pursuitIfPresent();
        if (pursuit == null) {
            return "none";
        }
        return "owner=" + (pursuit.owner == null ? "-" : pursuit.owner.getType())
                + " target=" + (pursuit.target == null ? "-" : pursuit.target.getType())
                + " calls=" + pursuit.timing.calls()
                + " direct=" + pursuit.timing.direct()
                + " window=" + pursuit.timing.window()
                + " windowDistance=" + pursuit.timing.windowDistance();
    }

    private static Mob caller(FusionMotion motion) {
        Object current = OrderedCalls.currentMember();
        return current instanceof Mob mob && motion.owns(mob) ? mob : motion.body();
    }

    private static void steer(Mob body, LivingEntity target) {
        Vec3 delta = target.getEyePosition().subtract(body.getEyePosition());
        double distance = delta.length();
        if (distance < 1.0E-4) {
            return;
        }
        double speed = body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (!(speed > 0.0)) {
            speed = 0.3;
        }
        speed = Mth.clamp(speed * 0.15, 0.03, 0.25);
        Vec3 direction = delta.scale(1.0 / distance);
        Vec3 velocity = new Vec3(direction.x * speed, direction.y * speed * 0.6, direction.z * speed);
        body.setDeltaMovement(velocity);
        body.hasImpulse = true;
        body.move(MoverType.SELF, velocity);
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0F;
        body.setYRot(yaw);
        body.yBodyRot = yaw;
        body.yHeadRot = yaw;
    }
}
