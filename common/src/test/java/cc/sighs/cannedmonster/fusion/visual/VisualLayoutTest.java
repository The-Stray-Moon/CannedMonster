package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VisualLayoutTest {
    @Test
    void resetProducesLegacyEqualHeightBandsWithNativeUnitScale() {
        VisualLayout layout = VisualLayout.reset(3);
        assertEquals(3, layout.layers().size());
        assertEquals(2, layout.planes().size());
        assertTrue(layout.layers().stream().allMatch(VisualLayout.Layer.IDENTITY::equals));
        for (VisualLayout.Layer layer : layout.layers()) {
            assertEquals(VisualLayout.V.ONE, layer.scale());
            assertEquals(VisualLayout.V.ZERO, layer.offset());
            assertEquals(VisualLayout.V.ZERO, layer.rotation());
        }
        assertEquals(2.0F / 3.0F, layout.planes().get(0).origin().y(), 1.0E-6);
        assertEquals(1.0F / 3.0F, layout.planes().get(1).origin().y(), 1.0E-6);
        for (VisualLayout.Plane plane : layout.planes()) {
            assertEquals(0.0F, plane.origin().x());
            assertEquals(0.0F, plane.origin().z());
            assertEquals(VisualLayout.V.ZERO, plane.rotation());
        }
    }

    @Test
    void resetHandlesEmptySingleAndOversizedCounts() {
        assertTrue(VisualLayout.reset(0).layers().isEmpty());
        assertTrue(VisualLayout.reset(0).planes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> VisualLayout.reset(-5));
        assertEquals(1, VisualLayout.reset(1).layers().size());
        assertTrue(VisualLayout.reset(1).planes().isEmpty());
        assertEquals(VisualLayout.MAX_LAYERS, VisualLayout.reset(VisualLayout.MAX_LAYERS + 100).layers().size());
    }

    @Test
    void legacyEncodesHostMemberFitRatiosInNativeScale() {
        VisualLayout legacy = VisualLayout.legacySizes(List.of(
                new VisualLayout.Size(1.0F, 2.0F),
                new VisualLayout.Size(4.0F, 8.0F),
                new VisualLayout.Size(4.0F, 8.0F)));
        assertEquals(3, legacy.layerCount());
        assertEquals(new VisualLayout.V(4.0F, 4.0F, 4.0F), legacy.layers().get(0).scale());
        assertEquals(new VisualLayout.V(0.0F, -2.0F / 3.0F, 0.0F), legacy.layers().get(0).offset());
        assertEquals(new VisualLayout.V(0.0F, -1.0F / 3.0F, 0.0F), legacy.layers().get(1).offset());
        assertEquals(VisualLayout.Layer.UNIT, legacy.layers().get(2));
        assertEquals(VisualLayout.V.ZERO, legacy.layers().get(0).rotation());
        assertEquals(VisualLayout.reset(3).planes(), legacy.planes());
        assertNotEquals(VisualLayout.reset(3).layers().get(0), legacy.layers().get(0));
    }

    @Test
    void legacyLayoutRequiresTheActualOrderedMembersWhenResolvingEntities() {
        assertEquals(VisualLayout.reset(0), VisualLayout.legacy(List.of()));
        assertThrows(NullPointerException.class, () -> VisualLayout.legacy(null));
        assertEquals(VisualLayout.reset(0), VisualLayout.legacySizes(List.of()));
    }

    @Test
    void saveAndLoadRoundTripTransforms() {
        VisualLayout layout = new VisualLayout(
                List.of(new VisualLayout.Layer(1.0F, true,
                                new VisualLayout.V(0.25F, -0.5F, 1.0F),
                                new VisualLayout.V(90.0F, 45.0F, 30.0F),
                                new VisualLayout.V(2.0F, 0.5F, -1.0F)),
                        VisualLayout.Layer.IDENTITY),
                List.of(new VisualLayout.Plane(new VisualLayout.V(-0.5F, 0.75F, 0.25F),
                        new VisualLayout.V(15.0F, -20.0F, 45.0F))));
        assertEquals(layout, VisualLayout.load(layout.save()));
        assertEquals(VisualLayout.reset(4), VisualLayout.load(VisualLayout.reset(4).save()));
    }

    @Test
    void loadRejectsCorruptRootData() {
        assertRejected(null);
        assertRejected(new CompoundTag());

        CompoundTag future = VisualLayout.reset(2).save();
        future.putInt("Version", VisualLayout.VERSION + 1);
        assertRejected(future);

        CompoundTag wrongType = VisualLayout.reset(2).save();
        wrongType.putString("Layers", "not a list");
        assertRejected(wrongType);

        CompoundTag noLayers = VisualLayout.reset(2).save();
        ((ListTag) noLayers.get("Layers")).clear();
        assertRejected(noLayers);
    }

    @Test
    void loadRejectsMalformedEntries() {
        CompoundTag nonFinite = VisualLayout.reset(2).save();
        CompoundTag layerEntry = (CompoundTag) ((ListTag) nonFinite.get("Layers")).get(0);
        ((CompoundTag) layerEntry.get("Offset")).putFloat("X", Float.NaN);
        assertRejected(nonFinite);

        CompoundTag notCompound = VisualLayout.reset(1).save();
        ListTag members = new ListTag();
        members.add(StringTag.valueOf("member"));
        notCompound.put("Layers", members);
        assertRejected(notCompound);

        CompoundTag badPlane = VisualLayout.reset(3).save();
        CompoundTag planeEntry = (CompoundTag) ((ListTag) badPlane.get("Planes")).get(1);
        ((CompoundTag) planeEntry.get("Origin")).putFloat("Y", Float.NaN);
        assertRejected(badPlane);
    }

    @Test
    void layoutListsAreImmutableSnapshots() {
        VisualLayout layout = VisualLayout.reset(2);
        assertThrows(UnsupportedOperationException.class, () -> layout.layers().add(VisualLayout.Layer.IDENTITY));
        assertThrows(UnsupportedOperationException.class,
                () -> layout.planes().add(new VisualLayout.Plane(VisualLayout.V.ZERO, VisualLayout.V.ZERO)));
    }

    @Test
    void transformsRejectNonFiniteValuesAndCollapsedScales() {
        assertThrows(IllegalArgumentException.class, () -> new VisualLayout.V(Float.NaN, 0.0F, 0.0F));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualLayout.Layer(new VisualLayout.V(0.0F, 0.0F, 0.0F), VisualLayout.V.ZERO,
                        new VisualLayout.V(0.0F, 1.0F, 1.0F)));
    }

    private static void assertRejected(CompoundTag tag) {
        try {
            assertNull(VisualLayout.load(tag));
        } catch (RuntimeException expected) {
            assertTrue(true);
        }
    }
}
