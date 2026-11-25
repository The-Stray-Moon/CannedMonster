package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.platform.EntityStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanPreviewSourceTest {
    private static CompoundTag entity(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putFloat("Health", 10.0F);
        return tag;
    }

    @Test
    void savedResidentsBecomePreviewHostsInStoredOrder() {
        ListTag residents = new ListTag();
        residents.add(entity("minecraft:pig"));
        residents.add(entity("minecraft:sheep"));
        var hosts = CanPreviewSource.spaceHosts(residents);
        assertEquals(2, hosts.size());
        assertEquals("minecraft:pig", EntityStorage.string(hosts.get(0), "id"));
        assertEquals("minecraft:sheep", EntityStorage.string(hosts.get(1), "id"));
    }

    @Test
    void spacePreviewIsBounded() {
        ListTag residents = new ListTag();
        for (int i = 0; i < CanContents.MAX_SPACE_PREVIEWS + 2; i++) residents.add(entity("minecraft:pig"));
        assertEquals(CanContents.MAX_SPACE_PREVIEWS, CanPreviewSource.spaceHosts(residents).size());
        assertEquals(CanContents.MAX_SPACE_PREVIEWS, CanContents.boundedPreview(residents).size());
    }

    @Test
    void oversizedResidentKeepsItsPositionAsAPlaceholder() {
        ListTag residents = new ListTag();
        CompoundTag oversized = entity("minecraft:pig");
        oversized.putString("Blob", "x".repeat(CanContents.MAX_SPACE_PREVIEW_ENTRY_BYTES + 1));
        residents.add(oversized);
        residents.add(entity("minecraft:cow"));
        ListTag bounded = CanContents.boundedPreview(residents);
        assertEquals(2, bounded.size());
        assertEquals("", EntityStorage.string((CompoundTag) bounded.get(0), "id"));
        assertEquals("minecraft:cow", EntityStorage.string((CompoundTag) bounded.get(1), "id"));
    }

    @Test
    void malformedResidentKeepsItsCarouselSlot() {
        ListTag residents = new ListTag();
        residents.add(new CompoundTag());
        residents.add(entity("minecraft:cow"));
        var hosts = CanPreviewSource.spaceHosts(residents);
        assertEquals(2, hosts.size());
        assertEquals("", EntityStorage.string(hosts.get(0), "id"));
        assertEquals("minecraft:cow", EntityStorage.string(hosts.get(1), "id"));
    }

    @Test
    void storedPreviewIsSanitizedBeforeItReachesTheStack() {
        ListTag residents = new ListTag();
        CompoundTag raw = entity("minecraft:cow");
        raw.put("Inventory", new ListTag());
        residents.add(raw);
        ListTag bounded = CanContents.boundedPreview(residents);
        assertEquals(1, bounded.size());
        CompoundTag preview = (CompoundTag) bounded.get(0);
        assertEquals("minecraft:cow", EntityStorage.string(preview, "id"));
        assertFalse(preview.contains("Health"));
        assertFalse(preview.contains("Inventory"));
        assertFalse(preview.contains("Pos"));
    }

    @Test
    void carouselStartsWithNewestAndAdvancesMonotonically() {
        assertEquals(0, CanPreviewSelection.index(2, 0L));
        assertEquals(0, CanPreviewSelection.index(2, CanPreviewSelection.ROTATE_MILLIS - 1L));
        assertEquals(1, CanPreviewSelection.index(2, CanPreviewSelection.ROTATE_MILLIS));
        assertEquals(1, CanPreviewSelection.index(2, CanPreviewSelection.ROTATE_MILLIS * 2L - 1L));
        assertEquals(0, CanPreviewSelection.index(2, CanPreviewSelection.ROTATE_MILLIS * 2L));
        assertEquals(15000L, CanPreviewSelection.ROTATE_MILLIS);
        assertEquals(-1, CanPreviewSelection.index(0));
    }

    @Test
    void keyedCarouselAnchorsAtTheNewestSlot() {
        assertEquals(0, CanPreviewSelection.index("space:test", 2, 1000L));
        assertEquals(0, CanPreviewSelection.index("space:test", 2, 1000L + CanPreviewSelection.ROTATE_MILLIS - 1L));
        assertEquals(1, CanPreviewSelection.index("space:test", 2, 1000L + CanPreviewSelection.ROTATE_MILLIS));
        assertEquals(0, CanPreviewSelection.index("other:test", 2, 16000L));
    }

    @Test
    void idleCarouselRestartsAtTheNewestSlot() {
        assertEquals(0, CanPreviewSelection.index("space:idle", 2, 0L));
        assertEquals(1, CanPreviewSelection.index("space:idle", 2, CanPreviewSelection.ROTATE_MILLIS));
        assertEquals(0, CanPreviewSelection.index("space:idle", 2, CanPreviewSelection.ROTATE_MILLIS * 4L));
    }
}
