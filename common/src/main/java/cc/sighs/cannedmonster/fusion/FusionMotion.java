package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class FusionMotion {
    public interface ClientView {
        boolean fusion(Mob body);
        int controller(Mob body);
    }

    private static volatile ClientView clientView;
    final java.util.UUID generation = java.util.UUID.randomUUID();
    private final Mob body;
    private final List<Mob> members;
    private final Map<Mob, FusionNavigation> navigations = new IdentityHashMap<>();
    private final MotionClaims claims = new MotionClaims();
    private static final ExecutionSlot<PathNavigation> NAVIGATION_TICK = new ExecutionSlot<>();
    private static final ExecutionSlot<Mob> NAVIGATION_CALLER = new ExecutionSlot<>();
    private Mob holdingMember;
    @Nullable
    private FusionPursuit.Pursuit pursuit;

    FusionMotion(Mob body, List<Mob> members) {
        this.body = body;
        this.members = List.copyOf(members);
    }

    public static void setClientView(ClientView view) { clientView = view; }

    public static List<Mob> members(Mob body) {
        FusionMotion motion = of(body);
        return motion == null ? List.of() : motion.members;
    }

    @Nullable
    static FusionMotion of(Mob entity) {
        return BodyLinks.session(entity) instanceof FusionMotion motion ? motion : null;
    }

    public static boolean isFusionBody(Entity entity) {
        return entity instanceof Mob mob && BodyLinks.body(mob) == mob && of(mob) != null;
    }

    public static boolean clientFusion(Mob body) {
        ClientView view = clientView;
        return view != null && view.fusion(body);
    }

    public static LivingEntity controllingPassenger(LivingEntity original, Entity entity) {
        if (entity instanceof Mob body && body.level().isClientSide && clientFusion(body)) {
            int id = clientView.controller(body);
            for (Entity passenger : body.getPassengers()) {
                if (passenger.getId() == id && passenger instanceof LivingEntity living) return living;
            }
            return null;
        }
        return FusionRiding.controller(entity, original);
    }

    public static int controlIndex(Mob body) {
        Mob source = FusionRiding.source(body);
        return source == null ? -1 : members(body).indexOf(source);
    }

    public static PathNavigation navigation(PathNavigation original, Entity entity) {
        if (original instanceof FusionNavigation || !(entity instanceof Mob mob) || !BodyLinks.isFacet(mob)) return original;
        FusionMotion motion = of(mob);
        return motion == null ? original : motion.navigations.computeIfAbsent(mob, ignored -> new FusionNavigation(mob, original));
    }

    @Nullable
    public static Boolean guardMoveTo(PathNavigation navigation, Mob mob, @Nullable Path path, double speed) {
        FusionMotion motion = of(mob);
        if (BodyLinks.isFacet(mob) || motion == null) return null;
        return motion.claim(motion.caller()) ? null : Boolean.FALSE;
    }

    public static boolean guardStop(PathNavigation navigation, Mob mob) {
        FusionMotion motion = of(mob);
        if (BodyLinks.isFacet(mob) || motion == null) return false;
        Mob owner = motion.pathOwner();
        if (NAVIGATION_TICK.get() == navigation) {
            if (owner != null) motion.release(owner);
            return false;
        }
        Mob caller = motion.caller();
        boolean moving = movingGoal(caller);
        if (owner != null && owner != caller && (!moving || motion.priority(caller) >= motion.priority(owner))) return true;
        if (moving) {
            if (!motion.claim(caller)) return true;
            motion.holdingMember = caller;
        } else {
            motion.release(caller);
        }
        return false;
    }

    public static boolean denyPath(Mob mob) {
        FusionMotion motion = of(mob);
        return motion != null && !BodyLinks.isFacet(mob) && !motion.mayPath(motion.caller());
    }

    <T> T query(Mob member, java.util.function.Supplier<T> action) {
        return NAVIGATION_CALLER.run(member, action);
    }

    public static void navigationTick(PathNavigation navigation) {
        NAVIGATION_TICK.run(navigation, () -> {
            navigation.tick();
            return null;
        });
    }

    PathNavigation bodyNavigation() { return body.getNavigation(); }

    boolean moveTo(Mob member, Path path, double speed) {
        return NAVIGATION_CALLER.run(member, () -> bodyNavigation().moveTo(path, speed));
    }

    void stop(Mob member) {
        NAVIGATION_CALLER.run(member, () -> {
            bodyNavigation().stop();
            return null;
        });
    }

    Mob body() { return body; }

    boolean owns(Mob member) { return member == body || members.contains(member); }

    FusionPursuit.Pursuit pursuit() {
        if (pursuit == null) pursuit = new FusionPursuit.Pursuit();
        return pursuit;
    }

    @Nullable
    FusionPursuit.Pursuit pursuitIfPresent() { return pursuit; }

    void clearPursuit() { pursuit = null; }

    private boolean reserved(Mob member) {
        FusionPursuit.Pursuit active = pursuit;
        return member == body && active != null && active.owner != null && active.owner != body
                && active.target != null && active.timing.active(body.tickCount);
    }

    @Nullable
    Mob pathOwner() { return (Mob) claims.owner(this::pathActive); }

    boolean claim(Mob member) {
        if (reserved(member)) return false;
        if (!claims.claim(member, priority(member), this::pathActive)) return false;
        holdingMember = null;
        return true;
    }
    boolean mayPath(Mob member) {
        if (reserved(member)) return false;
        return claims.allows(member, priority(member), this::pathActive);
    }
    int priority(Mob member) {
        int index = members.indexOf(member);
        return index < 0 ? members.size() : index;
    }
    void release(Mob member) {
        claims.release(member);
        if (holdingMember == member) holdingMember = null;
    }
    private boolean pathActive() {
        Path path = bodyNavigation().getPath();
        return path != null && !path.isDone() || holdingMember != null && movingGoal(holdingMember);
    }
    private static boolean movingGoal(Mob member) {
        if (!member.isAlive() || member.isRemoved() || member.isNoAi()) return false;
        for (var goal : ((NativeMobAccess) member).cannedmonster$goals().getAvailableGoals()) {
            if (goal.isRunning() && goal.getFlags().contains(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE)) return true;
        }
        return false;
    }
    private Mob caller() {
        Object current = NAVIGATION_CALLER.get();
        if (current == null) current = OrderedCalls.currentMember();
        return current instanceof Mob mob && (mob == body || members.contains(mob)) ? mob : body;
    }
}
