package cc.sighs.cannedmonster.space;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * External position semantics for space entities.
 *
 * Internally an entity lives at its room coordinates so that physics, AI and
 * collisions run against the virtual room. Outside a space frame the same entity
 * must be positioned at the can anchor instead: moдs, commands, entity tracking
 * and save files then see a real world position, never the virtual plot.
 */
public final class SpacePositions {
    private SpacePositions() {}

    public static Vec3 external(Entity entity) {
        if (entity == null || SpaceDomain.rawLookup()) return null;
        if (entity.level().isClientSide()) return null;
        if (SpaceManager.spaceCount() == 0) return null;
        SpaceManager.Space space = SpaceManager.trackedSpace(entity);
        if (space == null) return null;
        SpaceDomain.Frame frame = SpaceDomain.frame();
        if (frame.active() && space.id().equals(frame.space())) return null;
        return space.lastAnchor();
    }
}
