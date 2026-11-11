package cc.sighs.cannedmonster.fusion.visual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SliceGeometryTest {
    private static final List<float[]> SQUARE = List.of(
            new float[]{0, 0, 0}, new float[]{2, 0, 1}, new float[]{2, 2, 1}, new float[]{0, 2, 0});

    @Test
    void keepsWholePolygonInsideSlice() {
        assertEquals(SQUARE, SliceGeometry.clip(SQUARE, 1, -1, 3));
    }

    @Test
    void discardsWholePolygonOutsideSlice() {
        assertTrue(SliceGeometry.clip(SQUARE, 1, 3, 4).isEmpty());
    }

    @Test
    void createsEdgesWhereNoOriginalVertexIsInside() {
        var result = SliceGeometry.clip(SQUARE, 1, 0.5F, 1.5F);
        assertEquals(4, result.size());
        assertEquals(2, area(result), 0.00001);
        assertTrue(result.stream().allMatch(v -> v[1] >= 0.5F && v[1] <= 1.5F));
    }

    @Test
    void interpolatesTextureCoordinatesAtIntersections() {
        var result = SliceGeometry.clip(SQUARE, 0, 0.5F, 1.5F);
        assertTrue(result.stream().allMatch(v -> Math.abs(v[2] - v[0] / 2) < 0.00001));
    }

    @Test
    void adjacentSlicesPreserveAreaWithoutOverlap() {
        double area = 0;
        for (int i = 0; i < 7; i++) area += area(SliceGeometry.clip(SQUARE, 1, 2F * i / 7, 2F * (i + 1) / 7));
        assertEquals(area(SQUARE), area, 0.00001);
    }

    @Test
    void handlesUnboundedEndsAndEmptyInput() {
        assertEquals(SQUARE, SliceGeometry.clip(SQUARE, 1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY));
        assertTrue(SliceGeometry.clip(List.of(), 1, 0, 1).isEmpty());
    }

    @Test
    void doesNotMutateSourceGeometry() {
        SliceGeometry.clip(SQUARE, 1, 0.5F, 1.5F);
        assertArrayEquals(new float[]{0, 0, 0}, SQUARE.getFirst());
        assertArrayEquals(new float[]{2, 2, 1}, SQUARE.get(2));
    }

    @Test
    void handlesBoundaryVerticesWithoutNan() {
        var result = SliceGeometry.clip(SQUARE, 1, 0, 1);
        assertTrue(result.stream().allMatch(v -> Float.isFinite(v[0]) && Float.isFinite(v[1])));
        assertEquals(2, area(result), 0.00001);
    }

    private static double area(List<float[]> polygon) {
        double sum = 0;
        for (int i = 0; i < polygon.size(); i++) {
            float[] a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(sum) / 2;
    }
}
