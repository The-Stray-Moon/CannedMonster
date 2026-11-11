package cc.sighs.cannedmonster.can;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanContentsTest {
    @Test
    void acceptsSmallEntityData() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:cow");
        tag.putFloat("Health", 7);
        assertDoesNotThrow(() -> CanContents.validate(tag, 0));
    }

    @Test
    void rejectsLargeArraysBeforeSerialization() {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("custom_mod_data", new byte[CanContents.MAX_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> CanContents.validate(tag, 0));
    }

    @Test
    void rejectsModifiedUtfOverflowEvenBelowCapacity() {
        CompoundTag tag = new CompoundTag();
        tag.putString("custom_text", "\u4e00".repeat(23000));
        assertTrue(tag.sizeInBytes() < CanContents.MAX_BYTES);
        assertThrows(IllegalArgumentException.class, () -> CanContents.validate(tag, 0));
    }

    @Test
    void rejectsDeepNesting() {
        CompoundTag root = new CompoundTag(), current = root;
        for (int i = 0; i < 70; i++) {
            CompoundTag child = new CompoundTag();
            current.put("nested", child);
            current = child;
        }
        assertThrows(IllegalArgumentException.class, () -> CanContents.validate(root, 0));
    }
}
