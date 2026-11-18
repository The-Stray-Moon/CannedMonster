package cc.sighs.cannedmonster.fusion.visual;

import org.joml.Matrix4f;

/**
 * Thread local band applied while a fused member is submitted. Model and custom geometry submits
 * are converted to sliced custom geometry by {@code SubmitNodeSliceMixin}.
 */
public final class SliceContext {
    public record Band(Matrix4f inverse, float lower, float upper) {}

    private static final ThreadLocal<Band> CURRENT = new ThreadLocal<>();

    private SliceContext() {}

    public static Band current() {
        return CURRENT.get();
    }

    public static boolean active() {
        return CURRENT.get() != null;
    }

    public static void run(Band band, Runnable action) {
        Band previous = CURRENT.get();
        CURRENT.set(band);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
