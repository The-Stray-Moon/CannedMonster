package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Editor interaction model: selection, axis steps, reset, dirty tracking and the save/ack state
 * machine. GUI adapters are not involved, so this runs without a game client.
 */
class VisualEditorStateTest {
    private static VisualEditorState state() {
        return new VisualEditorState(InteractionHand.MAIN_HAND, 7L, 3, VisualLayout.reset(3), true);
    }

    @Test
    void axisStepsEditOnlyTheSelectedTransform() {
        VisualEditorState state = state();
        state.adjust(0, 0, 1, false);
        state.adjust(1, 1, -1, true);
        state.adjust(2, 2, 1, true);
        VisualLayout.Layer layer = state.layout().layer(0);
        assertEquals(0.05F, layer.offset().x(), 1.0E-6);
        assertEquals(-15.0F, layer.rotation().y(), 1.0E-6);
        assertEquals(1.25F, layer.scale().z(), 1.0E-6);
        assertEquals(VisualLayout.Layer.UNIT, state.layout().layer(1));
        assertTrue(state.dirty());
    }

    @Test
    void resetClearsOnlyTheSelectedUnitThenEverything() {
        VisualEditorState state = state();
        state.adjust(0, 0, 1, true);
        state.adjust(1, 0, 1, true);
        state.resetUnit();
        assertEquals(VisualLayout.Layer.UNIT, state.layout().layer(0));
        assertFalse(state.dirty());
        state.resetAll();
        assertEquals(VisualLayout.reset(3), state.layout());
        assertFalse(state.dirty());
    }

    @Test
    void planeEditingKeepsPositionAndOrder() {
        VisualEditorState state = state();
        state.selectTab(VisualEditorState.Tab.PLANE);
        assertEquals(2, state.count());
        state.select(10);
        assertEquals(1, state.index());
        state.adjust(0, 1, 1, false);
        VisualLayout.Plane plane = state.layout().plane(1);
        assertEquals(plane.origin().y(), plane.position(), 1.0E-6);
        assertTrue(plane.origin().y() > 0.0F);
        state.resetUnit();
        assertEquals(0.0F, state.layout().plane(1).rotation().x(), 1.0E-6);
    }

    @Test
    void scaleAndRotationStayInRange() {
        VisualEditorState state = state();
        for (int i = 0; i < 200; i++) state.adjust(2, 0, 1, true);
        assertEquals(8.0F, state.layout().layer(0).scale().x(), 1.0E-6);
        for (int i = 0; i < 200; i++) state.adjust(2, 0, -1, true);
        float magnitude = Math.abs(state.layout().layer(0).scale().x());
        assertTrue(magnitude >= 0.05F && magnitude <= 8.0F);
        for (int i = 0; i < 100; i++) state.adjust(1, 2, 1, true);
        assertTrue(state.layout().layer(0).rotation().z() >= -180.0F
                && state.layout().layer(0).rotation().z() <= 180.0F);
    }

    @Test
    void successfulPersistentAckMarksTheSentSnapshotClean() {
        VisualEditorState state = state();
        state.adjust(0, 0, 1, false);
        state.beginSave(VisualEditorServer.SCOPE_INDIVIDUAL);
        assertTrue(state.saving());
        state.acknowledge(4, true, "saved");
        assertFalse(state.saving());
        assertEquals(4, state.revision());
        assertFalse(state.dirty());
        assertFalse(state.messageError());
        state.adjust(0, 0, -1, false);
        assertTrue(state.dirty());
    }

    @Test
    void playerDefaultAckAlsoMarksTheSnapshotClean() {
        VisualEditorState state = state();
        state.adjust(0, 0, 1, false);
        state.beginSave(VisualEditorServer.SCOPE_PLAYER);
        state.acknowledge(4, true, "saved");
        assertEquals(4, state.revision());
        assertFalse(state.dirty());
    }

    @Test
    void failedAckKeepsTheChangesAndReportsAnError() {
        VisualEditorState state = state();
        state.adjust(0, 0, 1, false);
        state.beginSave(VisualEditorServer.SCOPE_INDIVIDUAL);
        state.acknowledge(3, false, "nope");
        assertTrue(state.dirty());
        assertTrue(state.messageError());
        assertFalse(state.saving());
    }

    @Test
    void dirtyCloseAsksForConfirmationAndSaveMenuClosesFirst() {
        VisualEditorState state = state();
        assertFalse(state.confirming());
        state.adjust(0, 0, 1, false);
        state.openSaveMenu();
        assertTrue(state.saveMenu());
        state.requestClose();
        assertTrue(state.confirming());
        assertFalse(state.saveMenu());
        state.cancelClose();
        assertFalse(state.confirming());
        state.closeSaveMenu();
        assertFalse(state.saveMenu());
    }

    @Test
    void viewControlsClampZoomAndReset() {
        VisualEditorState state = state();
        state.zoomBy(10.0F);
        assertEquals(4.0F, state.zoom(), 1.0E-6);
        state.zoomBy(-10.0F);
        assertEquals(0.25F, state.zoom(), 1.0E-6);
        state.rotateBy(1000.0, 1000.0);
        assertEquals(89.0F, state.pitch(), 1.0E-6);
        state.resetView();
        assertEquals(25.0F, state.yaw(), 1.0E-6);
        assertEquals(10.0F, state.pitch(), 1.0E-6);
        assertEquals(1.0F, state.zoom(), 1.0E-6);
    }

    @Test
    void numericEntryAppliesOffsetRotationAndSingleAxisScale() {
        VisualEditorState state = state();
        type(state, 0, 0, "1.5");
        assertEquals(1.5F, state.layout().layer(0).offset().x(), 1.0E-5);

        type(state, 1, 1, "45");
        assertEquals(45.0F, state.layout().layer(0).rotation().y(), 1.0E-5);

        type(state, 2, 2, "2.25");
        assertEquals(2.25F, state.layout().layer(0).scale().z(), 1.0E-5);
        assertEquals(1.0F, state.layout().layer(0).scale().x(), 1.0E-5);
    }

    @Test
    void numericEntryCancelsWithoutChangingTheValueOnInvalidInput() {
        VisualEditorState state = state();
        state.beginEdit(0, 0);
        state.editChar('x');
        state.editChar('1');
        state.backspaceEdit();
        state.commitEdit();
        assertEquals(0.0F, state.layout().layer(0).offset().x(), 1.0E-6);
        assertFalse(state.editing());
    }

    @Test
    void proportionalScaleLockKeepsAxisRatios() {
        VisualEditorState state = state();
        type(state, 2, 0, "2");
        state.toggleScaleLock();
        type(state, 2, 1, "4");
        assertEquals(4.0F, state.layout().layer(0).scale().y(), 1.0E-5);
        assertEquals(8.0F, state.layout().layer(0).scale().x(), 1.0E-5);
        assertEquals(4.0F, state.layout().layer(0).scale().z(), 1.0E-5);

        state.adjust(2, 1, -1, true);
        assertEquals(3.75F, state.layout().layer(0).scale().y(), 1.0E-5);
        assertEquals(7.5F, state.layout().layer(0).scale().x(), 1.0E-5);
        assertEquals(3.75F, state.layout().layer(0).scale().z(), 1.0E-5);

        state.toggleScaleLock();
        type(state, 2, 2, "0.5");
        assertEquals(7.5F, state.layout().layer(0).scale().x(), 1.0E-5);
        assertEquals(3.75F, state.layout().layer(0).scale().y(), 1.0E-5);
        assertEquals(0.5F, state.layout().layer(0).scale().z(), 1.0E-5);
    }

    @Test
    void changingSelectionOrTabCancelsNumericEntry() {
        VisualEditorState state = state();
        state.beginEdit(0, 0);
        state.editChar('2');
        state.selectTab(VisualEditorState.Tab.PLANE);
        assertFalse(state.editing());
        assertEquals(0.0F, state.layout().layer(0).offset().x(), 1.0E-6);

        state.selectTab(VisualEditorState.Tab.LAYER);
        state.beginEdit(0, 1);
        state.editChar('3');
        state.select(1);
        assertFalse(state.editing());
        assertEquals(0.0F, state.layout().layer(1).offset().y(), 1.0E-6);
    }

    @Test
    void translationSetterClampsForHandleDrags() {
        VisualEditorState state = state();
        state.setTranslation(0, 100.0F);
        assertEquals(16.0F, state.translation(0), 1.0E-6);
        state.setTranslation(0, -100.0F);
        assertEquals(-16.0F, state.translation(0), 1.0E-6);
    }

    private static void type(VisualEditorState state, int group, int axis, String value) {
        state.beginEdit(group, axis);
        assertTrue(state.editing());
        for (int i = 0; i < value.length(); i++) state.editChar(value.charAt(i));
        state.commitEdit();
        assertFalse(state.editing());
    }
}
