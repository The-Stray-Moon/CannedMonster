package cc.sighs.cannedmonster.can;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class CanContents {
    public static final String KEY = "cannedmonster:contents";
    public static final int MAX_BYTES = 262144;

    private CanContents() {
    }

    private static CompoundTag contents(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getCompound(KEY) : new CompoundTag();
    }

    public static boolean filled(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(KEY, Tag.TAG_COMPOUND);
    }

    public static CompoundTag entity(ItemStack stack) {
        return contents(stack).getCompound("Entity").copy();
    }

    public static String name(ItemStack stack) {
        return contents(stack).getString("Name");
    }

    public static int count(ItemStack stack) {
        CompoundTag contents = contents(stack);
        if (contents.contains("Count")) return Math.max(1, contents.getInt("Count"));
        CompoundTag fusion = contents.getCompound("Entity").getCompound("cannedmonster:fusion");
        return 1 + (fusion.contains("Members", Tag.TAG_LIST)
                ? fusion.getList("Members", Tag.TAG_COMPOUND).size() : 0);
    }

    public static void store(ItemStack stack, CompoundTag entity, String name) {
        store(stack, entity, name, 1);
    }

    public static void store(ItemStack stack, CompoundTag entity, String name, int count) {
        write(stack, build(entity, name, count));
    }

    public static CompoundTag build(CompoundTag entity, String name, int count) {
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
        return contents;
    }

    public static void write(ItemStack stack, CompoundTag contents) {
        stack.getOrCreateTag().put(KEY, contents);
    }

    static void validate(Tag tag, int depth) {
        if (depth > 64 || tag.sizeInBytes() > MAX_BYTES) throw new IllegalArgumentException("Entity data exceeds can capacity");
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                validateText(key);
                validate(compound.get(key), depth + 1);
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (Tag child : list) validate(child, depth + 1);
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
        if (!stack.hasTag()) return;
        stack.getTag().remove(KEY);
        if (stack.getTag().isEmpty()) stack.setTag(null);
    }

    public static boolean valid(ItemStack stack) {
        CompoundTag contents = contents(stack);
        return contents.getInt("Version") == 1 && contents.getCompound("Entity").contains("id", Tag.TAG_STRING);
    }
}
