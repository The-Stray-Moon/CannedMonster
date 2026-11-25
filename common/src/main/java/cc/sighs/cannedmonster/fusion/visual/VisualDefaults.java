package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.platform.EntityStorage;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Persistent non-entity layout defaults.
 *
 * <p>The file keeps one exact layout per layer count for the server and one per player UUID and
 * layer count. Defaults are never resized: a layout saved for three layers is only used by three
 * layer bodies, so changing member composition cannot silently distort an administrator preset.
 */
public final class VisualDefaults {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "cannedmonster_layouts.dat";
    private static final String SERVER = "Server";
    private static final String PLAYERS = "Players";
    private static final Map<MinecraftServer, Store> CACHE = new WeakHashMap<>();

    private VisualDefaults() {}

    public static VisualLayout server(MinecraftServer server, int layers) {
        if (server == null || layers <= 0) return null;
        return read(store(server).tag, SERVER, Integer.toString(layers));
    }

    public static void writeServer(MinecraftServer server, int layers, VisualLayout layout) {
        if (server == null || layers <= 0 || layout == null) return;
        Store store = store(server);
        write(store.tag, SERVER, Integer.toString(layers), layout);
        save(server, store);
    }

    public static VisualLayout player(MinecraftServer server, UUID player, int layers) {
        if (server == null || player == null || layers <= 0) return null;
        return read(players(store(server).tag, player), Integer.toString(layers));
    }

    public static void writePlayer(MinecraftServer server, UUID player, int layers, VisualLayout layout) {
        if (server == null || player == null || layers <= 0 || layout == null) return;
        Store store = store(server);
        CompoundTag target = EntityStorage.compound(store.tag, PLAYERS);
        CompoundTag own = EntityStorage.compound(target, player.toString());
        own.put(Integer.toString(layers), layout.save());
        target.put(player.toString(), own);
        store.tag.put(PLAYERS, target);
        save(server, store);
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static Store store(MinecraftServer server) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(server, ignored -> new Store(readFile(server)));
        }
    }

    private static CompoundTag players(CompoundTag root, UUID player) {
        return EntityStorage.compound(EntityStorage.compound(root, PLAYERS), player.toString());
    }

    private static VisualLayout read(CompoundTag parent, String key) {
        if (!parent.contains(key)) return null;
        return VisualLayout.load(EntityStorage.compound(parent, key));
    }

    private static VisualLayout read(CompoundTag parent, String group, String key) {
        return read(EntityStorage.compound(parent, group), key);
    }

    private static void write(CompoundTag parent, String key, VisualLayout layout) {
        parent.put(key, layout.save());
    }

    private static void write(CompoundTag parent, String group, String key, VisualLayout layout) {
        CompoundTag target = EntityStorage.compound(parent, group);
        target.put(key, layout.save());
        parent.put(group, target);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
    }

    private static CompoundTag readFile(MinecraftServer server) {
        Path path = file(server);
        if (!Files.isRegularFile(path)) return new CompoundTag();
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            CompoundTag tag = NbtIo.read(input);
            return tag == null ? new CompoundTag() : tag;
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Cannot read visual layout defaults: {}", failure.toString());
            return new CompoundTag();
        }
    }

    private static void save(MinecraftServer server, Store store) {
        Path path = file(server);
        try {
            Files.createDirectories(path.getParent());
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
                NbtIo.write(store.tag, output);
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Cannot write visual layout defaults: {}", failure.toString());
        }
    }

    private record Store(CompoundTag tag) {}
}
