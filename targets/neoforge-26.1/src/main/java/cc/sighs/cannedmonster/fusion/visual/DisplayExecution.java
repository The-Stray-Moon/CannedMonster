package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class DisplayExecution {
    private static final Set<Entity> MEMBERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Entity> CURRENT = new ThreadLocal<>();

    private DisplayExecution() {}

    public static void register(Entity entity) { MEMBERS.add(entity); }
    public static boolean member(Entity entity) { return entity.level().isClientSide() && MEMBERS.contains(entity); }
    public static Entity current() { return CURRENT.get(); }

    public static void run(Entity member, Runnable action) {
        Entity previous = CURRENT.get();
        CURRENT.set(member);
        try { action.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
