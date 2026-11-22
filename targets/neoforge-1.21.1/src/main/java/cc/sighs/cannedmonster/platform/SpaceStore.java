package cc.sighs.cannedmonster.platform;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.io.File;

public final class SpaceStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "cannedmonster_virtual_spaces";
    private static final SavedData.Factory<SpaceData> FACTORY = new SavedData.Factory<>(SpaceData::new, SpaceData::load, null);

    private SpaceStore() {}

    public static CompoundTag read(MinecraftServer server) {
        CompoundTag result = new CompoundTag();
        result.put("Spaces", server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME).tag().copy());
        return result;
    }

    public static void write(MinecraftServer server, CompoundTag tag) {
        SpaceData data = server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        data.tag = tag.getList("Spaces", Tag.TAG_COMPOUND).copy();
        data.setDirty();
    }

    public static void saveNow(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    public static final class SpaceData extends SavedData {
        private ListTag tag = new ListTag();
        private boolean readOnly;

        private SpaceData() {}

        private ListTag tag() {
            return tag;
        }

        public static SpaceData load(CompoundTag tag, HolderLookup.Provider provider) {
            SpaceData data = new SpaceData();
            data.tag = tag.getList("Spaces", Tag.TAG_COMPOUND).copy();
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            if (!readOnly) tag.put("Spaces", this.tag.copy());
            return tag;
        }

        @Override
        public void save(File file, HolderLookup.Provider provider) {
            if (!readOnly) super.save(file, provider);
        }
    }
}
