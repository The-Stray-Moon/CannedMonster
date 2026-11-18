package cc.sighs.cannedmonster.can;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class CanContents {
    public static final String KEY = "cannedmonster:contents";
    public static final int MAX_BYTES = 262144;

    private CanContents() {
    }

    private static CompoundTag root(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static boolean filled(ItemStack stack) {
        return root(stack).contains(KEY);
    }

    public static CompoundTag entity(ItemStack stack) {
        return root(stack).getCompoundOrEmpty(KEY).getCompoundOrEmpty("Entity").copy();
    }

    public static String name(ItemStack stack) {
        return root(stack).getCompoundOrEmpty(KEY).getString("Name").orElse("");
    }

    public static int count(ItemStack stack) {
        CompoundTag contents = root(stack).getCompoundOrEmpty(KEY);
        if (contents.contains("Count")) return Math.max(1, contents.getIntOr("Count", 1));
        CompoundTag fusion = contents.getCompoundOrEmpty("Entity").getCompoundOrEmpty("cannedmonster:fusion");
        return 1 + (fusion.contains("Members") ? fusion.getListOrEmpty("Members").size() : 0);
    }

    public static void store(ItemStack stack, CompoundTag entity, String name) {
        store(stack, entity, name, 1);
    }

    public static void store(ItemStack stack, CompoundTag entity, String name, int count) {
        CompoundTag contents = new CompoundTag();
        contents.putInt("Version", 1);
        contents.put("Entity", entity.copy());
        contents.putString("Name", name);
        contents.putInt("Count", Math.max(1, count));
        validate(contents, 0);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(contents, new DataOutputStream(bytes));
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Entity data exceeds can capacity");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot serialize can contents", exception);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(KEY, contents));
    }

    static void validate(Tag tag, int depth) {
        if (depth > 64 || tag.sizeInBytes() > MAX_BYTES) throw new IllegalArgumentException("Entity data exceeds can capacity");
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.keySet()) {
                validateText(key);
                validate(compound.get(key), depth + 1);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) validate(child, depth + 1);
        } else if (tag instanceof net.minecraft.nbt.StringTag string) {
            validateText(string.value());
        }
    }

    private static void validateText(String text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            bytes += value >= 1 && value <= 127 ? 1 : value > 2047 ? 3 : 2;
        }
        if (bytes > 65535) throw new IllegalArgumentException("Entity contains an oversized NBT string");
    }

    public static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY));
    }

    public static boolean valid(ItemStack stack) {
        CompoundTag contents = root(stack).getCompoundOrEmpty(KEY);
        return contents.getIntOr("Version", 0) == 1 && contents.getCompoundOrEmpty("Entity").getString("id").isPresent();
    }
}
