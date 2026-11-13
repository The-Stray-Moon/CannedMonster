package cc.sighs.cannedmonster.fusion;

import java.util.function.Supplier;

final class ExecutionSlot<T> {
    private final ThreadLocal<T> current = new ThreadLocal<>();

    T get() { return current.get(); }

    <R> R run(T frame, Supplier<R> action) {
        T previous = current.get();
        current.set(frame);
        try {
            return action.get();
        } finally {
            if (previous == null) current.remove(); else current.set(previous);
        }
    }
}
