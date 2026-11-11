package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

public final class SliceBuffers implements MultiBufferSource {
    private static final Set<VertexFormat.Mode> WARNED = EnumSet.noneOf(VertexFormat.Mode.class);
    private final Map<RenderType, Capture> buffers = new LinkedHashMap<>();
    private final Matrix4f inverse;
    private final float lower;
    private final float upper;

    public SliceBuffers(Matrix4f inverse, float lower, float upper) {
        this.inverse = new Matrix4f(inverse);
        this.lower = lower;
        this.upper = upper;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return buffers.computeIfAbsent(type, ignored -> new Capture());
    }

    public int submit(MultiBufferSource destination) {
        int emitted = 0;
        for (var entry : buffers.entrySet()) {
            VertexFormat.Mode mode = entry.getKey().mode();
            List<float[]> vertices = entry.getValue().vertices;
            if (mode != VertexFormat.Mode.QUADS && mode != VertexFormat.Mode.TRIANGLES) {
                if (WARNED.add(mode)) LogUtils.getLogger().warn("Fusion slices do not support primitive mode {}; its geometry is omitted", mode);
                continue;
            }
            int size = mode == VertexFormat.Mode.QUADS ? 4 : 3;
            VertexConsumer out = null;
            for (int offset = 0; offset + size <= vertices.size(); offset += size) {
                List<float[]> polygon = SliceGeometry.clip(vertices.subList(offset, offset + size), 16, lower, upper);
                if (polygon.size() < 3) continue;
                if (out == null) out = destination.getBuffer(entry.getKey());
                for (int i = 1; i + 1 < polygon.size(); i++) {
                    emit(out, polygon.getFirst());
                    emit(out, polygon.get(i));
                    emit(out, polygon.get(i + 1));
                    if (size == 4) emit(out, polygon.get(i + 1));
                    emitted += size;
                }
            }
        }
        return emitted;
    }

    private static void emit(VertexConsumer out, float[] v) {
        out.addVertex(v[0], v[1], v[2]);
        out.setColor(Math.round(v[3]), Math.round(v[4]), Math.round(v[5]), Math.round(v[6]));
        out.setUv(v[7], v[8]);
        out.setUv1(Math.round(v[9]), Math.round(v[10]));
        out.setUv2(Math.round(v[11]), Math.round(v[12]));
        float length = (float) Math.sqrt(v[13] * v[13] + v[14] * v[14] + v[15] * v[15]);
        out.setNormal(length == 0 ? 0 : v[13] / length, length == 0 ? 1 : v[14] / length, length == 0 ? 0 : v[15] / length);
    }

    private final class Capture implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();
        private float[] current;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            current = new float[17];
            current[0] = x;
            current[1] = y;
            current[2] = z;
            Arrays.fill(current, 3, 7, 255);
            current[16] = inverse.transformPosition(x, y, z, new Vector3f()).y;
            vertices.add(current);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            current[3] = red; current[4] = green; current[5] = blue; current[6] = alpha;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            current[7] = u; current[8] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            current[9] = u; current[10] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            current[11] = u; current[12] = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            current[13] = x; current[14] = y; current[15] = z;
            return this;
        }
    }
}
