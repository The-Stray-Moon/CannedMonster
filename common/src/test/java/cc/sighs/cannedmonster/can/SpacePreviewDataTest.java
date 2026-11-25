package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.platform.EntityStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpacePreviewDataTest {
    private static CompoundTag fusionMember(String id, int color, float health) {
        CompoundTag member = new CompoundTag();
        member.putString("id", id);
        member.putInt("Color", color);
        member.putFloat("Health", health);
        member.put("Inventory", new ListTag());
        return member;
    }

    @Test
    void appearanceDropsPrivateStateAndKeepsScalarAppearance() {
        CompoundTag saved = new CompoundTag();
        saved.putString("id", "minecraft:sheep");
        saved.putInt("Color", 2);
        saved.putBoolean("Sheared", true);
        saved.putFloat("Health", 99.0F);
        saved.putString("Owner", "player-secret");
        saved.putString("CustomName", "private name");
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(12.5));
        saved.put("Pos", pos);
        saved.put("UUID", new IntArrayTag(new int[]{1, 2, 3, 4}));
        saved.putString("LootTable", "minecraft:entities/sheep");
        saved.putBoolean("Saddle", true);
        saved.put("Inventory", new ListTag());
        saved.put("ArmorItems", new ListTag());
        saved.put("HandItems", new ListTag());
        saved.put("Offers", new CompoundTag());

        CompoundTag preview = SpacePreviewData.appearance(saved);
        assertEquals("minecraft:sheep", EntityStorage.string(preview, "id"));
        assertEquals(2, EntityStorage.integer(preview, "Color"));
        assertTrue(preview.contains("Sheared"));
        assertFalse(preview.contains("Health"));
        assertFalse(preview.contains("Owner"));
        assertFalse(preview.contains("CustomName"));
        assertFalse(preview.contains("Pos"));
        assertFalse(preview.contains("UUID"));
        assertFalse(preview.contains("LootTable"));
        assertFalse(preview.contains("Saddle"));
        assertFalse(preview.contains("Inventory"));
        assertFalse(preview.contains("ArmorItems"));
        assertFalse(preview.contains("HandItems"));
        assertFalse(preview.contains("Offers"));
    }

    @Test
    void fusionResidentKeepsOnlyItsOwnSanitizedMembers() {
        CompoundTag body = new CompoundTag();
        body.putString("id", "minecraft:cow");
        body.putFloat("Health", 20.0F);
        CompoundTag fusion = new CompoundTag();
        fusion.putInt("Version", 2);
        fusion.put("AttributeOrigins", new ListTag());
        ListTag members = new ListTag();
        members.add(fusionMember("minecraft:pig", 4, 8.0F));
        fusion.put("Members", members);
        body.put(SpacePreviewData.FUSION_KEY, fusion);

        CompoundTag resident = SpacePreviewData.resident(body);
        assertEquals("minecraft:cow", EntityStorage.string(resident, "id"));
        assertFalse(resident.contains("Health"));
        CompoundTag compact = EntityStorage.compound(resident, SpacePreviewData.FUSION_KEY);
        assertFalse(compact.isEmpty());
        assertFalse(compact.contains("AttributeOrigins"));
        ListTag kept = EntityStorage.list(compact, "Members");
        assertEquals(1, kept.size());
        CompoundTag member = (CompoundTag) kept.get(0);
        assertEquals("minecraft:pig", EntityStorage.string(member, "id"));
        assertEquals(4, EntityStorage.integer(member, "Color"));
        assertFalse(member.contains("Health"));
        assertFalse(member.contains("Inventory"));
    }

    @Test
    void truncatedFusionIsMarkedIncomplete() {
        CompoundTag body = new CompoundTag();
        body.putString("id", "minecraft:cow");
        CompoundTag fusion = new CompoundTag();
        fusion.putInt("Version", 2);
        ListTag members = new ListTag();
        for (int i = 0; i < 10; i++) members.add(fusionMember("minecraft:pig", i, 8.0F));
        fusion.put("Members", members);
        body.put(SpacePreviewData.FUSION_KEY, fusion);
        CompoundTag resident = SpacePreviewData.resident(body);
        CompoundTag compact = EntityStorage.compound(resident, SpacePreviewData.FUSION_KEY);
        assertTrue(EntityStorage.bool(compact, "Incomplete"));
        assertEquals(8, EntityStorage.list(compact, "Members").size());
    }

    @Test
    void nonFusionResidentHasNoFusionPayload() {
        CompoundTag body = new CompoundTag();
        body.putString("id", "minecraft:pig");
        CompoundTag preview = SpacePreviewData.resident(body);
        assertTrue(EntityStorage.compound(preview, SpacePreviewData.FUSION_KEY).isEmpty());
    }
}
