package cc.sighs.cannedmonster.fusion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Navigation view of a facet. Paths are computed and followed on the body navigation so that the
 * requesting member does not need a locomotion implementation of its own.
 */
public final class FusionNavigation extends PathNavigation {
    private final Mob owner;
    private final PathNavigation original;

    FusionNavigation(Mob owner, PathNavigation original) {
        super(owner, owner.level());
        this.owner = owner;
        this.original = original;
    }

    @Nullable
    private FusionMotion motion() {
        return FusionMotion.of(owner);
    }

    @Nullable
    private PathNavigation active() {
        FusionMotion motion = motion();
        return motion != null && motion.pathOwner() == owner ? motion.bodyNavigation() : null;
    }

    @Nullable
    private PathNavigation pathTarget() {
        FusionMotion motion = motion();
        return motion != null && motion.mayPath(owner) ? motion.bodyNavigation() : null;
    }

    @Override
    protected PathFinder createPathFinder(int range) {
        return null;
    }

    @Override
    protected Vec3 getTempMobPos() {
        return owner.position();
    }

    @Override
    protected boolean canUpdatePath() {
        return true;
    }

    @Override
    public void tick() {
    }

    @Override
    public void stop() {
        FusionMotion motion = motion();
        if (motion == null) {
            return;
        }
        motion.stop(owner);
    }

    @Override
    public boolean moveTo(@Nullable Path path, double speed) {
        if (path == null) {
            stop();
            return false;
        }
        FusionMotion motion = motion();
        return motion != null && motion.moveTo(owner, path, speed);
    }

    @Override
    public Path createPath(BlockPos pos, int radius) {
        PathNavigation target = pathTarget();
        return target == null ? null : motion().query(owner, () -> target.createPath(pos, radius));
    }

    @Override
    public Path createPath(BlockPos pos, int radius, int maxRange) {
        PathNavigation target = pathTarget();
        return target == null ? null : motion().query(owner, () -> target.createPath(pos, radius, maxRange));
    }

    @Override
    public Path createPath(Entity entity, int radius) {
        PathNavigation target = pathTarget();
        return target == null ? null : motion().query(owner, () -> target.createPath(entity, radius));
    }

    @Override
    public Path createPath(Stream<BlockPos> positions, int radius) {
        PathNavigation target = pathTarget();
        return target == null ? null : motion().query(owner, () -> target.createPath(positions, radius));
    }

    @Override
    public Path createPath(Set<BlockPos> positions, int radius) {
        PathNavigation target = pathTarget();
        return target == null ? null : motion().query(owner, () -> target.createPath(positions, radius));
    }

    @Override
    @Nullable
    public Path getPath() {
        PathNavigation active = active();
        return active == null ? null : active.getPath();
    }

    @Override
    public boolean isDone() {
        PathNavigation active = active();
        return active == null || active.isDone();
    }

    @Override
    public boolean isInProgress() {
        PathNavigation active = active();
        return active != null && active.isInProgress();
    }

    @Override
    public boolean isStuck() {
        PathNavigation active = active();
        return active != null && active.isStuck();
    }

    @Override
    public void recomputePath() {
        PathNavigation active = active();
        if (active != null) {
            active.recomputePath();
        }
    }

    @Override
    public void setSpeedModifier(double speed) {
        PathNavigation active = active();
        if (active != null) {
            active.setSpeedModifier(speed);
        }
    }

    @Override
    @Nullable
    public BlockPos getTargetPos() {
        PathNavigation active = active();
        return active == null ? null : active.getTargetPos();
    }

    @Override
    public boolean shouldRecomputePath(BlockPos pos) {
        PathNavigation active = active();
        return active != null && active.shouldRecomputePath(pos);
    }

    @Override
    public float getMaxDistanceToWaypoint() {
        PathNavigation active = active();
        return active == null ? 0.5F : active.getMaxDistanceToWaypoint();
    }

    @Override
    public boolean isStableDestination(BlockPos pos) {
        return original.isStableDestination(pos);
    }

    @Override
    public NodeEvaluator getNodeEvaluator() {
        PathNavigation active = active();
        return active == null ? original.getNodeEvaluator() : active.getNodeEvaluator();
    }

    @Override
    public boolean canFloat() {
        return original.canFloat();
    }

    @Override
    public void setCanFloat(boolean canFloat) {
        original.setCanFloat(canFloat);
    }

    @Override
    public void resetMaxVisitedNodesMultiplier() {
        original.resetMaxVisitedNodesMultiplier();
    }

    @Override
    public void setMaxVisitedNodesMultiplier(float multiplier) {
        original.setMaxVisitedNodesMultiplier(multiplier);
    }
}
