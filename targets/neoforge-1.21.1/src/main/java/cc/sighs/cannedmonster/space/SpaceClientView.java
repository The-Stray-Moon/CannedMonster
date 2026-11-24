package cc.sighs.cannedmonster.space;

import cc.sighs.cannedmonster.platform.SpaceRoomPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side isolated view of a virtual space.
 *
 * The view is an explicit session state machine keyed by (session, generation).
 * A payload is accepted only when its generation is newer than the last accepted
 * one, so an old active=true resync can never reopen a room after a newer close,
 * and a late packet from a previous session is ignored. Room activation does not
 * depend on the player's coordinates: the external anchor may legally sit inside
 * the local room bounds.
 *
 * The view owns its height profile, light values and biome while it is active.
 * Real chunks are never read through the view: FULL chunk reads are answered by
 * the synthetic room (or empty chunks). Real block and block-entity packets keep
 * updating the real cache directly, so the world is current when the view closes.
 */
public final class SpaceClientView {
    private static final RoomGeometry ROOM = SpaceManager.clientRoomGeometry();
    /** Fixed height profile used while the view is active; independent of the real level. */
    public static final int VIEW_MIN_Y = -64;
    public static final int VIEW_HEIGHT = 384;
    public static final int VIEW_MAX_Y = VIEW_MIN_Y + VIEW_HEIGHT;
    public static final int VIEW_SECTIONS = VIEW_HEIGHT / 16;
    public static final int VIEW_MIN_SECTION = VIEW_MIN_Y >> 4;

    private static Minecraft mc() { return Minecraft.getInstance(); }

    private static DimensionType viewTypeBase;
    private static DimensionType viewType;
    private static ClientLevel biomeLevel;
    private static Holder<Biome> viewBiome;

    private static boolean active;
    private static UUID session;
    private static long lastGeneration;
    private static ClientLevel level;
    private static ClientLevel lastSeenLevel;
    private static LevelChunk roomChunk;
    /** Depth counter: while non-zero, level height queries report the real dimension. */
    private static int realHeight;
    private static final Map<Long, LevelChunk> EMPTY = new HashMap<>();
    // Real updates for chunks that were not cached when they arrived. They are replayed
    // onto the real cache on deactivation; a chunk that loads later brings its own data.
    private static final Map<BlockPos, BlockState> PENDING = new HashMap<>();
    private static final Map<BlockPos, CompoundTag> PENDING_BE = new HashMap<>();

    private SpaceClientView() {}

    public static boolean active() { return active; }

    public static RoomGeometry room() { return ROOM; }

    public static UUID session() { return session; }

    public static long generation() { return lastGeneration; }

    public static int minY() { return VIEW_MIN_Y; }

    public static int sections() { return VIEW_SECTIONS; }

    /**
     * Real network data (chunk sections, biomes, light) must be decoded with the
     * real height profile even while the view is active. The view height is a
     * rendering/query view, not a different world format.
     */
    public static void pushRealHeight() { realHeight++; }

    public static void popRealHeight() { if (realHeight > 0) realHeight--; }

    public static boolean realHeight() { return realHeight > 0; }

    /** Fixed height profile derived from the real type but with a view-local range. */
    public static DimensionType viewDimensionType(DimensionType base) {
        if (viewType == null || !base.equals(viewTypeBase)) {
            viewTypeBase = base;
            viewType = new DimensionType(base.fixedTime(), base.hasSkyLight(), base.hasCeiling(), base.ultraWarm(),
                    base.natural(), base.coordinateScale(), base.bedWorks(), base.respawnAnchorWorks(),
                    VIEW_MIN_Y, VIEW_HEIGHT, VIEW_HEIGHT, base.infiniburn(), base.effectsLocation(),
                    base.ambientLight(), base.monsterSettings());
        }
        return viewType;
    }

    /** The room never samples the real biome, so fog and tint cannot leak in. */
    public static Holder<Biome> viewBiome() {
        ClientLevel current = mc().level;
        if (current == null) return null;
        if (current != biomeLevel || viewBiome == null) {
            biomeLevel = current;
            viewBiome = current.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        }
        return viewBiome;
    }

    public static void receive(SpaceRoomPayload payload) {
        if (payload.generation() <= lastGeneration) return;
        if (!payload.active()) {
            lastGeneration = payload.generation();
            deactivate();
            return;
        }
        ClientLevel current = mc().level;
        // Do not consume the generation while no world is available: the periodic
        // resync repeats the same generation once the level exists.
        if (current == null) return;
        if (active && current == level && payload.session().equals(session) && roomChunk != null) {
            lastGeneration = payload.generation();
            return;
        }
        long previous = lastGeneration;
        lastGeneration = payload.generation();
        // Mark active before construction so the height mixins report the fixed view
        // profile while the room chunk allocates its sections.
        active = true;
        level = current;
        lastSeenLevel = current;
        session = payload.session();
        LevelChunk chunk;
        try {
            chunk = buildRoom();
        } catch (RuntimeException failure) {
            lastGeneration = previous;
            active = false;
            session = null;
            level = null;
            roomChunk = null;
            return;
        }
        EMPTY.clear();
        PENDING.clear();
        PENDING_BE.clear();
        roomChunk = chunk;
        if (mc().levelRenderer != null) mc().levelRenderer.allChanged();
    }

    private static LevelChunk buildRoom() {
        LevelChunk chunk = new LevelChunk(level, new ChunkPos((int) ROOM.x() >> 4, (int) ROOM.z() >> 4));
        int baseX = (int) ROOM.x();
        int baseY = (int) ROOM.y();
        int baseZ = (int) ROOM.z();
        for (int lx = 0; lx < RoomGeometry.SIZE_X; lx++) {
            for (int ly = 0; ly < RoomGeometry.SIZE_Y; ly++) {
                int y = baseY + ly;
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                for (int lz = 0; lz < RoomGeometry.SIZE_Z; lz++) {
                    int z = baseZ + lz;
                    section.setBlockState((baseX + lx) & 15, y & 15, z & 15, ROOM.blockLocal(lx, ly, lz));
                }
            }
        }
        return chunk;
    }

    /** Close the view but keep the generation watermark so older resyncs stay ignored. */
    public static void deactivate() {
        boolean was = active;
        ClientLevel previous = level;
        active = false;
        session = null;
        level = null;
        roomChunk = null;
        EMPTY.clear();
        if (was) applyPending(previous);
        else { PENDING.clear(); PENDING_BE.clear(); }
        biomeLevel = null;
        viewBiome = null;
        if (was && mc().levelRenderer != null) mc().levelRenderer.allChanged();
    }

    /** World logout / level replacement: the next server generation may be lower. */
    public static void reset() {
        lastGeneration = 0;
        lastSeenLevel = mc().level;
        deactivate();
    }

    private static void applyPending(ClientLevel current) {
        if (current != null) {
            pushRealHeight();
            try {
                for (Map.Entry<BlockPos, BlockState> entry : PENDING.entrySet()) {
                    BlockPos pos = entry.getKey();
                    LevelChunk chunk = realChunk(current, pos);
                    if (chunk != null) chunk.setBlockState(pos, entry.getValue(), false);
                }
                for (Map.Entry<BlockPos, CompoundTag> entry : PENDING_BE.entrySet()) {
                    LevelChunk chunk = realChunk(current, entry.getKey());
                    if (chunk == null) continue;
                    BlockEntity be = chunk.getBlockEntity(entry.getKey());
                    if (be != null) be.loadWithComponents(entry.getValue(), current.registryAccess());
                }
            } finally {
                popRealHeight();
            }
        }
        PENDING.clear();
        PENDING_BE.clear();
    }

    private static LevelChunk realChunk(ClientLevel current, BlockPos pos) {
        return current.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
    }

    /**
     * Keep the real cache current while the room hides it. Applying the update to
     * the cached real chunk creates block entities and queues light like vanilla.
     * An uncached chunk needs no update: its next chunk packet is already current.
     */
    public static boolean applyBlock(BlockPos pos, BlockState state) {
        ClientLevel current = mc().level;
        if (current == null) return false;
        pushRealHeight();
        try {
            LevelChunk chunk = realChunk(current, pos);
            if (chunk == null) return false;
            chunk.setBlockState(pos, state, false);
        } finally {
            popRealHeight();
        }
        return true;
    }

    public static boolean applyBlockEntity(BlockPos pos, BlockEntityType<?> type, CompoundTag tag) {
        ClientLevel current = mc().level;
        if (current == null || tag == null) return false;
        pushRealHeight();
        try {
            LevelChunk chunk = realChunk(current, pos);
            if (chunk == null) return false;
            BlockEntity be = chunk.getBlockEntity(pos, type).orElse(null);
            if (be == null) return false;
            be.loadWithComponents(tag, current.registryAccess());
        } finally {
            popRealHeight();
        }
        return true;
    }

    public static void pending(BlockPos pos, BlockState state) {
        if (PENDING.size() < 8192) PENDING.put(pos.immutable(), state);
    }

    public static void pendingBlockEntity(BlockPos pos, CompoundTag tag) {
        if (PENDING_BE.size() < 2048) PENDING_BE.put(pos.immutable(), tag.copy());
    }

    /**
     * Level replacement (dimension change) clears the watermark; it is the only
     * coordinate-free reason to drop without a payload. Packet order on one
     * connection is preserved, so any later payload is newer.
     */
    public static void tick() {
        ClientLevel current = mc().level;
        if (current == lastSeenLevel) return;
        lastSeenLevel = current;
        lastGeneration = 0;
        if (active) deactivate();
    }

    public static LevelChunk chunk(int x, int z) {
        if (!active || level == null) return null;
        if (x == ((int) ROOM.x() >> 4) && z == ((int) ROOM.z() >> 4)) return roomChunk;
        return EMPTY.computeIfAbsent(ChunkPos.asLong(x, z), key -> new LevelChunk(level, new ChunkPos(x, z)));
    }

    public static boolean hasChunk(int x, int z) {
        if (!active || level == null) return false;
        if (x == ((int) ROOM.x() >> 4) && z == ((int) ROOM.z() >> 4)) return true;
        return EMPTY.containsKey(ChunkPos.asLong(x, z));
    }
}
