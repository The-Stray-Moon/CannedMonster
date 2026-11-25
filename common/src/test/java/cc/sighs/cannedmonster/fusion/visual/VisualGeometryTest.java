package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VisualGeometryTest {
    @Test
    void rotationsUseDegreesXyz() {
        Quaternionf rotation = VisualGeometry.rotation(new VisualLayout.V(0.0F, 90.0F, 0.0F));
        Vector3f forward = rotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        assertEquals(1.0F, forward.x, 1.0E-5);
        assertEquals(0.0F, forward.y, 1.0E-5);
        assertEquals(0.0F, forward.z, 1.0E-5);
    }

    @Test
    void horizontalPlaneIsNormalizedToHostHeight() {
        VisualLayout.Plane plane = new VisualLayout.Plane(new VisualLayout.V(0.0F, 0.5F, 0.0F), VisualLayout.V.ZERO);
        VisualGeometry.Cut lower = VisualGeometry.cut(plane, 2.0F, 4.0F, false);
        VisualGeometry.Cut upper = VisualGeometry.cut(plane, 2.0F, 4.0F, true);
        assertEquals(0.5F, lower.distance(0.0F, 2.5F, 0.0F), 1.0E-6);
        assertEquals(-0.5F, upper.distance(0.0F, 2.5F, 0.0F), 1.0E-6);
        assertTrue(lower.distance(0.0F, 1.5F, 0.0F) < 0.0F);
    }

    @Test
    void rotatedPlaneKeepsTheSideOppositeItsNormal() {
        VisualLayout.Plane plane = new VisualLayout.Plane(VisualLayout.V.ZERO, new VisualLayout.V(0.0F, 0.0F, 90.0F));
        VisualGeometry.Cut lower = VisualGeometry.cut(plane, 2.0F, 2.0F, false);
        assertTrue(lower.distance(-1.0F, 0.0F, 0.0F) > 0.0F);
        assertTrue(lower.distance(1.0F, 0.0F, 0.0F) < 0.0F);
    }

    @Test
    void outerLayersHaveOnlyOneCut() {
        VisualLayout layout = VisualLayout.reset(3);
        assertFalse(VisualGeometry.lower(layout, 0, 1.0F, 1.0F).isUnbounded());
        assertTrue(VisualGeometry.upper(layout, 0, 1.0F, 1.0F).isUnbounded());
        assertTrue(VisualGeometry.lower(layout, 2, 1.0F, 1.0F).isUnbounded());
        assertFalse(VisualGeometry.upper(layout, 2, 1.0F, 1.0F).isUnbounded());
        assertTrue(VisualGeometry.lower(VisualLayout.reset(1), 0, 1.0F, 1.0F).isUnbounded());
        assertTrue(VisualGeometry.upper(VisualLayout.reset(1), 0, 1.0F, 1.0F).isUnbounded());
        assertEquals(-1.0F / 6.0F, VisualGeometry.lower(layout, 0, 1.0F, 1.0F).distance(0.0F, 0.5F, 0.0F), 1.0E-6);
        assertEquals(1.0F / 6.0F, VisualGeometry.upper(layout, 1, 1.0F, 1.0F).distance(0.0F, 0.5F, 0.0F), 1.0E-6);
    }

    @Test
    void resetChangesEffectiveRenderFromTheAbsentLegacyInitialLayout() {
        VisualLayout legacy = VisualLayout.legacySizes(List.of(
                new VisualLayout.Size(1.0F, 2.0F),
                new VisualLayout.Size(2.0F, 4.0F)));
        VisualLayout initial = VisualGeometry.aligned(null, 2, () -> legacy);
        assertEquals(legacy, initial);
        VisualLayout reset = VisualLayout.reset(2);
        assertNotEquals(initial.layers().get(0).scale(), reset.layers().get(0).scale());

        AABB initialBox = VisualGeometry.layerBox(0.0, 64.0, 0.0, 2.0F, 4.0F, 1.0F, 2.0F,
                initial.layers().get(0), VisualGeometry.bandFloor(initial, 0));
        AABB resetBox = VisualGeometry.layerBox(0.0, 64.0, 0.0, 2.0F, 4.0F, 1.0F, 2.0F,
                reset.layers().get(0), VisualGeometry.bandFloor(reset, 0));
        assertEquals(-1.0, initialBox.minX, 1.0E-6);
        assertEquals(1.0, initialBox.maxX, 1.0E-6);
        assertEquals(64.0, initialBox.minY, 1.0E-6);
        assertEquals(68.0, initialBox.maxY, 1.0E-6);
        assertEquals(-0.5, resetBox.minX, 1.0E-6);
        assertEquals(0.5, resetBox.maxX, 1.0E-6);
        assertEquals(66.0, resetBox.minY, 1.0E-6);
        assertEquals(68.0, resetBox.maxY, 1.0E-6);
        assertNotEquals(initialBox, resetBox);
    }

    @Test
    void legacyLayoutStoresTheNegatedSlotFloorSoStretchedMembersKeepTheBodyOrigin() {
        VisualLayout legacy = VisualLayout.legacySizes(List.of(
                new VisualLayout.Size(1.0F, 2.0F),
                new VisualLayout.Size(4.0F, 8.0F),
                new VisualLayout.Size(2.0F, 4.0F)));
        float[][] sizes = {{1.0F, 2.0F}, {4.0F, 8.0F}, {2.0F, 4.0F}};
        for (int i = 0; i < legacy.layerCount(); i++) {
            float floor = VisualGeometry.bandFloor(legacy, i);
            assertEquals(0.0F, floor + legacy.layers().get(i).offset().y(), 1.0E-6);
            AABB box = VisualGeometry.layerBox(0.0, 64.0, 0.0, 2.0F, 4.0F, sizes[i][0], sizes[i][1],
                    legacy.layers().get(i), floor);
            assertEquals(64.0, box.minY, 1.0E-6, "layer " + i);
            assertEquals(68.0, box.maxY, 1.0E-6, "layer " + i);
        }
    }

    /**
     * The reset layout must keep every layer visible: a member that is shorter than its slot is
     * anchored on the slot floor, so the cut below it only trims the neighbouring layer.
     */
    @Test
    void resetTwoLayerKeepsTheShortMemberInsideItsOwnBand() {
        float hostWidth = 0.9F;
        float hostHeight = 1.4F;
        float memberWidth = 0.4F;
        float memberHeight = 0.6F;
        VisualLayout reset = VisualLayout.reset(2);
        float floor = VisualGeometry.bandFloor(reset, 0);
        assertEquals(0.5F, floor, 1.0E-6);

        AABB anchored = VisualGeometry.layerBox(0.0, 64.0, 0.0, hostWidth, hostHeight,
                memberWidth, memberHeight, reset.layers().get(0), floor);
        assertEquals(64.7, anchored.minY, 1.0E-6);
        assertEquals(65.3, anchored.maxY, 1.0E-6);

        VisualGeometry.Cut lower = VisualGeometry.lower(reset, 0, hostWidth, hostHeight);
        VisualGeometry.Cut upper = VisualGeometry.upper(reset, 0, hostWidth, hostHeight);
        assertTrue(lower.distance(0.0F, (float) (anchored.minY - 64.0), 0.0F) >= 0.0F);
        assertTrue(upper.isUnbounded());
        assertTrue(lower.distance(0.0F, (float) (anchored.maxY - 64.0), 0.0F) > 0.0F);

        AABB unanchored = VisualGeometry.layerBox(0.0, 64.0, 0.0, hostWidth, hostHeight,
                memberWidth, memberHeight, reset.layers().get(0), 0.0F);
        assertEquals(64.0, unanchored.minY, 1.0E-6);
        assertEquals(64.6, unanchored.maxY, 1.0E-6);
        assertTrue(lower.distance(0.0F, (float) (unanchored.maxY - 64.0), 0.0F) < 0.0F);

        AABB body = VisualGeometry.layerBox(0.0, 64.0, 0.0, hostWidth, hostHeight, hostWidth, hostHeight,
                reset.layers().get(1), VisualGeometry.bandFloor(reset, 1));
        assertEquals(64.0, body.minY, 1.0E-6);
        assertEquals(65.4, body.maxY, 1.0E-6);
        VisualGeometry.Cut cap = VisualGeometry.upper(reset, 1, hostWidth, hostHeight);
        assertTrue(cap.distance(0.0F, 0.7F, 0.0F) >= 0.0F);
        assertTrue(cap.distance(0.0F, 0.8F, 0.0F) < 0.0F);
    }

    @Test
    void alignedLayoutFallsBackToLegacyForAbsentOrMismatchedComposition() {
        VisualLayout two = VisualLayout.reset(2);
        assertSame(two, VisualGeometry.aligned(two, 2, () -> VisualLayout.reset(9)));

        VisualLayout legacy = VisualLayout.legacySizes(List.of(
                new VisualLayout.Size(1.0F, 1.0F),
                new VisualLayout.Size(2.0F, 2.0F),
                new VisualLayout.Size(2.0F, 2.0F)));
        assertEquals(legacy, VisualGeometry.aligned(null, 3, () -> legacy));
        assertEquals(legacy, VisualGeometry.aligned(two, 3, () -> legacy));
    }

    @Test
    void nativeUnitScaleDiffersFromLegacyFitForAnAsymmetricMember() {
        VisualLayout.Size host = new VisualLayout.Size(2.0F, 4.0F);
        VisualLayout.Size member = new VisualLayout.Size(1.0F, 2.0F);
        VisualLayout.V fit = VisualLayout.legacyScale(host, member);
        assertEquals(new VisualLayout.V(2.0F, 2.0F, 2.0F), fit);

        VisualLayout.Layer legacyLayer = new VisualLayout.Layer(VisualLayout.V.ZERO,
                VisualLayout.V.ZERO, fit);
        AABB legacyBox = VisualGeometry.layerBox(10.0, 64.0, 5.0, 2.0F, 4.0F, 1.0F, 2.0F, legacyLayer, 0.0F);
        AABB resetBox = VisualGeometry.layerBox(10.0, 64.0, 5.0, 2.0F, 4.0F, 1.0F, 2.0F,
                VisualLayout.Layer.IDENTITY, 0.0F);

        assertEquals(9.0, legacyBox.minX, 1.0E-6);
        assertEquals(11.0, legacyBox.maxX, 1.0E-6);
        assertEquals(64.0, legacyBox.minY, 1.0E-6);
        assertEquals(68.0, legacyBox.maxY, 1.0E-6);
        assertEquals(9.5, resetBox.minX, 1.0E-6);
        assertEquals(10.5, resetBox.maxX, 1.0E-6);
        assertEquals(64.0, resetBox.minY, 1.0E-6);
        assertEquals(66.0, resetBox.maxY, 1.0E-6);
        assertNotEquals(legacyBox, resetBox);
    }

    @Test
    void unitLayerBoxMatchesTheHostBox() {
        AABB box = VisualGeometry.layerBox(10.0, 64.0, 5.0, 1.0F, 2.0F, 1.0F, 2.0F,
                VisualLayout.Layer.IDENTITY, 0.0F);
        assertEquals(9.5, box.minX, 1.0E-6);
        assertEquals(64.0, box.minY, 1.0E-6);
        assertEquals(4.5, box.minZ, 1.0E-6);
        assertEquals(10.5, box.maxX, 1.0E-6);
        assertEquals(66.0, box.maxY, 1.0E-6);
        assertEquals(5.5, box.maxZ, 1.0E-6);
    }

    @Test
    void layerBoxAppliesNativeScaleAndOffset() {
        VisualLayout.Layer layer = new VisualLayout.Layer(new VisualLayout.V(1.0F, 0.5F, 0.0F),
                VisualLayout.V.ZERO, new VisualLayout.V(2.0F, 2.0F, 2.0F));
        AABB box = VisualGeometry.layerBox(10.0, 64.0, 5.0, 2.0F, 4.0F, 2.0F, 4.0F, layer, 0.0F);
        assertEquals(10.0, box.minX, 1.0E-6);
        assertEquals(66.0, box.minY, 1.0E-6);
        assertEquals(14.0, box.maxX, 1.0E-6);
        assertEquals(74.0, box.maxY, 1.0E-6);
    }

    @Test
    void rotatedLayerBoxCoversRotatedFootprint() {
        VisualLayout.Layer layer = new VisualLayout.Layer(VisualLayout.V.ZERO,
                new VisualLayout.V(0.0F, 0.0F, 90.0F), VisualLayout.V.UNIT);
        AABB box = VisualGeometry.layerBox(0.0, 0.0, 0.0, 2.0F, 2.0F, 2.0F, 2.0F, layer, 0.0F);
        assertEquals(-2.0, box.minX, 1.0E-5);
        assertEquals(-1.0, box.minY, 1.0E-5);
        assertEquals(-1.0, box.minZ, 1.0E-5);
        assertEquals(0.0, box.maxX, 1.0E-5);
        assertEquals(1.0, box.maxY, 1.0E-5);
        assertEquals(1.0, box.maxZ, 1.0E-5);
    }
}
