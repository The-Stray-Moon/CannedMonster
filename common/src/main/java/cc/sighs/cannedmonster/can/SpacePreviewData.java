package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.platform.EntityStorage;
import cc.sighs.cannedmonster.platform.TagAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

/**
 * Reduces a saved resident to the fields a renderer can use. Compound/list/array values (inventories,
 * equipment, positions, brain memories, UUID arrays and loot tables) are never copied, and volatile
 * state fields are omitted even when scalar. Fusion residents keep only their own sanitized member
 * stack so one preview unit is never composed from unrelated residents.
 */
public final class SpacePreviewData {
    public static final String FUSION_KEY = "cannedmonster:fusion";
    private static final int MAX_FUSION_MEMBERS = 8;
    private static final Set<String> OMITTED = Set.of(
            "UUID", "Pos", "Motion", "Rotation", "FallDistance", "Health", "AbsorptionAmount",
            "HurtTime", "HurtByTimestamp", "DeathTime", "Air", "Fire", "PortalCooldown",
            "Invulnerable", "PersistenceRequired", "CanPickUpLoot", "NoAI", "Silent", "NoGravity",
            "Glowing", "Team", "Leashed", "Leash", "LootTable", "LootTableSeed", "Owner",
            "Saddle", "SaddleItem", "ArmorItem", "DecorItem", "body_armor_item",
            "CustomName", "CustomNameVisible", "Tags", "Passengers", "RootVehicle", "attributes",
            "Attributes", "ActiveEffects", "Inventory", "ArmorItems", "HandItems", "Items",
            "Offers", "Trades", "Brain", "memories", "Equipment", "equipment", "DropChances",
            "ArmorDropChances", "HandDropChances", "drop_chances");

    private SpacePreviewData() {}

    public static CompoundTag resident(CompoundTag saved) {
        CompoundTag preview = appearance(saved);
        CompoundTag fusion = EntityStorage.compound(saved, FUSION_KEY);
        boolean fusionPresent = !fusion.isEmpty();
        if (fusionPresent) {
            ListTag savedMembers = EntityStorage.list(fusion, "Members");
            ListTag members = new ListTag();
            CompoundTag compact = new CompoundTag();
            compact.putInt("Version", 2);
            compact.put("Members", members);
            boolean incomplete = EntityStorage.bool(fusion, "Incomplete") || savedMembers.size() > MAX_FUSION_MEMBERS;
            preview.put(FUSION_KEY, compact);
            for (Tag child : savedMembers) {
                if (members.size() >= MAX_FUSION_MEMBERS) {
                    incomplete = true;
                    break;
                }
                if (!(child instanceof CompoundTag member)) {
                    incomplete = true;
                    continue;
                }
                CompoundTag sanitized = appearance(member);
                if (EntityStorage.string(sanitized, "id").isEmpty()) {
                    incomplete = true;
                    continue;
                }
                members.add(sanitized);
                if (preview.sizeInBytes() > CanContents.MAX_SPACE_PREVIEW_ENTRY_BYTES) {
                    members.remove(members.size() - 1);
                    incomplete = true;
                    break;
                }
            }
            if (incomplete) compact.putBoolean("Incomplete", true);
            if (members.isEmpty() && !incomplete) preview.remove(FUSION_KEY);
        }
        if (preview.sizeInBytes() > CanContents.MAX_SPACE_PREVIEW_ENTRY_BYTES) {
            if (fusionPresent) return new CompoundTag();
            CompoundTag idOnly = new CompoundTag();
            String id = EntityStorage.string(preview, "id");
            if (!id.isEmpty()) idOnly.putString("id", id);
            return idOnly;
        }
        return preview;
    }

    public static CompoundTag appearance(CompoundTag saved) {
        CompoundTag preview = new CompoundTag();
        if (saved == null) return preview;
        String id = EntityStorage.string(saved, "id");
        if (!id.isEmpty()) preview.putString("id", id);
        for (String key : TagAccess.keys(saved)) {
            if (key.equals("id") || OMITTED.contains(key)) continue;
            Tag value = saved.get(key);
            if (value instanceof NumericTag || value instanceof StringTag) preview.put(key, value.copy());
        }
        return preview;
    }
}
