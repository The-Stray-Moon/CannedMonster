package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.Mob;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class TargetResets {
    private static final Set<Mob> PENDING = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Mob> CHECKING = Collections.newSetFromMap(new WeakHashMap<>());

    private TargetResets() {}

    public static void cleared(Mob member) {
        if (FusionTraits.playerMember(member) && OrderedCalls.currentMember() == member) PENDING.add(member);
    }

    public static boolean pending(Mob member) { return PENDING.contains(member); }

    public static boolean begin(Mob member) {
        boolean pending = pending(member);
        if (pending) CHECKING.add(member);
        else CHECKING.remove(member);
        return pending;
    }

    public static void checked(Mob member) {
        if (CHECKING.remove(member)) PENDING.remove(member);
    }
}
