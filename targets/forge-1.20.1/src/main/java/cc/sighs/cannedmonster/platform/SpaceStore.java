package cc.sighs.cannedmonster.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** Virtual-space persistence is only active on NeoForge 1.21.1. */
public final class SpaceStore {
    private SpaceStore() {}

    public static CompoundTag read(MinecraftServer server) {
        return new CompoundTag();
    }

    public static void write(MinecraftServer server, CompoundTag tag) {
    }

    public static void saveNow(MinecraftServer server) {
    }
}
