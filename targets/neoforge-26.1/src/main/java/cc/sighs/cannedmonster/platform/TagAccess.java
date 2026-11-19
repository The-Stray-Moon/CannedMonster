package cc.sighs.cannedmonster.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.Set;

public final class TagAccess {
    private TagAccess() {}
    public static CompoundTag compound(CompoundTag tag, String key) { return tag.getCompoundOrEmpty(key); }
    public static CompoundTag compound(ListTag list, int index) { return list.getCompound(index).orElseThrow(); }
    public static ListTag list(CompoundTag tag, String key) { return tag.getListOrEmpty(key); }
    public static int integer(CompoundTag tag, String key) { return tag.getIntOr(key, 0); }
    public static String string(CompoundTag tag, String key) { return tag.getStringOr(key, ""); }
    public static double number(CompoundTag tag, String key) { return tag.getDoubleOr(key, 0.0); }
    public static Set<String> keys(CompoundTag tag) { return tag.keySet(); }
    public static String text(StringTag tag) { return tag.value(); }
    public static boolean hasId(CompoundTag tag) { return tag.getString("id").isPresent(); }
}
