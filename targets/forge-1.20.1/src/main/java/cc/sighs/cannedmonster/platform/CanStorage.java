package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.can.CanContents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class CanStorage {
    private CanStorage() {}

    public static CompoundTag read(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getCompound(CanContents.KEY) : new CompoundTag();
    }

    public static boolean filled(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(CanContents.KEY, Tag.TAG_COMPOUND);
    }

    public static void write(ItemStack stack, CompoundTag contents) {
        stack.getOrCreateTag().put(CanContents.KEY, contents);
    }

    public static void clear(ItemStack stack) {
        if (!stack.hasTag()) return;
        stack.getTag().remove(CanContents.KEY);
        if (stack.getTag().isEmpty()) stack.setTag(null);
    }

    public static CompoundTag compound(ItemStack stack, String key) {
        return stack.hasTag() ? stack.getTag().getCompound(key) : new CompoundTag();
    }

    public static void put(ItemStack stack, String key, CompoundTag value) {
        stack.getOrCreateTag().put(key, value.copy());
    }

    public static void remove(ItemStack stack, String key) {
        if (!stack.hasTag()) return;
        stack.getTag().remove(key);
        if (stack.getTag().isEmpty()) stack.setTag(null);
    }

    public static String text(StringTag tag) {
        return tag.getAsString();
    }

    public static boolean hasId(CompoundTag tag) {
        return tag.contains("id", Tag.TAG_STRING);
    }
}
