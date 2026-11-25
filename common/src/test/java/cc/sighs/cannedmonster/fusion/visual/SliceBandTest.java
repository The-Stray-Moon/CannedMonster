package cc.sighs.cannedmonster.fusion.visual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SliceBandTest {
    private static final int STRIDE = 18;
    private static final float[][] SQUARE = {
            {0.0F, 0.0F, 0.0F}, {2.0F, 0.0F, 1.0F}, {2.0F, 2.0F, 1.0F}, {0.0F, 2.0F, 0.0F}};

    @Test
    void clipsBetweenTwoStoredHalfSpaces() {
        float[] packed = pack((x, y, z) -> new float[]{y - 0.5F, 1.5F - y});
        SliceGeometry.Workspace workspace = new SliceGeometry.Workspace(STRIDE);
        int count = workspace.clipBand(packed, 0, 4, 16, 17);
        assertEquals(4, count);
        assertEquals(2.0, area(workspace.vertices(), count), 1.0E-5);
        for (int i = 0; i < count; i++) {
            float y = workspace.vertices()[i * STRIDE + 1];
            assertTrue(y >= 0.5F - 1.0E-5F && y <= 1.5F + 1.0E-5F);
        }
    }

    @Test
    void clipsWithIndependentPlaneAxes() {
        float[] packed = pack((x, y, z) -> new float[]{x - 0.5F, 1.5F - y});
        SliceGeometry.Workspace workspace = new SliceGeometry.Workspace(STRIDE);
        int count = workspace.clipBand(packed, 0, 4, 16, 17);
        assertEquals(4, count);
        assertEquals(2.25, area(workspace.vertices(), count), 1.0E-5);
    }

    @Test
    void unboundedHalfSpacesKeepWholePolygon() {
        float[] packed = pack((x, y, z) -> new float[]{Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY});
        SliceGeometry.Workspace workspace = new SliceGeometry.Workspace(STRIDE);
        assertEquals(4, workspace.clipBand(packed, 0, 4, 16, 17));
        assertEquals(4.0, area(workspace.vertices(), 4), 1.0E-5);
    }

    @Test
    void discardedBandProducesNoVertices() {
        float[] packed = pack((x, y, z) -> new float[]{y - 10.0F, y - 11.0F});
        SliceGeometry.Workspace workspace = new SliceGeometry.Workspace(STRIDE);
        assertEquals(0, workspace.clipBand(packed, 0, 4, 16, 17));
    }

    private interface Distance {
        float[] at(float x, float y, float z);
    }

    private static float[] pack(Distance distance) {
        float[] packed = new float[SQUARE.length * STRIDE];
        for (int i = 0; i < SQUARE.length; i++) {
            float[] vertex = SQUARE[i];
            System.arraycopy(vertex, 0, packed, i * STRIDE, 3);
            float[] values = distance.at(vertex[0], vertex[1], vertex[2]);
            packed[i * STRIDE + 16] = values[0];
            packed[i * STRIDE + 17] = values[1];
        }
        return packed;
    }

    private static double area(float[] vertices, int count) {
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            float[] a = vertex(vertices, i), b = vertex(vertices, (i + 1) % count);
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(sum) / 2.0;
    }

    private static float[] vertex(float[] vertices, int index) {
        return new float[]{vertices[index * STRIDE], vertices[index * STRIDE + 1],
                vertices[index * STRIDE + 2]};
    }
}
