package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Preview geometry recorder for the 1.20.1 vertex contract ({@code vertex(double...)} plus
 * {@code color}/{@code uv}/{@code overlayCoords}/{@code uv2}/{@code normal} and {@code endVertex}).
 *
 * <p>Every call is forwarded unchanged so the measured frame is the frame that is drawn. Default
 * colours are forwarded through reflection because the method no longer exists in newer versions;
 * this class itself is only compiled for the 1.20.1 target.
 */
public final class LegacyPreviewBounds implements PreviewBounds.Factory {
    @Override
    public PreviewBounds.Recorder create(MultiBufferSource delegate, Matrix4f bodyInverse) {
        return new Recorder(delegate, new Matrix4f(bodyInverse));
    }

    private static final class Recorder implements PreviewBounds.Recorder {
        private final MultiBufferSource delegate;
        private final Matrix4f inverse;
        private float minX = Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE;
        private float minZ = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE;
        private float maxY = -Float.MAX_VALUE;
        private float maxZ = -Float.MAX_VALUE;
        private int vertices;

        private Recorder(MultiBufferSource delegate, Matrix4f inverse) {
            this.delegate = delegate;
            this.inverse = inverse;
        }

        @Override
        public VisualEditorProjection.Box box() {
            if (vertices == 0) return null;
            return new VisualEditorProjection.Box(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return new Capture(delegate.getBuffer(type));
        }

        private void record(double x, double y, double z) {
            if (!isFinite(x) || !isFinite(y) || !isFinite(z)) return;
            float fx = (float) x;
            float fy = (float) y;
            float fz = (float) z;
            float localX = inverse.m00() * fx + inverse.m10() * fy + inverse.m20() * fz + inverse.m30();
            float localY = inverse.m01() * fx + inverse.m11() * fy + inverse.m21() * fz + inverse.m31();
            float localZ = inverse.m02() * fx + inverse.m12() * fy + inverse.m22() * fz + inverse.m32();
            if (vertices == 0) {
                minX = maxX = localX;
                minY = maxY = localY;
                minZ = maxZ = localZ;
            } else {
                minX = Math.min(minX, localX);
                maxX = Math.max(maxX, localX);
                minY = Math.min(minY, localY);
                maxY = Math.max(maxY, localY);
                minZ = Math.min(minZ, localZ);
                maxZ = Math.max(maxZ, localZ);
            }
            vertices++;
        }

        private static boolean isFinite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }

        private final class Capture implements VertexConsumer {
            private final VertexConsumer out;

            private Capture(VertexConsumer out) {
                this.out = out;
            }

            @Override
            public VertexConsumer vertex(double x, double y, double z) {
                record(x, y, z);
                out.vertex(x, y, z);
                return this;
            }

            @Override
            public VertexConsumer color(int red, int green, int blue, int alpha) {
                out.color(red, green, blue, alpha);
                return this;
            }

            @Override
            public VertexConsumer uv(float u, float v) {
                out.uv(u, v);
                return this;
            }

            @Override
            public VertexConsumer overlayCoords(int u, int v) {
                out.overlayCoords(u, v);
                return this;
            }

            @Override
            public VertexConsumer uv2(int u, int v) {
                out.uv2(u, v);
                return this;
            }

            @Override
            public VertexConsumer normal(float x, float y, float z) {
                out.normal(x, y, z);
                return this;
            }

            @Override
            public void endVertex() {
                out.endVertex();
            }

            @Override
            public void defaultColor(int red, int green, int blue, int alpha) {
                out.defaultColor(red, green, blue, alpha);
            }

            @Override
            public void unsetDefaultColor() {
                out.unsetDefaultColor();
            }
        }
    }
}
