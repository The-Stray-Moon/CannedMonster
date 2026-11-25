package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GUI-independent checks for the editor hotspot wiring. A fake canvas supplies font metrics, so
 * the real layout math decides where buttons land; no game client is required.
 */
class VisualEditorViewTest {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 270;

    private static final class Canvas implements EditorCanvas {
        @Override
        public void fill(int x0, int y0, int x1, int y1, int color) {
        }

        @Override
        public void text(String text, int x, int y, int color, boolean shadow) {
        }

        @Override
        public int width(String text) {
            return text == null ? 0 : text.length() * 6;
        }
    }

    private static VisualEditorState state() {
        return new VisualEditorState(InteractionHand.MAIN_HAND, 11L, 1, VisualLayout.reset(3), true);
    }

    private static void draw(VisualEditorView view, VisualEditorState state) {
        view.draw(new Canvas(), state, WIDTH, HEIGHT, 0, 0);
    }

    @Test
    void saveButtonOpensAndCancelClosesTheMenu() {
        VisualEditorState state = state();
        VisualEditorView view = new VisualEditorView();
        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 300, 238, false));
        assertTrue(state.saveMenu());
        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 200, 178, false));
        assertFalse(state.saveMenu());
    }

    @Test
    void clickingOutsideTheMenuKeepsTheEditorOpen() {
        VisualEditorState state = state();
        VisualEditorView view = new VisualEditorView();
        state.openSaveMenu();
        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 20, 40, false));
        assertFalse(state.saveMenu());
    }

    @Test
    void planeTabAndListRowsSelectTheRightElement() {
        VisualEditorState state = state();
        VisualEditorView view = new VisualEditorView();
        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 400, 35, false));
        assertEquals(VisualEditorState.Tab.PLANE, state.tab());
        assertEquals(0, state.index());
        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 300, 52, false));
        assertEquals(0, state.index());
    }

    @Test
    void cleanCloseClosesImmediately() {
        VisualEditorState state = state();
        VisualEditorView view = new VisualEditorView();
        draw(view, state);
        assertEquals(VisualEditorView.Action.CLOSE, view.click(state, 430, 255, false));
        assertFalse(state.confirming());
    }

    @Test
    void dirtyCloseRequiresDiscardConfirmation() {
        VisualEditorState state = state();
        VisualEditorView view = new VisualEditorView();
        state.adjust(0, 0, 1, false);
        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 430, 255, false));
        assertTrue(state.confirming());
        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 300, 150, false));
        assertFalse(state.confirming());
        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 430, 255, false));
        assertTrue(state.confirming());
        draw(view, state);
        assertEquals(VisualEditorView.Action.CLOSE, view.click(state, 180, 150, false));
    }

    @Test
    void numericFieldStartsDirectEntryAndScaleLockToggles() {
        VisualEditorState state = state();
        VisualEditorView view = new VisualEditorView();
        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 330, 170, false));
        assertTrue(state.editing());
        assertEquals(0, state.editGroup());
        assertEquals(0, state.editAxis());
        state.editChar('2');
        state.commitEdit();
        assertEquals(2.0F, state.layout().layer(0).offset().x(), 1.0E-5);

        draw(view, state);
        assertEquals(VisualEditorView.Action.NONE, view.click(state, 440, 148, false));
        assertTrue(state.scaleLock());
    }

    @Test
    void axisHandleDragMovesTheSelectedLayer() {
        VisualEditorState state = state();
        VisualEditorView view = new VisualEditorView();
        VisualEditorView.Rect content = view.previewContent(WIDTH, HEIGHT);
        VisualEditorProjection.Camera camera = VisualEditorProjection.camera(content.x(), content.y(),
                content.right(), content.bottom(), 2.0F, 2.0F, state.yaw(), state.pitch(), state.zoom());
        VisualEditorView.PreviewInfo info = new VisualEditorView.PreviewInfo(2.0F, 2.0F, camera);
        view.draw(new Canvas(), state, WIDTH, HEIGHT, 0, 0, info);
        float anchorY = VisualGeometry.bandFloor(state.layout(), 0) * 2.0F;
        VisualEditorProjection.Point tip = VisualEditorProjection.endpoint(camera, 0.0F, anchorY, 0.0F,
                1.0F, 0.0F, 0.0F, 0.6F);
        VisualEditorView.Drag drag = view.beginDrag(state, tip.x(), tip.y(), WIDTH, HEIGHT);
        assertEquals(VisualEditorView.Drag.AXIS_X, drag);
        view.drag(state, tip.x() + 20, tip.y());
        assertNotEquals(0.0F, state.layout().layer(0).offset().x());
        view.endDrag();
        assertEquals(VisualEditorView.Drag.NONE, view.dragMode());
    }

    @Test
    void planeTabShowsGuidesWithoutBreakingPreviewDrag() {
        VisualEditorState state = state();
        VisualEditorView view = new VisualEditorView();
        VisualEditorView.Rect content = view.previewContent(WIDTH, HEIGHT);
        VisualEditorView.PreviewInfo info = new VisualEditorView.PreviewInfo(2.0F, 2.0F,
                VisualEditorProjection.camera(content.x(), content.y(), content.right(), content.bottom(),
                        2.0F, 2.0F, state.yaw(), state.pitch(), state.zoom()));
        state.selectTab(VisualEditorState.Tab.PLANE);
        view.draw(new Canvas(), state, WIDTH, HEIGHT, 0, 0, info);
        VisualEditorView.Drag drag = view.beginDrag(state, 100.0, 100.0, WIDTH, HEIGHT);
        assertNotEquals(VisualEditorView.Drag.NONE, drag);
        view.endDrag();
    }

    @Test
    void previewDragStartsOnlyInsideThePreview() {
        VisualEditorState state = state();
        VisualEditorView view = new VisualEditorView();
        VisualEditorView.Rect preview = view.preview(WIDTH, HEIGHT);
        assertTrue(preview.contains(preview.x() + 10, preview.y() + 10));
        assertTrue(view.mouseOverPreview(preview.x() + 10, preview.y() + 10, WIDTH, HEIGHT));
        assertFalse(view.mouseOverPreview(preview.right() + 5, preview.y() + 10, WIDTH, HEIGHT));
    }
}
