package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Preview geometry recorder for the 1.21.1 vertex contract ({@code addVertex} plus
 * {@code setColor}/{@code setUv}/{@code setUv1}/{@code setUv2}/{@code setNormal}).
 *
 * <p>Attribute calls are forwarded unchanged, so the measured frame is the frame that is drawn and
 * the interface defaults run against this consumer. Only vertices in the host body space are
 * recorded, using the inverse supplied by {@link CanPreview}.
 */
public final class NeoForgePreviewBounds implements PreviewBounds.Factory {
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

        private void record(float x, float y, float z) {
            if (!isFinite(x) || !isFinite(y) || !isFinite(z)) return;
            float localX = inverse.m00() * x + inverse.m10() * y + inverse.m20() * z + inverse.m30();
            float localY = inverse.m01() * x + inverse.m11() * y + inverse.m21() * z + inverse.m31();
            float localZ = inverse.m02() * x + inverse.m12() * y + inverse.m22() * z + inverse.m32();
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

        private static boolean isFinite(float value) {
            return !Float.isNaN(value) && !Float.isInfinite(value);
        }

        private final class Capture implements VertexConsumer {
            private final VertexConsumer out;

            private Capture(VertexConsumer out) {
                this.out = out;
            }

            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                record(x, y, z);
                out.addVertex(x, y, z);
                return this;
            }

            @Override
            public VertexConsumer setColor(int red, int green, int blue, int alpha) {
                out.setColor(red, green, blue, alpha);
                return this;
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                out.setUv(u, v);
                return this;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                out.setUv1(u, v);
                return this;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                out.setUv2(u, v);
                return this;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                out.setNormal(x, y, z);
                return this;
            }
        }
    }
}
