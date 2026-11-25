package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * Version bridge for measuring the real rendered preview geometry.
 *
 * <p>The vertex contract differs between versions: 1.20.1 writes through {@code vertex(double...)}
 * with {@code color}/{@code uv}/{@code overlayCoords}/{@code uv2}/{@code normal}/{@code endVertex},
 * while 1.21.1 and 26.1 use {@code addVertex(float...)} and the {@code set*} attribute methods. Each
 * client target therefore binds a recorder built for its own contract; without a binding the preview
 * keeps the deterministic bounding-box framing.
 */
public final class PreviewBounds {
    /** A buffer source that forwards every call and additionally records body-space vertex bounds. */
    public interface Recorder extends MultiBufferSource {
        /** Measured body-space box, or {@code null} when nothing was emitted. */
        VisualEditorProjection.Box box();
    }

    public interface Factory {
        Recorder create(MultiBufferSource delegate, Matrix4f bodyInverse);
    }

    private static Factory factory;

    private PreviewBounds() {}

    /** Called once by each client target during setup. */
    public static void bind(Factory value) {
        factory = value;
    }

    /** Recording source for {@code delegate}, or {@code null} when the target cannot measure. */
    public static Recorder create(MultiBufferSource delegate, Matrix4f bodyInverse) {
        Factory current = factory;
        return current == null ? null : current.create(delegate, bodyInverse);
    }
}
