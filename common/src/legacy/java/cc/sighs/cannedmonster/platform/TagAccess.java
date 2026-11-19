package cc.sighs.cannedmonster.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

public final class TagAccess {
    private TagAccess() {}
    public static CompoundTag compound(CompoundTag tag, String key) { return tag.getCompound(key); }
    public static CompoundTag compound(ListTag list, int index) { return list.getCompound(index); }
    public static ListTag list(CompoundTag tag, String key) { return tag.getList(key, Tag.TAG_COMPOUND); }
    public static int integer(CompoundTag tag, String key) { return tag.getInt(key); }
    public static String string(CompoundTag tag, String key) { return tag.getString(key); }
    public static double number(CompoundTag tag, String key) { return tag.getDouble(key); }
    public static Set<String> keys(CompoundTag tag) { return tag.getAllKeys(); }
    public static String text(StringTag tag) { return tag.getAsString(); }
    public static boolean hasId(CompoundTag tag) { return tag.contains("id", Tag.TAG_STRING); }
}
