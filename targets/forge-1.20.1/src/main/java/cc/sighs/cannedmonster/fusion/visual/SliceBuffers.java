package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.*;

public final class SliceBuffers implements MultiBufferSource {
    private static final int STRIDE = 18;
    private static final int LOWER_AXIS = 16;
    private static final int UPPER_AXIS = 17;
    private static final Set<VertexFormat.Mode> WARNED = EnumSet.noneOf(VertexFormat.Mode.class);
    private final Map<RenderType, Capture> buffers = new LinkedHashMap<>();
    private final Matrix4f inverse = new Matrix4f();
    private final SliceGeometry.Workspace clipping = new SliceGeometry.Workspace(STRIDE);
    private VisualGeometry.Cut lower = VisualGeometry.Cut.unbounded();
    private VisualGeometry.Cut upper = VisualGeometry.Cut.unbounded();

    public SliceBuffers(Matrix4f inverse, VisualGeometry.Cut lower, VisualGeometry.Cut upper) {
        reset(inverse, lower, upper);
    }

    public void reset(Matrix4f inverse, VisualGeometry.Cut lower, VisualGeometry.Cut upper) {
        this.inverse.set(inverse);
        this.lower = lower;
        this.upper = upper;
        if (buffers.size() > 256) buffers.clear();
        buffers.values().forEach(buffer -> buffer.count = 0);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) { return buffers.computeIfAbsent(type, ignored -> new Capture()); }

    public int submit(MultiBufferSource destination) {
        int emitted = 0;
        for (var entry : buffers.entrySet()) {
            VertexFormat.Mode mode = entry.getKey().mode();
            Capture capture = entry.getValue();
            if (capture.count == 0) continue;
            if (mode != VertexFormat.Mode.QUADS && mode != VertexFormat.Mode.TRIANGLES) {
                if (WARNED.add(mode)) LogUtils.getLogger().warn("Fusion slices do not support primitive mode {}; its geometry is omitted", mode);
                continue;
            }
            int size = mode == VertexFormat.Mode.QUADS ? 4 : 3;
            VertexConsumer out = null;
            for (int offset = 0; offset + size <= capture.count; offset += size) {
                int count = clipping.clipBand(capture.vertices, offset * STRIDE, size, LOWER_AXIS, UPPER_AXIS);
                if (count < 3) continue;
                float[] polygon = clipping.vertices();
                if (out == null) out = destination.getBuffer(entry.getKey());
                for (int i = 1; i + 1 < count; i++) {
                    emit(out, polygon, 0);
                    emit(out, polygon, i * STRIDE);
                    emit(out, polygon, (i + 1) * STRIDE);
                    if (size == 4) emit(out, polygon, (i + 1) * STRIDE);
                    emitted += size;
                }
            }
        }
        return emitted;
    }

    private static void emit(VertexConsumer out, float[] v, int p) {
        out.vertex(v[p], v[p + 1], v[p + 2]);
        out.color(Math.round(v[p + 3]), Math.round(v[p + 4]), Math.round(v[p + 5]), Math.round(v[p + 6]));
        out.uv(v[p + 7], v[p + 8]);
        out.overlayCoords(Math.round(v[p + 9]), Math.round(v[p + 10]));
        out.uv2(Math.round(v[p + 11]), Math.round(v[p + 12]));
        float x = v[p + 13], y = v[p + 14], z = v[p + 15];
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        out.normal(length == 0 ? 0 : x / length, length == 0 ? 1 : y / length, length == 0 ? 0 : z / length);
        out.endVertex();
    }

    private final class Capture implements VertexConsumer {
        private float[] vertices = new float[STRIDE * 64];
        private int count;
        private int current;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            current = count++ * STRIDE;
            if (current + STRIDE > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
            Arrays.fill(vertices, current, current + STRIDE, 0);
            vertices[current] = (float) x;
            vertices[current + 1] = (float) y;
            vertices[current + 2] = (float) z;
            Arrays.fill(vertices, current + 3, current + 7, 255);
            float localX = inverse.m00() * (float) x + inverse.m10() * (float) y + inverse.m20() * (float) z + inverse.m30();
            float localY = inverse.m01() * (float) x + inverse.m11() * (float) y + inverse.m21() * (float) z + inverse.m31();
            float localZ = inverse.m02() * (float) x + inverse.m12() * (float) y + inverse.m22() * (float) z + inverse.m32();
            vertices[current + LOWER_AXIS] = lower.distance(localX, localY, localZ);
            vertices[current + UPPER_AXIS] = upper.distance(localX, localY, localZ);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            vertices[current + 3] = red; vertices[current + 4] = green; vertices[current + 5] = blue; vertices[current + 6] = alpha;
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) { vertices[current + 7] = u; vertices[current + 8] = v; return this; }

        @Override
        public VertexConsumer overlayCoords(int u, int v) { vertices[current + 9] = u; vertices[current + 10] = v; return this; }

        @Override
        public VertexConsumer uv2(int u, int v) { vertices[current + 11] = u; vertices[current + 12] = v; return this; }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            vertices[current + 13] = x; vertices[current + 14] = y; vertices[current + 15] = z;
            return this;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }

        @Override
        public void endVertex() {
        }
    }
}
