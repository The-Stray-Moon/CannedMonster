package cc.sighs.cannedmonster.fusion.visual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Resolution precedence for release and editor fallbacks. The pure selector is used by
 * {@code VisualLayoutResolver.applyFallback}, so these assertions cover the decision order without
 * requiring a live entity or a saved ItemStack.
 */
class VisualLayoutResolverTest {
    private static VisualLayout variant(int layers, float offset) {
        VisualLayout base = VisualLayout.defaults(layers);
        VisualLayout.Layer first = base.layer(0);
        VisualLayout.Layer edited = new VisualLayout.Layer(first.height(), first.visible(),
                new VisualLayout.V(offset, 0.0F, 0.0F), VisualLayout.V.ZERO, first.scale());
        return base.withLayer(0, edited);
    }

    @Test
    void releasePrecedenceIsIndividualItemPlayerServer() {
        VisualLayout individual = variant(2, 0.1F);
        VisualLayout item = variant(2, 0.2F);
        VisualLayout player = variant(2, 0.3F);
        VisualLayout server = variant(2, 0.4F);

        assertEquals(VisualLayoutResolver.Source.INDIVIDUAL,
                VisualLayoutResolver.select(individual, item, player, server, 2).source());
        assertEquals(VisualLayoutResolver.Source.ITEM,
                VisualLayoutResolver.select(null, item, player, server, 2).source());
        assertEquals(VisualLayoutResolver.Source.PLAYER,
                VisualLayoutResolver.select(null, null, player, server, 2).source());
        assertEquals(VisualLayoutResolver.Source.SERVER,
                VisualLayoutResolver.select(null, null, null, server, 2).source());
    }

    @Test
    void actorDefaultWinsOverServerDefault() {
        VisualLayout player = variant(4, 0.5F);
        VisualLayout server = variant(4, 0.6F);
        var resolved = VisualLayoutResolver.select(null, null, player, server, 4);
        assertSame(player, resolved.layout());
        assertEquals(VisualLayoutResolver.Source.PLAYER, resolved.source());
    }

    @Test
    void noActorStillFallsBackToServer() {
        VisualLayout server = variant(3, 0.75F);
        var resolved = VisualLayoutResolver.select(null, null, null, server, 3);
        assertSame(server, resolved.layout());
        assertEquals(VisualLayoutResolver.Source.SERVER, resolved.source());
    }

    @Test
    void presetsForAnotherLayerCountAreIgnored() {
        VisualLayout item = variant(2, 0.1F);
        VisualLayout player = variant(3, 0.2F);
        VisualLayout server = variant(4, 0.3F);
        var resolved = VisualLayoutResolver.select(null, item, player, server, 5);
        assertEquals(VisualLayoutResolver.Source.DEFAULT, resolved.source());
        assertEquals(VisualLayout.defaults(5), resolved.layout());

        assertEquals(VisualLayoutResolver.Source.ITEM,
                VisualLayoutResolver.select(null, item, player, server, 2).source());
        assertEquals(VisualLayoutResolver.Source.PLAYER,
                VisualLayoutResolver.select(null, null, player, server, 3).source());
        assertEquals(VisualLayoutResolver.Source.SERVER,
                VisualLayoutResolver.select(null, null, null, server, 4).source());
    }

    @Test
    void noStoredLayoutReportsIdentityFallbackForLegacyFit() {
        var resolved = VisualLayoutResolver.select(null, null, null, null, 3);
        assertEquals(VisualLayoutResolver.Source.DEFAULT, resolved.source());
        assertEquals(VisualLayout.defaults(3), resolved.layout());
        assertTrue(resolved.layout().layers().stream().allMatch(VisualLayout.Layer.IDENTITY::equals));
    }
}
