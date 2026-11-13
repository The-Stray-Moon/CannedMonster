package cc.sighs.cannedmonster.fusion;

import java.util.function.BooleanSupplier;

final class MotionClaims {
    private Object owner;
    private int priority = Integer.MAX_VALUE;

    Object owner(BooleanSupplier active) {
        if (owner != null && !active.getAsBoolean()) {
            clear();
        }
        return owner;
    }

    boolean allows(Object member, int priority, BooleanSupplier active) {
        Object current = owner(active);
        return current == null || current == member || priority < this.priority;
    }

    boolean claim(Object member, int priority, BooleanSupplier active) {
        if (!allows(member, priority, active)) {
            return false;
        }
        owner = member;
        this.priority = priority;
        return true;
    }

    void release(Object member) {
        if (owner == member) {
            clear();
        }
    }

    private void clear() {
        owner = null;
        priority = Integer.MAX_VALUE;
    }
}
