package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.can.CanContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class CanStorage {
    private CanStorage() {}

    public static CompoundTag read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(CanContents.KEY);
    }

    public static boolean filled(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(CanContents.KEY);
    }

    public static void write(ItemStack stack, CompoundTag contents) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(CanContents.KEY, contents));
    }

    public static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(CanContents.KEY));
    }

    public static String text(StringTag tag) {
        return tag.getAsString();
    }

    public static boolean hasId(CompoundTag tag) {
        return tag.contains("id", Tag.TAG_STRING);
    }
}
