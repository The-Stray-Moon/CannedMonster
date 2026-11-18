package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Captures the vertices a model emits and re-emits only the part inside a horizontal band of the
 * fusion body. Vertices arrive in body space (the model applies its own pose), so the inverse body
 * matrix maps them back before clipping.
 */
public final class SliceConsumer implements VertexConsumer {
    private static final int STRIDE = 17;
    private static final int AXIS = 16;
    private static final Set<VertexFormat.Mode> WARNED = EnumSet.noneOf(VertexFormat.Mode.class);

    private final VertexConsumer destination;
    private final Matrix4f inverse;
    private final float lower;
    private final float upper;
    private final VertexFormat.Mode mode;
    private float[] vertices = new float[STRIDE * 64];
    private int count;
    private int current;
    private final SliceGeometry.Workspace clipping = new SliceGeometry.Workspace(STRIDE);

    public SliceConsumer(VertexConsumer destination, Matrix4f inverse, float lower, float upper, VertexFormat.Mode mode) {
        this.destination = destination;
        this.inverse = new Matrix4f(inverse);
        this.lower = lower;
        this.upper = upper;
        this.mode = mode;
    }

    public int flush() {
        if (count == 0) return 0;
        if (mode != VertexFormat.Mode.QUADS && mode != VertexFormat.Mode.TRIANGLES) {
            if (WARNED.add(mode)) LogUtils.getLogger().warn("Fusion slices do not support primitive mode {}; its geometry is omitted", mode);
            count = 0;
            return 0;
        }
        int size = mode == VertexFormat.Mode.QUADS ? 4 : 3;
        int emitted = 0;
        for (int offset = 0; offset + size <= count; offset += size) {
            int clipped = clipping.clip(vertices, offset * STRIDE, size, AXIS, lower, upper);
            if (clipped < 3) continue;
            float[] polygon = clipping.vertices();
            for (int i = 1; i + 1 < clipped; i++) {
                emit(polygon, 0);
                emit(polygon, i * STRIDE);
                emit(polygon, (i + 1) * STRIDE);
                if (size == 4) emit(polygon, (i + 1) * STRIDE);
                emitted += size;
            }
        }
        count = 0;
        return emitted;
    }

    private void emit(float[] v, int p) {
        destination.addVertex(v[p], v[p + 1], v[p + 2]);
        destination.setColor(Math.round(v[p + 3]), Math.round(v[p + 4]), Math.round(v[p + 5]), Math.round(v[p + 6]));
        destination.setUv(v[p + 7], v[p + 8]);
        destination.setUv1(Math.round(v[p + 9]), Math.round(v[p + 10]));
        destination.setUv2(Math.round(v[p + 11]), Math.round(v[p + 12]));
        float x = v[p + 13], y = v[p + 14], z = v[p + 15];
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        destination.setNormal(length == 0 ? 0 : x / length, length == 0 ? 1 : y / length, length == 0 ? 0 : z / length);
    }

    private void begin() {
        current = count++ * STRIDE;
        if (current + STRIDE > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
        Arrays.fill(vertices, current, current + STRIDE, 0);
        Arrays.fill(vertices, current + 3, current + 7, 255);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        begin();
        vertices[current] = x;
        vertices[current + 1] = y;
        vertices[current + 2] = z;
        vertices[current + AXIS] = inverse.m01() * x + inverse.m11() * y + inverse.m21() * z + inverse.m31();
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        vertices[current + 3] = red;
        vertices[current + 4] = green;
        vertices[current + 5] = blue;
        vertices[current + 6] = alpha;
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        return setColor(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        vertices[current + 7] = u;
        vertices[current + 8] = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        vertices[current + 9] = u;
        vertices[current + 10] = v;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        vertices[current + 11] = u;
        vertices[current + 12] = v;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        vertices[current + 13] = x;
        vertices[current + 14] = y;
        vertices[current + 15] = z;
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }
}
