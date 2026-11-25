package cc.sighs.cannedmonster.fusion.visual;

import java.util.ArrayList;
import java.util.List;

public final class SliceGeometry {
    private SliceGeometry() {}

    public static final class Workspace {
        private final int stride;
        private float[] first;
        private float[] second;
        private float[] result;

        public Workspace(int stride) {
            this.stride = stride;
            first = new float[8 * stride];
            second = new float[8 * stride];
        }

        public float[] vertices() { return result; }

        public int clip(float[] source, int offset, int count, int axis, float lower, float upper) {
            ensureCapacity(count);
            System.arraycopy(source, offset, first, 0, count * stride);
            count = plane(first, second, count, axis, lower, true);
            count = plane(second, first, count, axis, upper, false);
            result = first;
            return count;
        }

        /**
         * Clips by two stored half spaces whose signed distances live in {@code firstAxis} and
         * {@code secondAxis}; both keep vertices with a non-negative distance.
         */
        public int clipBand(float[] source, int offset, int count, int firstAxis, int secondAxis) {
            ensureCapacity(count);
            System.arraycopy(source, offset, first, 0, count * stride);
            count = plane(first, second, count, firstAxis, 0.0F, true);
            count = plane(second, first, count, secondAxis, 0.0F, true);
            result = first;
            return count;
        }

        private void ensureCapacity(int count) {
            int capacity = (count + 4) * stride;
            if (first.length < capacity) {
                first = new float[capacity];
                second = new float[capacity];
            }
        }

        private int plane(float[] input, float[] output, int count, int axis, float boundary, boolean above) {
            if (count == 0) return 0;
            if (!Float.isFinite(boundary)) {
                System.arraycopy(input, 0, output, 0, count * stride);
                return count;
            }
            int emitted = 0;
            int previous = (count - 1) * stride;
            boolean previousInside = above ? input[previous + axis] >= boundary : input[previous + axis] <= boundary;
            for (int current = 0; current < count * stride; current += stride) {
                boolean inside = above ? input[current + axis] >= boundary : input[current + axis] <= boundary;
                if (inside != previousInside) {
                    float t = (boundary - input[previous + axis]) / (input[current + axis] - input[previous + axis]);
                    // A non-finite parameter can only come from a degenerate or non-finite distance.
                    // Snapping to the previous vertex keeps the polygon finite instead of writing NaN
                    // positions, which the GPU drops together with the whole primitive.
                    if (!Float.isFinite(t)) t = 0.0F;
                    for (int i = 0; i < stride; i++) output[emitted * stride + i] = input[previous + i] + t * (input[current + i] - input[previous + i]);
                    output[emitted++ * stride + axis] = boundary;
                }
                if (inside) {
                    System.arraycopy(input, current, output, emitted++ * stride, stride);
                }
                previous = current;
                previousInside = inside;
            }
            return emitted;
        }
    }

    public static List<float[]> clip(List<float[]> polygon, int axis, float lower, float upper) {
        return plane(plane(polygon, axis, lower, true), axis, upper, false);
    }

    private static List<float[]> plane(List<float[]> input, int axis, float boundary, boolean above) {
        if (input.isEmpty() || !Float.isFinite(boundary)) return input;
        List<float[]> output = new ArrayList<>();
        float[] previous = input.get(input.size() - 1);
        boolean previousInside = above ? previous[axis] >= boundary : previous[axis] <= boundary;
        for (float[] current : input) {
            boolean inside = above ? current[axis] >= boundary : current[axis] <= boundary;
            if (inside != previousInside) {
                float t = (boundary - previous[axis]) / (current[axis] - previous[axis]);
                float[] intersection = new float[current.length];
                for (int i = 0; i < intersection.length; i++) intersection[i] = previous[i] + t * (current[i] - previous[i]);
                intersection[axis] = boundary;
                output.add(intersection);
            }
            if (inside) output.add(current);
            previous = current;
            previousInside = inside;
        }
        return output;
    }
}
