package cc.sighs.cannedmonster.can;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
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

    public static boolean filled(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(KEY);
    }

    public static CompoundTag entity(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(KEY).getCompound("Entity").copy();
    }

    public static String name(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(KEY).getString("Name");
    }

    public static void store(ItemStack stack, CompoundTag entity, String name) {
        CompoundTag contents = new CompoundTag();
        contents.putInt("Version", 1);
        contents.put("Entity", entity.copy());
        contents.putString("Name", name);
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

    static void validate(net.minecraft.nbt.Tag tag, int depth) {
        if (depth > 64 || tag.sizeInBytes() > MAX_BYTES) throw new IllegalArgumentException("Entity data exceeds can capacity");
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                validateText(key);
                validate(compound.get(key), depth + 1);
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (net.minecraft.nbt.Tag child : list) validate(child, depth + 1);
        } else if (tag instanceof net.minecraft.nbt.StringTag string) {
            validateText(string.getAsString());
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
        CompoundTag contents = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(KEY);
        return contents.getInt("Version") == 1 && contents.getCompound("Entity").contains("id", 8);
    }
}
