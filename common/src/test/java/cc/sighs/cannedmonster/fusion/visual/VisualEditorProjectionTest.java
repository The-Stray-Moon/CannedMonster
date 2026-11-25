package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VisualEditorProjectionTest {
    private static VisualEditorProjection.Camera camera(float yaw, float pitch) {
        return VisualEditorProjection.camera(0, 0, 100, 100, 2.0F, 2.0F, yaw, pitch, 1.0F);
    }

    @Test
    void bodyCenterProjectsToThePreviewCenter() {
        VisualEditorProjection.Camera camera = camera(25.0F, 10.0F);
        VisualEditorProjection.Point feet = camera.project(0.0F, 0.0F, 0.0F);
        assertEquals(50.0F, feet.x(), 1.0E-4);
        assertEquals(50.0F + 42.5F, feet.y(), 1.0E-4);
        assertEquals(42.5F, camera.scale(), 1.0E-4);
    }

    @Test
    void pitchPivotsOnTheFeetAndLiftsTheBodyCenterLikeThePreviewPose() {
        VisualEditorProjection.Camera upright = camera(0.0F, 0.0F);
        assertEquals(50.0F + 42.5F, upright.project(0.0F, 0.0F, 0.0F).y(), 1.0E-4);
        assertEquals(50.0F - 42.5F, upright.project(0.0F, 2.0F, 0.0F).y(), 1.0E-4);

        VisualEditorProjection.Camera tilted = camera(0.0F, 45.0F);
        float cos = (float) Math.cos(Math.toRadians(45.0));
        assertEquals(50.0F + 42.5F, tilted.project(0.0F, 0.0F, 0.0F).y(), 1.0E-3);
        assertEquals(50.0F - 42.5F * (2.0F * cos - 1.0F), tilted.project(0.0F, 2.0F, 0.0F).y(), 1.0E-3);
        assertEquals(50.0F - 42.5F * (cos - 1.0F), tilted.project(0.0F, 1.0F, 0.0F).y(), 1.0E-3);
    }

    @Test
    void yawRotatesSidePointsIntoDepth() {
        VisualEditorProjection.Camera camera = camera(90.0F, 0.0F);
        assertEquals(50.0F, camera.project(1.0F, 1.0F, 0.0F).x(), 1.0E-4);
        assertEquals(7.5F, camera.project(0.0F, 1.0F, 1.0F).x(), 1.0E-4);
        assertEquals(7.5F, camera(0.0F, 0.0F).project(1.0F, 1.0F, 0.0F).x(), 1.0E-4);
    }

    @Test
    void layoutRotationAppliesDegreesXyz() {
        float[] forward = VisualEditorProjection.rotate(new VisualLayout.V(0.0F, 90.0F, 0.0F), 1.0F, 0.0F, 0.0F);
        assertEquals(0.0F, forward[0], 1.0E-5);
        assertEquals(-1.0F, forward[2], 1.0E-5);
        float[] normal = VisualEditorProjection.rotate(new VisualLayout.V(0.0F, 0.0F, 90.0F), 0.0F, 1.0F, 0.0F);
        assertEquals(-1.0F, normal[0], 1.0E-5);
        assertEquals(0.0F, normal[1], 1.0E-5);

        float[] combined = VisualEditorProjection.rotate(new VisualLayout.V(90.0F, 90.0F, 90.0F), 1.0F, 0.0F, 0.0F);
        assertEquals(0.0F, combined[0], 1.0E-5);
        assertEquals(0.0F, combined[1], 1.0E-5);
        assertEquals(1.0F, combined[2], 1.0E-5);
    }

    @Test
    void endpointExtendsAlongTheRotatedAxis() {
        VisualEditorProjection.Camera camera = camera(0.0F, 0.0F);
        VisualEditorProjection.Point start = camera.project(0.0F, 1.0F, 0.0F);
        VisualEditorProjection.Point end = VisualEditorProjection.endpoint(camera, 0.0F, 1.0F, 0.0F,
                1.0F, 0.0F, 0.0F, 0.5F);
        assertEquals(start.x() - 42.5F * 0.5F, end.x(), 1.0E-4);
        assertEquals(start.y(), end.y(), 1.0E-4);
    }

    /**
     * A reset two-layer preview has to keep both members inside the preview: the upper layer is
     * anchored on its own slot floor, so the whole composition stays within the body envelope. The
     * unanchored upper layer would fall below the cut and vanish.
     */
    @Test
    void resetTwoLayerCompositionProjectsInsideThePreviewRect() {
        VisualEditorView view = new VisualEditorView();
        VisualEditorView.Rect content = view.previewContent(427, 240);
        VisualEditorProjection.Frame frame = frame(content);
        float hostWidth = 0.9F;
        float hostHeight = 1.4F;
        VisualLayout layout = VisualLayout.reset(2);
        float[][] sizes = {{0.4F, 0.6F}, {hostWidth, hostHeight}};
        VisualEditorProjection.Box box = VisualEditorProjection.Box.of(0, 0, 0, 0, 0, 0);
        for (int i = 0; i < sizes.length; i++) {
            float floor = VisualGeometry.bandFloor(layout, i);
            AABB layer = VisualGeometry.layerBox(0.0, 0.0, 0.0, hostWidth, hostHeight,
                    sizes[i][0], sizes[i][1], layout.layers().get(i), floor);
            float[] band = layout.bounds(i);
            assertTrue(layer.minY >= band[0] * hostHeight - 1.0E-5, "layer " + i + " below its band");
            box = box.union(VisualEditorProjection.Box.of(layer.minX, layer.minY, layer.minZ,
                    layer.maxX, layer.maxY, layer.maxZ));
        }
        VisualEditorProjection.Camera camera = VisualEditorProjection.boxCamera(frame, box, hostWidth,
                hostHeight, 25.0F, 10.0F, 1.0F, VisualEditorView.PREVIEW_MARGIN);
        assertInside(frame, camera, box, 2.0F);
    }

    /**
     * Framing regression for the reported screenshot: the rendered chicken model is taller than its
     * bounding box, so a body-box or layer-box fit pushes its head above the preview. The measured
     * composition box keeps the whole head and both feet inside the content frame with real margin.
     */
    @Test
    void measuredCompositionKeepsHeadAndFeetInsideTheContentFrame() {
        VisualEditorView view = new VisualEditorView();
        VisualEditorView.Rect preview = view.preview(427, 240);
        VisualEditorView.Rect content = view.previewContent(427, 240);
        VisualEditorProjection.Frame contentFrame = frame(content);
        // Chicken head at 0.9375 over a 0.7 bounding box, lifted onto the 0.7 slot floor above a
        // 1.4 tall cow body; the same envelope covers the stretched legacy member.
        VisualEditorProjection.Box measured = VisualEditorProjection.Box.of(-0.45, 0.0, -0.45, 0.45, 1.6375, 0.45);
        VisualEditorProjection.Camera camera = VisualEditorProjection.boxCamera(contentFrame, measured,
                0.9F, 1.4F, 25.0F, 10.0F, 1.0F, VisualEditorView.PREVIEW_MARGIN);
        assertInside(contentFrame, camera, measured, 3.0F);

        VisualEditorProjection.Point head = camera.project(0.0F, 1.6375F, 0.0F);
        VisualEditorProjection.Point feet = camera.project(0.0F, 0.0F, 0.0F);
        assertTrue(head.y() > preview.y() + 13, "head clears the hint line: " + head.y());
        assertTrue(feet.y() < preview.bottom() - 20, "feet clear the toolbar: " + feet.y());
        assertTrue(preview.contains(head.x(), head.y()) && preview.contains(feet.x(), feet.y()));

        // The bounding-box fit used before this fix leaves the head above the preview top.
        VisualEditorProjection.Camera bodyOnly = VisualEditorProjection.camera(preview.x(), preview.y(),
                preview.right(), preview.bottom(), 0.9F, 1.4F, 25.0F, 10.0F, 1.0F);
        assertTrue(bodyOnly.project(0.0F, 1.6375F, 0.0F).y() < preview.y(),
                "documenting the reported clipping");
    }

    @Test
    void editorContentFrameExcludesHintLineAndToolbar() {
        VisualEditorView view = new VisualEditorView();
        VisualEditorView.Rect preview = view.preview(427, 240);
        VisualEditorView.Rect content = view.previewContent(427, 240);
        assertTrue(content.x() > preview.x());
        assertTrue(content.right() < preview.right());
        assertTrue(content.y() >= preview.y() + 14, "below the hint text at preview.y + 5");
        assertTrue(content.bottom() <= preview.bottom() - 20, "above the toolbar at bottom - 19");
        assertTrue(preview.contains(content.x(), content.y()));
        assertTrue(preview.contains(content.right(), content.bottom()));
        assertEquals(6, preview.x());
        assertEquals(26, preview.y());

        VisualEditorView.Rect small = view.previewContent(200, 120);
        assertTrue(small.w() >= 24 && small.h() >= 24);
    }

    @Test
    void userZoomScalesTheFittedCompositionWithoutChangingTheFrame() {
        VisualEditorView view = new VisualEditorView();
        VisualEditorView.Rect content = view.previewContent(427, 240);
        VisualEditorProjection.Frame frame = frame(content);
        VisualEditorProjection.Box measured = VisualEditorProjection.Box.of(-0.45, 0.0, -0.45, 0.45, 1.6375, 0.45);
        VisualEditorProjection.Camera base = VisualEditorProjection.boxCamera(frame, measured, 0.9F, 1.4F,
                25.0F, 10.0F, 1.0F, VisualEditorView.PREVIEW_MARGIN);
        VisualEditorProjection.Camera zoomed = VisualEditorProjection.boxCamera(frame, measured, 0.9F, 1.4F,
                25.0F, 10.0F, 2.0F, VisualEditorView.PREVIEW_MARGIN);
        assertEquals(base.scale() * 2.0F, zoomed.scale(), 1.0E-4);

        VisualEditorProjection.Point baseCenter = base.project(measured.centerX(), measured.centerY(),
                measured.centerZ());
        VisualEditorProjection.Point zoomedCenter = zoomed.project(measured.centerX(), measured.centerY(),
                measured.centerZ());
        assertEquals(baseCenter.x(), zoomedCenter.x(), 1.0E-3);
        assertEquals(baseCenter.y(), zoomedCenter.y(), 1.0E-3);
        assertEquals(frame.centerX(), zoomedCenter.x(), 1.0E-3);
        assertEquals(frame.centerY(), zoomedCenter.y(), 1.0E-3);
    }

    private static VisualEditorProjection.Frame frame(VisualEditorView.Rect rect) {
        return new VisualEditorProjection.Frame(rect.x(), rect.y(), rect.right(), rect.bottom());
    }

    private static void assertInside(VisualEditorProjection.Frame frame, VisualEditorProjection.Camera camera,
                                     VisualEditorProjection.Box box, float margin) {
        for (int corner = 0; corner < 8; corner++) {
            float x = (corner & 1) == 0 ? box.minX() : box.maxX();
            float y = (corner & 2) == 0 ? box.minY() : box.maxY();
            float z = (corner & 4) == 0 ? box.minZ() : box.maxZ();
            VisualEditorProjection.Point point = camera.project(x, y, z);
            assertTrue(point.x() >= frame.left() + margin - 1.0E-3F && point.x() <= frame.right() - margin + 1.0E-3F,
                    "x " + point.x());
            assertTrue(point.y() >= frame.top() + margin - 1.0E-3F && point.y() <= frame.bottom() - margin + 1.0E-3F,
                    "y " + point.y());
        }
    }
}
