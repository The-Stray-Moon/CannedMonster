package cc.sighs.cannedmonster.fusion.visual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Composition changes used by fusion prepend/peel: the stack grows and shrinks at its top, the
 * remaining layers keep their custom values and the result stays serialisable.
 */
class VisualLayoutCompositionTest {
    @Test
    void peelDropsOnlyTheTopLayer() {
        VisualLayout layout = VisualLayout.reset(3)
                .withLayer(0, new VisualLayout.Layer(2.0F, true))
                .withLayer(2, new VisualLayout.Layer(0.5F, false));
        VisualLayout peeled = layout.resized(2);
        assertEquals(2, peeled.layerCount());
        assertEquals(new VisualLayout.Layer(0.5F, false), peeled.layers().get(1));
        assertNotEquals(layout.layers().get(0), peeled.layers().get(0));
    }

    @Test
    void prependInsertsIdentityLayersAtTheTop() {
        VisualLayout host = VisualLayout.reset(2).withLayer(1, new VisualLayout.Layer(0.25F, true));
        VisualLayout combined = host.resized(4);
        assertEquals(4, combined.layerCount());
        assertEquals(VisualLayout.Layer.UNIT, combined.layers().get(0));
        assertEquals(VisualLayout.Layer.UNIT, combined.layers().get(1));
        assertEquals(new VisualLayout.Layer(0.25F, true), combined.layers().get(3));
    }

    @Test
    void joinedCompositionKeepsEveryLayerAndRemainsSerialisable() {
        VisualLayout incoming = VisualLayout.reset(2);
        VisualLayout host = VisualLayout.reset(3);
        VisualLayout joined = VisualLayout.join(incoming, host);
        assertEquals(5, joined.layerCount());
        assertEquals(incoming.layers().get(0), joined.layers().get(0));
        assertEquals(host.layers().get(2), joined.layers().get(4));
        VisualLayout normalised = new VisualLayout(joined.layers(), VisualLayout.defaults(joined.layerCount()).planes());
        VisualLayout restored = VisualLayout.load(normalised.save());
        assertEquals(normalised, restored);
    }

    @Test
    void emptyLayoutsDoNotParticipateInComposition() {
        VisualLayout empty = VisualLayout.reset(0);
        assertEquals(0, empty.layerCount());
        assertEquals(List.of(), empty.planes());
        assertEquals(VisualLayout.defaults(2), VisualLayout.join(empty, VisualLayout.defaults(2)));
    }
}
