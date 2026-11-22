package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.space.SpaceDomain;
import cc.sighs.cannedmonster.space.SpaceManager;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * External view of a server level's entities: virtual-room residents are not part
 * of the real world, so ordinary queries must not return them. Inside a space
 * frame only that space's entities are visible, and runtime management (which
 * needs the live instance) explicitly bypasses the filter.
 */
final class SpaceEntityGetter implements LevelEntityGetter<Entity> {
    private final LevelEntityGetter<Entity> delegate;

    SpaceEntityGetter(LevelEntityGetter<Entity> delegate) {
        this.delegate = delegate;
    }

    private static boolean raw() {
        return SpaceDomain.rawLookup();
    }

    private static boolean visible(Entity entity) {
        if (raw()) return true;
        SpaceDomain.Frame frame = SpaceDomain.frame();
        if (frame.active()) return SpaceDomain.sameSpace(frame, entity);
        return !SpaceManager.isTracked(entity);
    }

    private static boolean visible(SpaceDomain.Frame frame, Entity entity) {
        if (raw()) return true;
        if (frame.active()) return SpaceDomain.sameSpace(frame, entity);
        return !SpaceManager.isTracked(entity);
    }

    @Override
    public Entity get(int id) {
        Entity entity = delegate.get(id);
        return entity != null && visible(entity) ? entity : null;
    }

    @Override
    public Entity get(UUID uuid) {
        Entity entity = delegate.get(uuid);
        return entity != null && visible(entity) ? entity : null;
    }

    @Override
    public Iterable<Entity> getAll() {
        SpaceDomain.Frame frame = SpaceDomain.frame();
        List<Entity> visible = new ArrayList<>();
        for (Entity entity : delegate.getAll()) {
            if (visible(frame, entity)) visible.add(entity);
        }
        return visible;
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) {
        SpaceDomain.Frame frame = SpaceDomain.frame();
        delegate.get(test, entity -> visible(frame, entity)
                ? consumer.accept(entity) : AbortableIterationConsumer.Continuation.CONTINUE);
    }

    @Override
    public void get(AABB box, Consumer<Entity> consumer) {
        SpaceDomain.Frame frame = SpaceDomain.frame();
        delegate.get(box, entity -> {
            if (visible(frame, entity)) consumer.accept(entity);
        });
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB box, AbortableIterationConsumer<U> consumer) {
        SpaceDomain.Frame frame = SpaceDomain.frame();
        delegate.get(test, box, entity -> visible(frame, entity)
                ? consumer.accept(entity) : AbortableIterationConsumer.Continuation.CONTINUE);
    }
}
