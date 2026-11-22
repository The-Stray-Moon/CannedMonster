package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.fusion.FusionRuntime;
import cc.sighs.cannedmonster.platform.TagAccess;
import cc.sighs.cannedmonster.platform.CanStorage;
import cc.sighs.cannedmonster.platform.EntityStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public final class CanContents {
    public static final String KEY = "cannedmonster:contents";
    public static final int MAX_BYTES = 262144;

    private CanContents() {}

    public static boolean filled(ItemStack stack) {
        return CanStorage.filled(stack);
    }

    public static CompoundTag entity(ItemStack stack) {
        return EntityStorage.compound(CanStorage.read(stack), "Entity").copy();
    }

    public static String name(ItemStack stack) {
        return EntityStorage.string(CanStorage.read(stack), "Name");
    }

    public static int count(ItemStack stack) {
        CompoundTag contents = CanStorage.read(stack);
        if (contents.contains("Count")) return Math.max(1, EntityStorage.integer(contents, "Count"));
        return FusionRuntime.memberCount(EntityStorage.compound(contents, "Entity"));
    }

    public static int spaceCount(ItemStack stack) {
        return Math.max(0, EntityStorage.integer(CanStorage.read(stack), "Count"));
    }

    public static UUID spaceId(ItemStack stack) {
        String raw = EntityStorage.string(CanStorage.read(stack), "Space");
        if (raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Drop a space reference that was never committed (failed entry). */
    public static void clearSpace(ItemStack stack) {
        CompoundTag contents = CanStorage.read(stack);
        if (contents.contains("Entity")) return;
        CanStorage.clear(stack);
    }

    public static void storeSpace(ItemStack stack, UUID space, int residents) {
        CompoundTag contents = new CompoundTag();
        contents.putInt("Version", 1);
        contents.putString("Space", space.toString());
        contents.putInt("Count", Math.max(0, residents));
        validate(contents, 0);
        write(stack, contents);
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
        CanStorage.write(stack, contents);
    }

    static void validate(net.minecraft.nbt.Tag tag, int depth) {
        if (depth > 64 || tag.sizeInBytes() > MAX_BYTES) throw new IllegalArgumentException("Entity data exceeds can capacity");
        if (tag instanceof CompoundTag compound) {
            for (String key : TagAccess.keys(compound)) {
                validateText(key);
                validate(compound.get(key), depth + 1);
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (net.minecraft.nbt.Tag child : list) validate(child, depth + 1);
        } else if (tag instanceof net.minecraft.nbt.StringTag string) {
            validateText(CanStorage.text(string));
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
        CanStorage.clear(stack);
    }

    public static boolean valid(ItemStack stack) {
        CompoundTag contents = CanStorage.read(stack);
        return EntityStorage.integer(contents, "Version") == 1 && CanStorage.hasId(EntityStorage.compound(contents, "Entity"));
    }
}
