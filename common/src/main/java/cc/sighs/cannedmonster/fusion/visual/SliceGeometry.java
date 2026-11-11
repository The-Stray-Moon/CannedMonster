package cc.sighs.cannedmonster.fusion.visual;

import java.util.ArrayList;
import java.util.List;

public final class SliceGeometry {
    private SliceGeometry() {}

    public static List<float[]> clip(List<float[]> polygon, int axis, float lower, float upper) {
        return plane(plane(polygon, axis, lower, true), axis, upper, false);
    }

    private static List<float[]> plane(List<float[]> input, int axis, float boundary, boolean above) {
        if (input.isEmpty() || !Float.isFinite(boundary)) return input;
        List<float[]> output = new ArrayList<>();
        float[] previous = input.getLast();
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
