package cc.sighs.cannedmonster.fusion.visual;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for clipped primitives that vanished instead of being trimmed by a cut.
 *
 * <p>A layer has at most one cut on the outside of the stack, so the renderers clip every quad by
 * two half spaces at once: the layer's own cut first and an unbounded one second (upper layers) or
 * the other way round (the root body). These cases run the real workspace with the real
 * {@link VisualGeometry.Cut} distances, because a non-finite distance on the unbounded side made the
 * intersection vertices carry {@code NaN} positions: the GPU then dropped the whole primitive, which
 * read as disappearing side faces on a fused model.
 */
class SliceBandRegressionTest {
    private static final int STRIDE = 18;
    private static final int LOWER_AXIS = 16;
    private static final int UPPER_AXIS = 17;
    private static final float CUT = 0.35F;
    private static final float INVERSE_ROOT = (float) (1.0 / Math.sqrt(2.0));

    private static final VisualGeometry.Cut ABOVE = new VisualGeometry.Cut(0.0F, 1.0F, 0.0F, CUT);
    private static final VisualGeometry.Cut BELOW = new VisualGeometry.Cut(0.0F, -1.0F, 0.0F, -CUT);
    private static final VisualGeometry.Cut UNBOUNDED = VisualGeometry.Cut.unbounded();

    /** One cat torso flank: the cat body box spans y 0.2..0.5, so the 0.35 cut crosses it. */
    private static final float[] FLANK = {
            0.1F, 0.2F, 0.45F, 0.1F, 0.2F, -0.35F, 0.1F, 0.5F, -0.35F, 0.1F, 0.5F, 0.45F};
    /** A vertical unit square in the XY plane, used for arbitrarily rotated cuts. */
    private static final float[] SQUARE = {
            0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F};

    @Test
    void unboundedCutAnswersWithFiniteDistance() {
        float distance = UNBOUNDED.distance(0.0F, CUT, 0.0F);
        assertTrue(Float.isFinite(distance), "unbounded distance must stay finite, was " + distance);
        assertTrue(distance > 0.0F, "every point is inside an unbounded half space");
        assertTrue(UNBOUNDED.isUnbounded());
        assertFalse(ABOVE.isUnbounded());
    }

    /**
     * The reported case: an upper layer clips by its bounded lower cut first, so intersection
     * vertices are created while the unbounded upper cut is still in the workspace.
     */
    @Test
    void quadCrossingOneCutKeepsItsTrimmedHalf() {
        Clip clipped = clip(FLANK, ABOVE, UNBOUNDED);
        assertEquals(4, clipped.count, "a quad crossing one cut stays a quad");
        assertFinite(clipped);
        for (int i = 0; i < clipped.count; i++) {
            float y = clipped.vertex(i)[1];
            assertTrue(y >= CUT - 1.0E-5F, "kept vertices start at the cut, was y=" + y);
        }
        assertEquals(0.8 * 0.15, area(clipped), 1.0E-5, "half of the 0.8 x 0.3 flank survives");
        assertWinding(FLANK, clipped);
        assertTriangles(clipped, 0.8 * 0.15);
        assertInterpolatedAttributes(clipped);
    }

    @Test
    void rootBodyQuadKeepsThePartBelowTheCut() {
        Clip clipped = clip(FLANK, UNBOUNDED, BELOW);
        assertEquals(4, clipped.count);
        assertFinite(clipped);
        for (int i = 0; i < clipped.count; i++) {
            float y = clipped.vertex(i)[1];
            assertTrue(y <= CUT + 1.0E-5F, "the root body keeps the part below its cut, was y=" + y);
        }
        assertEquals(0.8 * 0.15, area(clipped), 1.0E-5);
        assertWinding(FLANK, clipped);
        assertTriangles(clipped, 0.8 * 0.15);
        assertInterpolatedAttributes(clipped);
    }

    /** Every face of a cat-like torso box: horizontal faces are kept or dropped, the four sides trim. */
    @Test
    void catLikeBoxKeepsEveryCrossingFaceTrimmed() {
        List<float[]> faces = box(-0.1F, 0.2F, -0.35F, 0.1F, 0.5F, 0.45F);
        double[] upperLayer = {0.16, 0.0, 0.12, 0.12, 0.03, 0.03};
        double[] rootBody = {0.0, 0.16, 0.12, 0.12, 0.03, 0.03};
        for (int i = 0; i < faces.size(); i++) {
            Clip upper = clip(faces.get(i), ABOVE, UNBOUNDED);
            Clip root = clip(faces.get(i), UNBOUNDED, BELOW);
            assertEquals(upperLayer[i] > 0.0, upper.count > 0, "upper layer face " + i);
            assertEquals(rootBody[i] > 0.0, root.count > 0, "root body face " + i);
            if (upperLayer[i] > 0.0) {
                assertFinite(upper);
                assertEquals(upperLayer[i], area(upper), 1.0E-5, "upper layer face " + i);
                assertWinding(faces.get(i), upper);
                assertTriangles(upper, upperLayer[i]);
            }
            if (rootBody[i] > 0.0) {
                assertFinite(root);
                assertEquals(rootBody[i], area(root), 1.0E-5, "root body face " + i);
                assertWinding(faces.get(i), root);
                assertTriangles(root, rootBody[i]);
            }
        }
    }

    /** Both cuts bounded and non parallel: a two-plane band trims two corners of the same face. */
    @Test
    void twoPlaneBandCutsBothCorners() {
        VisualGeometry.Cut lower = new VisualGeometry.Cut(INVERSE_ROOT, INVERSE_ROOT, 0.0F, 0.2F * INVERSE_ROOT);
        VisualGeometry.Cut upper = new VisualGeometry.Cut(-INVERSE_ROOT, -INVERSE_ROOT, 0.0F, -1.6F * INVERSE_ROOT);
        Clip band = clip(SQUARE, lower, upper);
        assertEquals(6, band.count, "two corner cuts produce a hexagon");
        assertFinite(band);
        assertEquals(0.9, area(band), 1.0E-5);
        assertWinding(SQUARE, band);
        assertTriangles(band, 0.9);
        assertInterpolatedAttributes(band);
    }

    @Test
    void rotatedCutPlanesTrimWithoutLosingWinding() {
        // keeps x + y >= 0.8, the unit square without its lower left corner triangle
        VisualGeometry.Cut diagonal = new VisualGeometry.Cut(INVERSE_ROOT, INVERSE_ROOT, 0.0F, 0.8F * INVERSE_ROOT);
        Clip corner = clip(SQUARE, diagonal, UNBOUNDED);
        assertEquals(5, corner.count);
        assertFinite(corner);
        assertEquals(0.68, area(corner), 1.0E-5);
        assertWinding(SQUARE, corner);
        assertTriangles(corner, 0.68);
        // keeps 0.8 <= x + y <= 1.4, both opposite corners removed
        VisualGeometry.Cut cap = new VisualGeometry.Cut(-INVERSE_ROOT, -INVERSE_ROOT, 0.0F, -1.4F * INVERSE_ROOT);
        Clip band = clip(SQUARE, diagonal, cap);
        assertEquals(6, band.count);
        assertFinite(band);
        assertEquals(0.5, area(band), 1.0E-5);
        assertWinding(SQUARE, band);
        assertTriangles(band, 0.5);
        assertInterpolatedAttributes(band);
    }

    @Test
    void fullyDiscardedOrUntouchedFacesBehave() {
        float[] high = {0.0F, 0.9F, 0.0F, 1.0F, 0.9F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F};
        assertEquals(0, clip(high, UNBOUNDED, BELOW).count, "a face under the cut is dropped");
        Clip kept = clip(high, ABOVE, UNBOUNDED);
        assertEquals(4, kept.count, "a face over the cut is kept whole");
        assertEquals(0.1, area(kept), 1.0E-5);
    }

    /**
     * Emits the fan the renderers emit: triangle {@code (0, i, i + 1)}; for quads the fourth vertex is
     * repeated so the triangle pair covers the polygon exactly once.
     */
    private static void assertTriangles(Clip clip, double expectedArea) {
        double total = 0.0;
        for (int i = 1; i + 1 < clip.count; i++) {
            double part = triangleArea(clip.vertex(0), clip.vertex(i), clip.vertex(i + 1));
            assertTrue(part > 1.0E-7, "fan triangle " + i + " must not collapse");
            total += part;
        }
        assertEquals(expectedArea, total, 1.0E-5, "fan triangles cover the polygon exactly once");
    }

    private static void assertInterpolatedAttributes(Clip clip) {
        // uv and normal mirror the position in the packed input, so interpolation must reproduce them,
        // and the constant color must survive every crossing.
        for (int i = 0; i < clip.count; i++) {
            float[] vertex = clip.vertex(i);
            assertEquals(vertex[0], vertex[7], 1.0E-5, "u at vertex " + i);
            assertEquals(vertex[1], vertex[8], 1.0E-5, "v at vertex " + i);
            assertEquals(vertex[0], vertex[13], 1.0E-5, "normal x at vertex " + i);
            assertEquals(vertex[1], vertex[14], 1.0E-5, "normal y at vertex " + i);
            assertEquals(255.0F, vertex[3], 1.0E-5);
            assertEquals(64.0F, vertex[5], 1.0E-5);
            assertEquals(32.0F, vertex[6], 1.0E-5);
        }
    }

    private static void assertWinding(float[] face, Clip clip) {
        float[] original = faceNormal(face);
        float[] clipped = normal(clip.vertices, clip.count);
        double dot = original[0] * clipped[0] + original[1] * clipped[1] + original[2] * clipped[2];
        assertTrue(dot > 0.0, "clipping must keep the source winding, dot=" + dot);
    }

    private static void assertFinite(Clip clip) {
        assertTrue(clip.count > 0, "expected geometry to survive the cut");
        for (int i = 0; i < clip.count; i++) {
            float[] vertex = clip.vertex(i);
            for (int axis = 0; axis < 16; axis++) {
                assertTrue(Float.isFinite(vertex[axis]), "vertex " + i + " axis " + axis + " is " + vertex[axis]);
            }
            assertTrue(Float.isFinite(vertex[LOWER_AXIS]), "lower distance must stay finite");
            assertTrue(Float.isFinite(vertex[UPPER_AXIS]), "upper distance must stay finite");
        }
    }

    private record Clip(int count, float[] vertices) {
        float[] vertex(int index) {
            return Arrays.copyOfRange(vertices, index * STRIDE, (index + 1) * STRIDE);
        }
    }

    private static Clip clip(float[] polygon, VisualGeometry.Cut lower, VisualGeometry.Cut upper) {
        int count = polygon.length / 3;
        float[] packed = new float[count * STRIDE];
        for (int i = 0; i < count; i++) {
            float x = polygon[i * 3], y = polygon[i * 3 + 1], z = polygon[i * 3 + 2];
            int offset = i * STRIDE;
            packed[offset] = x;
            packed[offset + 1] = y;
            packed[offset + 2] = z;
            packed[offset + 3] = 255.0F;
            packed[offset + 4] = 128.0F;
            packed[offset + 5] = 64.0F;
            packed[offset + 6] = 32.0F;
            packed[offset + 7] = x;
            packed[offset + 8] = y;
            packed[offset + 9] = 16.0F;
            packed[offset + 10] = 32.0F;
            packed[offset + 11] = 240.0F;
            packed[offset + 12] = 240.0F;
            packed[offset + 13] = x;
            packed[offset + 14] = y;
            packed[offset + 15] = z;
            packed[offset + LOWER_AXIS] = lower.distance(x, y, z);
            packed[offset + UPPER_AXIS] = upper.distance(x, y, z);
        }
        SliceGeometry.Workspace workspace = new SliceGeometry.Workspace(STRIDE);
        int kept = workspace.clipBand(packed, 0, count, LOWER_AXIS, UPPER_AXIS);
        return new Clip(kept, Arrays.copyOf(workspace.vertices(), kept * STRIDE));
    }

    /** Six quads of an axis aligned box, each wound counter clockwise seen from outside. */
    private static List<float[]> box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        List<float[]> faces = new ArrayList<>();
        faces.add(new float[]{minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ});
        faces.add(new float[]{minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ});
        faces.add(new float[]{maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ});
        faces.add(new float[]{minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ});
        faces.add(new float[]{maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ});
        faces.add(new float[]{minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ});
        return faces;
    }

    private static double area(Clip clip) {
        return magnitude(normal(clip.vertices, clip.count)) / 2.0;
    }

    private static float[] faceNormal(float[] face) {
        float nx = 0.0F, ny = 0.0F, nz = 0.0F;
        int count = face.length / 3;
        for (int i = 0; i < count; i++) {
            int a = i * 3, b = (i + 1) % count * 3;
            nx += (face[a + 1] - face[b + 1]) * (face[a + 2] + face[b + 2]);
            ny += (face[a + 2] - face[b + 2]) * (face[a] + face[b]);
            nz += (face[a] - face[b]) * (face[a + 1] + face[b + 1]);
        }
        return new float[]{nx, ny, nz};
    }

    private static float[] normal(float[] vertices, int count) {
        float nx = 0.0F, ny = 0.0F, nz = 0.0F;
        for (int i = 0; i < count; i++) {
            int a = i * STRIDE, b = (i + 1) % count * STRIDE;
            nx += (vertices[a + 1] - vertices[b + 1]) * (vertices[a + 2] + vertices[b + 2]);
            ny += (vertices[a + 2] - vertices[b + 2]) * (vertices[a] + vertices[b]);
            nz += (vertices[a] - vertices[b]) * (vertices[a + 1] + vertices[b + 1]);
        }
        return new float[]{nx, ny, nz};
    }

    private static double magnitude(float[] vector) {
        return Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
    }

    private static double triangleArea(float[] a, float[] b, float[] c) {
        double abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        double acx = c[0] - a[0], acy = c[1] - a[1], acz = c[2] - a[2];
        double x = aby * acz - abz * acy;
        double y = abz * acx - abx * acz;
        double z = abx * acy - aby * acx;
        return Math.sqrt(x * x + y * y + z * z) / 2.0;
    }
}
