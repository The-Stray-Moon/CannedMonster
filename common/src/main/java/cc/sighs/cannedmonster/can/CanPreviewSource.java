package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.fusion.visual.VisualLayout;
import cc.sighs.cannedmonster.fusion.visual.VisualViews;
import cc.sighs.cannedmonster.platform.CanStorage;
import cc.sighs.cannedmonster.platform.EntityStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the client-safe part of a can preview: saved entity snapshots and the item-root layout.
 * No level, registry or renderer types are touched here so tooltip measurements can run before
 * a preview host is built.
 */
public final class CanPreviewSource {
    private CanPreviewSource() {}

    public static boolean has(ItemStack stack) {
        return !hosts(stack).isEmpty();
    }

    /** Cheap structural check used before the tooltip component measures a host. */
    public static boolean previewable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag contents = CanStorage.read(stack);
        if (!EntityStorage.string(contents, "Space").isEmpty()) return true;
        return saved(EntityStorage.compound(contents, "Entity"));
    }

    /**
     * Entity snapshots in render order: fusion members first and the body last. Spatial cans keep
     * the bounded LIFO snapshot written by the live space ledger, never a generated stand-in.
     * Malformed entries stay as empty placeholders so carousel positions never shift.
     */
    public static List<CompoundTag> hosts(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        if (CanContents.spaceId(stack) != null) return spaceHosts(stack);
        if (!CanContents.filled(stack) || !CanContents.valid(stack)) return List.of();
        CompoundTag entity = CanContents.entity(stack);
        return saved(entity) ? List.of(entity) : List.of();
    }

    public static VisualLayout layout(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = CanStorage.compound(stack, VisualViews.SAVE_KEY);
        return tag.isEmpty() ? null : VisualLayout.load(tag);
    }

    /** Selected carousel slot and actual resident total for the spatial tooltip label. */
    public record SpaceSelection(int selected, int total) {}

    public static SpaceSelection spaceSelection(ItemStack stack) {
        if (stack == null || stack.isEmpty() || CanContents.spaceId(stack) == null) return null;
        int previews = CanContents.spacePreviewCount(stack);
        if (previews <= 0) return null;
        int selected = Math.max(1, spatialIndex(stack) + 1);
        return new SpaceSelection(selected, Math.max(CanContents.spaceCount(stack), previews));
    }

    public static int spatialIndex(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        return CanPreviewSelection.index(spaceKey(stack), CanContents.spacePreviewCount(stack));
    }

    private static String spaceKey(ItemStack stack) {
        return String.valueOf(CanContents.spaceId(stack)) + ':' + CanContents.spacePreviewRevision(stack);
    }

    /** Individual layout embedded inside the stored can entity. */
    public static VisualLayout embedded(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !CanContents.valid(stack)) return null;
        CompoundTag tag = EntityStorage.compound(CanContents.entity(stack), VisualViews.SAVE_KEY);
        return tag.isEmpty() ? null : VisualLayout.load(tag);
    }

    private static List<CompoundTag> spaceHosts(ItemStack stack) {
        return spaceHosts(CanContents.spacePreview(stack));
    }

    static List<CompoundTag> spaceHosts(ListTag saved) {
        if (saved.isEmpty()) return List.of();
        int count = Math.min(saved.size(), CanContents.MAX_SPACE_PREVIEWS);
        List<CompoundTag> hosts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Tag child = saved.get(i);
            hosts.add(child instanceof CompoundTag tag ? SpacePreviewData.resident(tag) : new CompoundTag());
        }
        return List.copyOf(hosts);
    }

    private static boolean saved(CompoundTag tag) {
        return tag != null && !EntityStorage.string(tag, "id").isEmpty();
    }
}
