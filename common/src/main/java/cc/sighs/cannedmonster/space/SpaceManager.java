package cc.sighs.cannedmonster.space;

import cc.sighs.cannedmonster.can.CanContents;
import cc.sighs.cannedmonster.platform.EntityStorage;
import cc.sighs.cannedmonster.platform.PlayerAccess;
import cc.sighs.cannedmonster.platform.SpacePlatform;
import cc.sighs.cannedmonster.platform.SpaceStore;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minimal true dual-coordinate space runtime.
 *
 * Server side: a virtual room at an internal coordinate (no blocks are written),
 * authoritative virtual block/collision queries, real resident entities and an
 * external can ItemEntity at the original position.
 * Client side: a synthetic room chunk at a different coordinate and translated
 * position/entity packets, so the client actually renders the room.
 */
public final class SpaceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Space> SPACES = new HashMap<>();
    private static final Map<UUID, Space> PLAYERS = new HashMap<>();
    private static final Map<UUID, Space> RESIDENTS = new HashMap<>();
    private static final Map<UUID, Space> DERIVED = new HashMap<>();
    private static final Map<UUID, Space> QUARANTINED = new HashMap<>();
    private static final Map<UUID, Space> SAVED_PLAYERS = new HashMap<>();
    private static final Map<UUID, Integer> SYNC = new HashMap<>();
    private static final Map<UUID, Integer> RESYNC_AT = new HashMap<>();
    private static final ListTag UNPARSED = new ListTag();
    private static int tickCounter;
    private static boolean stopped;

    public record ContainerCarrier(BlockPos pos, int slot) {}

    private SpaceManager() {}

    public static final class Space {
        private final UUID id;
        private final ServerLevel level;
        private RoomGeometry internal;
        private final RoomGeometry client;
        private final int anchorChunkX;
        private final int anchorChunkZ;
        private UUID anchorItem;
        private UUID anchorPlayer;
        private UUID anchorEntity;
        private BlockPos anchorBlock;
        private int anchorBlockSlot = -1;
        private int anchorEntitySlot = -1;
        private Vec3 lastAnchor;
        private BlockPos roomTicket;
        private BlockPos anchorTicket;
        private final Map<UUID, CompoundTag> residentData = new HashMap<>();
        private final Map<UUID, CompoundTag> derivedData = new HashMap<>();
        private final java.util.List<UUID> residentOrder = new ArrayList<>();
        private boolean destroyPending;
        private int destroyTicks;
        private String anchorState = "UNKNOWN";

        private Space(UUID id, ServerLevel level, RoomGeometry internal, RoomGeometry client, int anchorChunkX, int anchorChunkZ) {
            this.id = id;
            this.level = level;
            this.internal = internal;
            this.client = client;
            this.anchorChunkX = anchorChunkX;
            this.anchorChunkZ = anchorChunkZ;
        }

        public UUID id() { return id; }
        public ServerLevel level() { return level; }
        public RoomGeometry internal() { return internal; }
        public RoomGeometry client() { return client; }
        public int anchorChunkX() { return anchorChunkX; }
        public int anchorChunkZ() { return anchorChunkZ; }
        public UUID anchorItem() { return anchorItem; }
        public Vec3 lastAnchor() { return lastAnchor; }
        public Map<UUID, CompoundTag> residents() { return residentData; }
        public Map<UUID, CompoundTag> derived() { return derivedData; }
        public java.util.List<UUID> residentOrder() { return residentOrder; }
        public boolean destroyPending() { return destroyPending; }
    }

    public static boolean enter(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (!SpacePlatform.supported()) {
            restoreEntryHand(player, hand, stack);
            return false;
        }
        if (player == null || stack == null || hand == null || !(player.level() instanceof ServerLevel level)) return false;
        if (PLAYERS.containsKey(player.getUUID())) {
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_already"));
            restoreEntryHand(player, hand, stack);
            return false;
        }
        UUID requested = CanContents.spaceId(stack);
        boolean newSpace = requested == null || !SPACES.containsKey(requested);
        Space space = spaceFor(stack, player, level);
        if (space == null) {
            restoreEntryHand(player, hand, stack);
            return false;
        }
        Vec3 anchor = player.position();
        Vec3 previousAnchor = space.lastAnchor;
        ItemStack dropped = stack.copyWithCount(1);
        CanContents.storeSpace(dropped, space.id, space.residentData.size());
        ItemEntity item = new ItemEntity(level, anchor.x, anchor.y + 0.1, anchor.z, dropped);
        item.setUnlimitedLifetime();
        item.setDefaultPickUpDelay();
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        if (!level.addFreshEntity(item)) {
            item.discard();
            rollbackEntry(space, newSpace, stack);
            restoreEntryHand(player, hand, stack);
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_anchor_failed"));
            return false;
        }
        player.setItemInHand(hand, ItemStack.EMPTY);
        PlayerAccess.syncInventory(player);
        space.anchorItem = item.getUUID();
        space.anchorPlayer = null;
        space.anchorEntity = null;
        space.anchorBlock = null;
        rebaseTo(space, anchor);
        forceRoom(space);
        forceAnchorTicket(space, anchor);
        PLAYERS.put(player.getUUID(), space);
        SAVED_PLAYERS.put(player.getUUID(), space);
        SpacePlatform.beginSession(player);
        SpacePlatform.sendRoom(player, space);
        SpacePlatform.sendResidents(player, space);
        SYNC.put(player.getUUID(), 30);
        Vec3 spawn = space.internal.spawn();
        boolean placed;
        SpaceDomain.push(player);
        try {
            SpacePlatform.teleportPlayer(player, level, spawn, player.getYRot(), player.getXRot());
            SpacePlatform.resetMovement(player);
            placed = player.level() == level && player.distanceToSqr(spawn) <= 4.0;
        } finally {
            SpaceDomain.pop();
        }
        if (!placed) {
            PLAYERS.remove(player.getUUID());
            SAVED_PLAYERS.remove(player.getUUID());
            SYNC.remove(player.getUUID());
            item.discard();
            SpacePlatform.restoreRoom(player, space);
            space.anchorItem = null;
            if (previousAnchor != null) rebaseTo(space, previousAnchor);
            else space.lastAnchor = null;
            rollbackEntry(space, newSpace, dropped);
            player.setItemInHand(hand, dropped);
            PlayerAccess.syncInventory(player);
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_teleport_failed"));
            return false;
        }
        PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_entered"));
        return true;
    }

    private static void restoreEntryHand(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (player == null || hand == null || stack == null) return;
        player.setItemInHand(hand, stack);
        PlayerAccess.syncInventory(player);
    }

    /** Undo a failed first entry: no space record, no ticket and no id on the returned can. */
    private static void rollbackEntry(Space space, boolean newSpace, ItemStack stack) {
        if (!newSpace) return;
        SPACES.remove(space.id);
        SAVED_PLAYERS.entrySet().removeIf(entry -> entry.getValue() == space);
        releaseRoomTicket(space);
        releaseAnchorTicket(space);
        CanContents.clearSpace(stack);
    }

    public static boolean exit(ServerPlayer player) {
        Space space = PLAYERS.get(player.getUUID());
        if (space == null) return false;
        Vec3 target = externalAnchor(space);
        if (target == null) {
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_anchor_lost"));
            return false;
        }
        PLAYERS.remove(player.getUUID());
        SAVED_PLAYERS.remove(player.getUUID());
        SYNC.remove(player.getUUID());
        SpaceDomain.invalidate(player);
        releaseAnchorTicket(space);
        SpacePlatform.restoreRoom(player, space);
        SpacePlatform.teleportPlayer(player, space.level, target, player.getYRot(), player.getXRot());
        SpacePlatform.resetMovement(player);
        if (player.level() != space.level) {
            PLAYERS.put(player.getUUID(), space);
            SAVED_PLAYERS.put(player.getUUID(), space);
            SpacePlatform.sendRoom(player, space);
            Vec3 spawn = space.internal.spawn();
            SpaceDomain.push(player);
            try {
                SpacePlatform.teleportPlayer(player, space.level, spawn, player.getYRot(), player.getXRot());
                SpacePlatform.resetMovement(player);
            } finally {
                SpaceDomain.pop();
            }
            return false;
        }
        PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_exited"));
        return true;
    }

    public static boolean clicked(ServerPlayer player) {
        return exit(player);
    }

    public static boolean capture(ServerPlayer player, Mob mob, ItemStack stack) {
        if (!SpacePlatform.supported()) return false;
        if (player == null || mob == null || stack == null || !(mob.level() instanceof ServerLevel level)) return false;
        if (PLAYERS.containsKey(player.getUUID())) return false;
        if (!mob.isAlive() || mob.isRemoved() || trackedSpace(mob) != null
                || mob.isPassenger() || mob.isVehicle() || mob.isLeashed()) {
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_capture_failed"));
            return false;
        }
        UUID requested = CanContents.spaceId(stack);
        boolean newSpace = requested == null || !SPACES.containsKey(requested);
        Space space = spaceFor(stack, player, level);
        if (space == null) return false;
        if (mob.getBbWidth() > 8.5F || mob.getBbHeight() > 3.8F) {
            rollbackEntry(space, newSpace, stack);
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_too_large"));
            return false;
        }
        CompoundTag data = EntityStorage.save(mob);
        Mob resident = null;
        UUID residentId = UUID.randomUUID();
        try {
            Entity created = EntityStorage.create(EntityStorage.string(data, "id"), level);
            if (!(created instanceof Mob candidate)) throw new IllegalArgumentException("Not a mob");
            resident = candidate;
            EntityStorage.load(resident, data);
            resident.setUUID(residentId);
            resident.setPersistenceRequired();
            Vec3 spawn = residentSpawn(space, space.residentOrder.size());
            EntityStorage.position(resident, spawn, mob.getXRot());
            resident.setDeltaMovement(Vec3.ZERO);
            // Register before insertion so the entity tracker's AddEntity/bundle is
            // filtered as space-owned; otherwise an external client sees the resident
            // at the internal room coordinates until the next sync.
            RESIDENTS.put(residentId, space);
            space.residentOrder.add(residentId);
            if (!level.addFreshEntity(resident)) throw new IllegalStateException("Level rejected resident");
        } catch (RuntimeException failure) {
            RESIDENTS.remove(residentId);
            space.residentOrder.remove(residentId);
            space.residentData.remove(residentId);
            if (resident != null) resident.discard();
            rollbackEntry(space, newSpace, stack);
            LOGGER.warn("Cannot admit {} into virtual space: {}", mob.getType(), failure.toString());
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_capture_failed"));
            return false;
        }
        space.residentData.put(residentId, EntityStorage.save(resident));
        mob.setRemoved(Entity.RemovalReason.DISCARDED);
        CanContents.storeSpace(stack, space.id, space.residentData.size());
        forceRoom(space);
        PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_captured", mob.getName()));
        return true;
    }

    private static java.util.List<UUID> orderedResidentIds(Space space) {
        java.util.List<UUID> ids = new ArrayList<>();
        for (UUID id : space.residentOrder) if (space.residentData.containsKey(id)) ids.add(id);
        for (UUID id : space.residentData.keySet()) if (!ids.contains(id)) ids.add(id);
        return ids;
    }

    private static Vec3 residentSpawn(Space space, int index) {
        Vec3 base = space.internal.spawn();
        int col = index % 3;
        int row = (index / 3) % 3;
        return new Vec3(base.x + (col - 1) * 2.0, base.y, base.z + (row - 1) * 2.0);
    }

    /**
     * Release the last resident admitted to the space held by this stack (LIFO).
     * A failed level insertion keeps both the ledger row and the can reference.
     */
    public static boolean releaseLast(ServerPlayer player, ItemStack stack, ServerLevel level, Vec3 position) {
        if (!SpacePlatform.supported() || player == null || stack == null || level == null || position == null) return false;
        if (PLAYERS.containsKey(player.getUUID())) return false;
        UUID spaceId = CanContents.spaceId(stack);
        Space space = spaceId == null ? null : SPACES.get(spaceId);
        if (space == null || space.level != level || space.destroyPending) return false;
        UUID residentId = null;
        for (int i = space.residentOrder.size() - 1; i >= 0; i--) {
            UUID candidate = space.residentOrder.get(i);
            if (QUARANTINED.containsKey(candidate)) continue;
            if (space.residentData.containsKey(candidate)) {
                residentId = candidate;
                break;
            }
        }
        if (residentId == null) return false;
        final UUID releasedId = residentId;
        CompoundTag data = space.residentData.get(residentId);
        if (data == null) return false;
        Mob mob = null;
        try {
            Entity existing = SpaceDomain.raw(() -> level.getEntity(releasedId));
            if (existing instanceof Mob current && !current.isRemoved()) {
                Space owner = trackedSpace(current);
                if (owner != null && owner != space) {
                    throw new IllegalStateException("UUID is owned by another space");
                }
                mob = current;
                Mob moved = mob;
                SpaceDomain.raw(() -> EntityStorage.position(moved, position, player.getYRot()));
                moved.setDeltaMovement(Vec3.ZERO);
            } else {
                Entity created = EntityStorage.create(EntityStorage.string(data, "id"), level);
                if (!(created instanceof Mob candidate)) throw new IllegalArgumentException("Not a mob");
                mob = candidate;
                EntityStorage.load(mob, data);
                mob.setUUID(residentId);
                mob.setPersistenceRequired();
                EntityStorage.position(mob, position, player.getYRot());
                mob.setDeltaMovement(Vec3.ZERO);
                if (!level.addFreshEntity(mob)) {
                    Entity conflict = SpaceDomain.raw(() -> level.getEntity(releasedId));
                    if (conflict == null || SpaceManager.trackedSpace(conflict) != null) {
                        throw new IllegalStateException("Level rejected release");
                    }
                    // The entity already exists in the real world: treat as released.
                }
            }
        } catch (RuntimeException failure) {
            if (mob != null) mob.discard();
            LOGGER.warn("Cannot release resident {}: {}", residentId, failure.toString());
            return false;
        }
        RESIDENTS.remove(residentId);
        SpacePlatform.retrack(mob);
        space.residentData.remove(residentId);
        space.residentOrder.remove(residentId);
        if (space.residentData.isEmpty() && space.derivedData.isEmpty() && !hasPlayer(space)
                && !hasQuarantine(space)) {
            if (space.anchorItem != null) {
                Entity anchor = SpaceDomain.raw(() -> space.level.getEntity(space.anchorItem));
                if (anchor != null) anchor.discard();
            }
            SPACES.remove(space.id);
            SAVED_PLAYERS.entrySet().removeIf(entry -> entry.getValue() == space);
            releaseRoomTicket(space);
            releaseAnchorTicket(space);
            CanContents.clearSpace(stack);
        } else {
            CanContents.storeSpace(stack, space.id, space.residentData.size());
        }
        PlayerAccess.syncInventory(player);
        PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_released", mob.getName()));
        return true;
    }

    /**
     * Only a terminal removal with a still-present can stack is confirmed destruction.
     * Pickup/hopper transfer empties the live stack before discard; chunk unload and
     * dimension transfer are not destruction. Missing carriers are never inferred here.
     */
    public static void anchorItemRemoved(ItemEntity item, Entity.RemovalReason reason) {
        if (item == null || item.level().isClientSide()) return;
        Space space = spaceForAnchorItem(item.getUUID());
        if (space == null) return;
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            space.anchorState = "UNLOADED";
            return;
        }
        if (reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            space.anchorState = "TRANSFER";
            return;
        }
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) return;
        if (item.getItem().isEmpty()) {
            // Ownership transfer (player pickup or hopper): let updateCarrier follow it.
            space.anchorState = "TRANSFER";
            return;
        }
        space.anchorState = "DESTROYED";
        space.destroyPending = true;
        space.destroyTicks = 0;
    }

    private static Space spaceForAnchorItem(UUID itemId) {
        if (itemId == null) return null;
        for (Space space : SPACES.values()) {
            if (itemId.equals(space.anchorItem)) return space;
        }
        return null;
    }

    /** Shatter a space after its only can was really destroyed, preserving failed transfers. */
    private static void shatter(Space space) {
        final Vec3 target = space.lastAnchor == null ? space.internal.spawn() : space.lastAnchor;
        boolean failed = false;
        for (ServerPlayer player : new ArrayList<>(space.level.getServer().getPlayerList().getPlayers())) {
            if (PLAYERS.get(player.getUUID()) != space) continue;
            PLAYERS.remove(player.getUUID());
            SAVED_PLAYERS.remove(player.getUUID());
            SYNC.remove(player.getUUID());
            SpaceDomain.invalidate(player);
            SpacePlatform.restoreRoom(player, space);
            SpacePlatform.teleportPlayer(player, space.level, target, player.getYRot(), player.getXRot());
            SpacePlatform.resetMovement(player);
            if (player.level() != space.level || player.distanceToSqr(target) > 16.0) {
                PLAYERS.put(player.getUUID(), space);
                SAVED_PLAYERS.put(player.getUUID(), space);
                failed = true;
            } else {
                PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_shattered"));
            }
        }
        java.util.List<UUID> residents = orderedResidentIds(space);
        for (int index = 0; index < residents.size(); index++) {
            UUID id = residents.get(index);
            CompoundTag data = space.residentData.get(id);
            if (data == null) continue;
            Mob mob = null;
            try {
                double dx = (index % 3 - 1) * 1.5;
                double dz = (index / 3 % 3 - 1) * 1.5;
                Entity existing = SpaceDomain.raw(() -> space.level.getEntity(id));
                if (existing instanceof Mob current && !current.isRemoved()) {
                    Space owner = trackedSpace(current);
                    if (owner != null && owner != space) {
                        throw new IllegalStateException("UUID is owned by another space");
                    }
                    mob = current;
                    Mob moved = mob;
                    SpaceDomain.raw(() -> EntityStorage.position(moved, target.add(dx, 0, dz), moved.getYRot()));
                    moved.setDeltaMovement(Vec3.ZERO);
                } else {
                    Entity created = EntityStorage.create(EntityStorage.string(data, "id"), space.level);
                    if (!(created instanceof Mob candidate)) throw new IllegalArgumentException("Not a mob");
                    mob = candidate;
                    EntityStorage.load(mob, data);
                    mob.setUUID(id);
                    mob.setPersistenceRequired();
                    EntityStorage.position(mob, target.add(dx, 0, dz), mob.getYRot());
                    mob.setDeltaMovement(Vec3.ZERO);
                    if (!space.level.addFreshEntity(mob)) {
                        Entity conflict = SpaceDomain.raw(() -> space.level.getEntity(id));
                        if (conflict == null || trackedSpace(conflict) != null) {
                            throw new IllegalStateException("Level rejected resident");
                        }
                    }
                }
                RESIDENTS.remove(id);
                SpacePlatform.retrack(mob);
                space.residentData.remove(id);
                space.residentOrder.remove(id);
            } catch (RuntimeException failure) {
                if (mob != null) mob.discard();
                LOGGER.warn("Cannot eject resident {} on shatter: {}", id, failure.toString());
                failed = true;
            }
        }
        for (UUID id : new ArrayList<>(space.derivedData.keySet())) {
            CompoundTag record = space.derivedData.get(id);
            if (record == null) continue;
            Entity created = null;
            try {
                double lx = EntityStorage.decimal(record, "LocalX");
                double ly = EntityStorage.decimal(record, "LocalY");
                double lz = EntityStorage.decimal(record, "LocalZ");
                Entity existing = SpaceDomain.raw(() -> space.level.getEntity(id));
                if (existing != null && !existing.isRemoved()) {
                    Space owner = trackedSpace(existing);
                    if (owner != null && owner != space) {
                        throw new IllegalStateException("UUID is owned by another space");
                    }
                    created = existing;
                    Entity moved = created;
                    SpaceDomain.raw(() -> moved.setPos(target.x + lx, target.y + ly, target.z + lz));
                } else {
                    CompoundTag data = EntityStorage.compound(record, "Data");
                    created = EntityStorage.create(EntityStorage.string(data, "id"), space.level);
                    EntityStorage.load(created, data);
                    created.setUUID(id);
                    created.setPos(target.x + lx, target.y + ly, target.z + lz);
                    if (!space.level.addFreshEntity(created)) {
                        Entity conflict = SpaceDomain.raw(() -> space.level.getEntity(id));
                        if (conflict == null || trackedSpace(conflict) != null) {
                            throw new IllegalStateException("Level rejected derived");
                        }
                    }
                }
                DERIVED.remove(id);
                SpacePlatform.retrack(created);
                space.derivedData.remove(id);
            } catch (RuntimeException failure) {
                if (created != null) created.discard();
                LOGGER.warn("Cannot eject derived {} on shatter: {}", id, failure.toString());
                failed = true;
            }
        }
        if (failed || !space.residentData.isEmpty() || !space.derivedData.isEmpty() || hasPlayer(space)) {
            space.destroyPending = true;
            return;
        }
        SPACES.remove(space.id);
        SAVED_PLAYERS.entrySet().removeIf(entry -> entry.getValue() == space);
        releaseRoomTicket(space);
        releaseAnchorTicket(space);
    }

    /** Identity is the session maps (UUID), never a coordinate: rooms may overlap. */
    public static Space spaceOf(Entity entity) {
        return trackedSpace(entity);
    }

    public static Space trackedSpace(Entity entity) {
        return entity == null ? null : trackedSpace(entity.getUUID());
    }

    public static Space trackedSpace(UUID id) {
        if (id == null) return null;
        Space space = PLAYERS.get(id);
        if (space == null) space = RESIDENTS.get(id);
        if (space == null) space = QUARANTINED.get(id);
        return space == null ? DERIVED.get(id) : space;
    }

    /** Residents, derived entities and players are owned by a space; plain world entities are not. */
    public static boolean isSpaceOwned(Entity entity) {
        if (entity == null) return false;
        UUID id = entity.getUUID();
        return RESIDENTS.containsKey(id) || DERIVED.containsKey(id) || QUARANTINED.containsKey(id);
    }

    /** Entities spawned inside a room (drops, xp, arrows, babies) belong to that space. */
    public static void derive(Entity entity) {
        SpaceDomain.Frame frame = SpaceDomain.frame();
        if (entity == null || entity.level().isClientSide()) return;
        if (!frame.active() || entity instanceof ServerPlayer) return;
        if (entity.level() != frame.level() || !frame.room().contains(entity.position())) return;
        Space space = SPACES.get(frame.space());
        if (space == null || space.level != entity.level()) return;
        UUID id = entity.getUUID();
        if (RESIDENTS.containsKey(id) || DERIVED.containsKey(id)) return;
        DERIVED.put(id, space);
        space.derivedData.put(id, derivedEntry(entity, space));
    }

    /** Drops a space-owned entity identity; used on real termination so it cannot resurrect. */
    public static void forget(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        UUID id = entity.getUUID();
        Space space = trackedSpace(id);
        QUARANTINED.remove(id);
        RESIDENTS.remove(id);
        DERIVED.remove(id);
        if (space == null) return;
        boolean resident = space.residentData.remove(id) != null;
        space.derivedData.remove(id);
        space.residentOrder.remove(id);
        if (resident) refreshCarrier(space);
    }

    /**
     * A dying resident leaves the live ledger at once so the can count, released set and
     * saved record agree; only the quarantine row stays, to keep the corpse hidden until
     * vanilla really removes it. Releasing must never recreate a dead resident.
     */
    public static void quarantine(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        Space space = trackedSpace(entity);
        if (space == null) return;
        UUID id = entity.getUUID();
        QUARANTINED.put(id, space);
        if (space.residentData.remove(id) != null) {
            space.residentOrder.remove(id);
            RESIDENTS.remove(id);
            refreshCarrier(space);
        }
    }

    /** Keep every can that references this space showing the live resident count. */
    private static void refreshCarrier(Space space) {
        if (space == null || space.level.getServer() == null) return;
        int count = space.residentData.size();
        for (ServerPlayer player : space.level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != space.level) continue;
            var inventory = player.getInventory();
            boolean changed = false;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (space.id.equals(CanContents.spaceId(stack)) && CanContents.spaceCount(stack) != count) {
                    CanContents.storeSpace(stack, space.id, count);
                    changed = true;
                }
            }
            if (changed) PlayerAccess.syncInventory(player);
        }
        if (space.anchorItem != null) {
            Entity anchor = SpaceDomain.raw(() -> space.level.getEntity(space.anchorItem));
            if (anchor instanceof ItemEntity item && !item.isRemoved()) {
                ItemStack stack = item.getItem();
                if (space.id.equals(CanContents.spaceId(stack)) && CanContents.spaceCount(stack) != count) {
                    CanContents.storeSpace(stack, space.id, count);
                }
            }
        }
    }

    public static boolean isQuarantined(Entity entity) {
        return entity != null && QUARANTINED.containsKey(entity.getUUID());
    }

    /** Saved cans may predate a resident loss: the ledger is authoritative on join. */
    private static void reconcileInventory(ServerPlayer player) {
        var inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            UUID id = CanContents.spaceId(stack);
            if (id == null) continue;
            Space space = SPACES.get(id);
            if (space == null) {
                if (!hasOrphan(id.toString()) && CanContents.spaceCount(stack) != 0) {
                    CanContents.clearSpace(stack);
                    changed = true;
                }
                continue;
            }
            int count = space.residentData.size();
            if (CanContents.spaceCount(stack) != count) {
                CanContents.storeSpace(stack, id, count);
                changed = true;
            }
        }
        if (changed) PlayerAccess.syncInventory(player);
    }

    private static void reconcileStack(Space space, ItemStack stack) {
        int count = space.residentData.size();
        if (CanContents.spaceCount(stack) != count) CanContents.storeSpace(stack, space.id, count);
    }

    /**
     * Pickup domain is the ItemEntity's session identity, not its can contents.
     * An inside player may only take entities owned by their own space (including
     * derived loot without a Space key); an outside player may only take external
     * entities. The anchor can is external, so inside players cannot take it.
     */
    public static boolean blocksPickup(ServerPlayer player, ItemEntity item) {
        if (player == null || item == null) return false;
        Space playerSpace = PLAYERS.get(player.getUUID());
        Space itemSpace = trackedSpace(item);
        if (playerSpace == null) return itemSpace != null;
        return itemSpace != playerSpace;
    }

    /** Other players sharing the same room, in join order. */
    public static java.util.List<ServerPlayer> playersIn(Space space, ServerPlayer except) {
        java.util.List<ServerPlayer> players = new ArrayList<>();
        if (space == null || space.level.getServer() == null) return players;
        for (ServerPlayer player : space.level.getServer().getPlayerList().getPlayers()) {
            if (player == except || player.level() != space.level) continue;
            if (PLAYERS.get(player.getUUID()) == space) players.add(player);
        }
        return players;
    }

    /** Diagnostic only: positions where more than one room volume overlaps. */
    public static java.util.List<Space> spacesAt(ServerLevel level, Vec3 position) {
        java.util.List<Space> hits = new ArrayList<>();
        for (Space space : SPACES.values()) {
            if (space.level == level && space.internal.contains(position)) hits.add(space);
        }
        return hits;
    }

    public static Space spaceAt(ServerLevel level, Vec3 position) {
        java.util.List<Space> hits = spacesAt(level, position);
        return hits.isEmpty() ? null : hits.get(0);
    }

    private static void forceRoom(Space space) {
        BlockPos pos = BlockPos.containing(space.internal.center());
        if (pos.equals(space.roomTicket)) return;
        if (space.roomTicket != null) SpacePlatform.release(space.level, space.roomTicket, space.id);
        space.roomTicket = pos;
        SpacePlatform.forceTicking(space.level, pos, space.id);
    }

    private static void forceAnchorTicket(Space space, Vec3 anchor) {
        BlockPos pos = BlockPos.containing(anchor);
        if (pos.equals(space.anchorTicket)) return;
        if (space.anchorTicket != null) SpacePlatform.release(space.level, space.anchorTicket, space.id);
        space.anchorTicket = pos;
        SpacePlatform.forceAnchor(space.level, pos, space.id);
    }

    private static void releaseRoomTicket(Space space) {
        if (space.roomTicket == null) return;
        SpacePlatform.release(space.level, space.roomTicket, space.id);
        space.roomTicket = null;
    }

    private static void releaseAnchorTicket(Space space) {
        if (space.anchorTicket == null) return;
        SpacePlatform.release(space.level, space.anchorTicket, space.id);
        space.anchorTicket = null;
    }

    public static boolean insideAnyRoom(ServerLevel level, Vec3 position) {
        return !spacesAt(level, position).isEmpty();
    }

    /** External anchor a space entity is projected onto: the can's real world position. */
    public static Vec3 projectedPosition(Entity entity) {
        Space space = trackedSpace(entity);
        return space == null ? null : space.lastAnchor;
    }

    public static Vec3 saveAnchor(Entity entity) {
        Space space = PLAYERS.get(entity.getUUID());
        if (space == null) space = SAVED_PLAYERS.get(entity.getUUID());
        if (space == null) space = spaceOf(entity);
        if (space == null) return null;
        if (space.anchorBlock != null && space.anchorBlockSlot >= 0) {
            return Vec3.atBottomCenterOf(space.anchorBlock.above());
        }
        Vec3 anchor = externalAnchor(space);
        return anchor == null ? space.lastAnchor : anchor;
    }

    public static Space spaceForPlayer(ServerPlayer player) {
        return player == null ? null : PLAYERS.get(player.getUUID());
    }

    public static Space spaceById(UUID id) {
        return id == null ? null : SPACES.get(id);
    }

    public static int spaceCount() {
        return SPACES.size();
    }

    public static java.util.List<UUID> spaceIds() {
        return new ArrayList<>(SPACES.keySet());
    }

    public static Vec3 packetOffset(ServerPlayer player) {
        Space space = PLAYERS.get(player.getUUID());
        if (space == null) return Vec3.ZERO;
        return space.internal.center().subtract(space.client.center());
    }

    public static Space spaceOfUuid(UUID id) {
        return trackedSpace(id);
    }

    public static boolean isTracked(Entity entity) {
        if (entity == null) return false;
        UUID id = entity.getUUID();
        return PLAYERS.containsKey(id) || RESIDENTS.containsKey(id) || DERIVED.containsKey(id)
                || QUARANTINED.containsKey(id);
    }

    /** Residents persist in the space ledger only, never in world chunk entity data. */
    public static boolean isResident(Entity entity) {
        return entity != null && RESIDENTS.containsKey(entity.getUUID());
    }

    public static Entity findEntity(Space space, UUID id) {
        return space == null ? null : SpaceDomain.raw(() -> space.level.getEntity(id));
    }

    public static java.util.Collection<UUID> ownedEntities(Space space) {
        java.util.List<UUID> ids = new ArrayList<>();
        for (UUID id : space.residentOrder) if (space.residentData.containsKey(id)) ids.add(id);
        for (UUID id : space.residentData.keySet()) if (!ids.contains(id)) ids.add(id);
        ids.addAll(space.derivedData.keySet());
        return ids;
    }

    /** Filtered external lookup: a resident is not part of the real world outside its space. */
    public static Entity visibleEntity(ServerLevel level, UUID id) {
        return level.getEntity(id);
    }

    public static Space spaceOfEntityId(ServerPlayer player, int id) {
        Entity entity = player.level() instanceof ServerLevel level
                ? SpaceDomain.raw(() -> level.getEntity(id)) : null;
        return entity == null ? null : spaceOf(entity);
    }

    public static boolean isSpaceEntity(Entity entity) {
        return spaceOf(entity) != null;
    }

    public static boolean isInside(ServerPlayer player) {
        return PLAYERS.containsKey(player.getUUID());
    }

    public static void clientLeft(ServerPlayer player) {
        RESYNC_AT.remove(player.getUUID());
        Space space = PLAYERS.remove(player.getUUID());
        if (space != null) {
            SpaceDomain.invalidate(player);
            SpacePlatform.restoreRoom(player, space);
        }
    }

    public static void abandon(ServerPlayer player) {
        RESYNC_AT.remove(player.getUUID());
        Space space = PLAYERS.remove(player.getUUID());
        SAVED_PLAYERS.remove(player.getUUID());
        SYNC.remove(player.getUUID());
        if (space == null) return;
        SpaceDomain.invalidate(player);
        releaseAnchorTicket(space);
        SpacePlatform.restoreRoom(player, space);
        Vec3 real = SpaceDomain.raw(player::position);
        if (player.level() != space.level || !space.internal.contains(real)) return;
        Vec3 anchor = externalAnchor(space);
        if (anchor == null) anchor = space.lastAnchor;
        if (anchor != null) {
            SpacePlatform.teleportPlayer(player, space.level, anchor, player.getYRot(), player.getXRot());
            SpacePlatform.resetMovement(player);
        }
    }

    /**
     * Death is not terminal: the dead body must stay hidden until vanilla removes it,
     * and it must not be written to the ledger (a restart would resurrect it).
     */
    public static void entityDied(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        if (entity instanceof ServerPlayer player) {
            died(player);
            return;
        }
        if (isSpaceOwned(entity)) quarantine(entity);
    }

    /** Called before death drops: a death inside a space must drop loot at the external anchor. */
    public static void died(ServerPlayer player) {
        if (player == null || !PLAYERS.containsKey(player.getUUID())) return;
        abandon(player);
    }

    public static boolean clickedAt(ServerPlayer player, BlockPos pos) {
        Space space = PLAYERS.get(player.getUUID());
        if (space == null) return false;
        return space.client.contains(pos);
    }

    public static void tick(MinecraftServer server) {
        SpaceDomain.reset();
        for (Space space : new ArrayList<>(SPACES.values())) {
            if (tickCounter % 20 == 0) prune(space);
            if (space.residentData.isEmpty() && space.derivedData.isEmpty() && !hasPlayer(space)
                    && !hasQuarantine(space)) {
                if (space.destroyPending) shatter(space);
                else releaseRoomTicket(space);
                continue;
            }
            forceRoom(space);
            updateCarrier(server, space);
            if (space.destroyPending) {
                if (carrierCount(space) > 0) {
                    space.destroyPending = false;
                    space.destroyTicks = 0;
                } else if (++space.destroyTicks >= 2) {
                    shatter(space);
                }
            }
        }
        tickCounter++;
        for (Map.Entry<UUID, Space> entry : new ArrayList<>(PLAYERS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            Space space = entry.getValue();
            Integer left = SYNC.get(entry.getKey());
            if (left != null) {
                if (left <= 0) {
                    SYNC.remove(entry.getKey());
                } else {
                    if (left % 5 == 0) resync(player);
                    SYNC.put(entry.getKey(), left - 1);
                }
            }
            if (tickCounter % 100 == 0) SpacePlatform.sendRoom(player, space);
            // An inside player must never keep the anchor can. Creative prediction or a
            // delayed slot echo can reinsert it after entry; enforce the authoritative state.
            var inventory = player.getInventory();
            boolean cleared = false;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (space.id.equals(CanContents.spaceId(inventory.getItem(slot)))) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                    cleared = true;
                }
            }
            if (cleared) PlayerAccess.syncInventory(player);
            SpacePlatform.sendEntityUpdates(player, space);
        }
    }

    private static void updateCarrier(MinecraftServer server, Space space) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != space.level) continue;
            if (PLAYERS.get(player.getUUID()) == space) continue;
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (space.id.equals(CanContents.spaceId(inventory.getItem(slot)))) {
                    reconcileStack(space, inventory.getItem(slot));
                    boolean inside = PLAYERS.get(player.getUUID()) == space;
                    space.anchorPlayer = player.getUUID();
                    space.anchorItem = null;
                    space.anchorEntity = null;
                    space.anchorBlock = null;
                    space.anchorState = "CARRIER";
                    if (!inside) rebaseTo(space, player.position());
                    return;
                }
            }
        }
        if (space.anchorItem != null) {
            Entity item = SpaceDomain.raw(() -> space.level.getEntity(space.anchorItem));
            if (item instanceof ItemEntity entity && !entity.isRemoved()) {
                reconcileStack(space, entity.getItem());
                space.anchorState = "CARRIER";
                rebaseTo(space, entity.position());
                return;
            }
        }
        for (Entity entity : space.level.getAllEntities()) {
            if (entity instanceof ItemEntity item && !item.isRemoved()
                    && space.id.equals(CanContents.spaceId(item.getItem()))) {
                reconcileStack(space, item.getItem());
                space.anchorItem = item.getUUID();
                space.anchorPlayer = null;
                space.anchorEntity = null;
                space.anchorBlock = null;
                space.anchorState = "CARRIER";
                rebaseTo(space, item.position());
                return;
            }
            if (entity instanceof Container container && !(entity instanceof Player)) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    if (space.id.equals(CanContents.spaceId(container.getItem(slot)))) {
                        space.anchorEntity = entity.getUUID();
                        space.anchorEntitySlot = slot;
                        space.anchorItem = null;
                        space.anchorPlayer = null;
                        space.anchorBlock = null;
                        space.anchorState = "CARRIER";
                        rebaseTo(space, entity.position());
                        return;
                    }
                }
            }
        }
        ContainerCarrier block = SpacePlatform.findContainer(space.level,
                BlockPos.containing(space.lastAnchor == null ? space.internal.spawn() : space.lastAnchor), space.id);
        if (block != null) {
            space.anchorBlock = block.pos();
            space.anchorBlockSlot = block.slot();
            space.anchorItem = null;
            space.anchorPlayer = null;
            space.anchorEntity = null;
            space.anchorState = "CARRIER";
            rebaseTo(space, Vec3.atCenterOf(block.pos()));
        }
    }

    public static void started(MinecraftServer server) {
        stopped = false;
        PLAYERS.clear();
        RESIDENTS.clear();
        SPACES.clear();
        SAVED_PLAYERS.clear();
        UNPARSED.clear();
        DERIVED.clear();
        QUARANTINED.clear();
        SpacePlatform.reset();
        load(SpaceStore.read(server), server);
        for (Space space : new ArrayList<>(SPACES.values())) hydrate(space);
    }

    public static void joined(ServerPlayer player) {
        reconcileInventory(player);
        Space space = SAVED_PLAYERS.get(player.getUUID());
        if (space == null || PLAYERS.containsKey(player.getUUID())) return;
        forceRoom(space);
        PLAYERS.put(player.getUUID(), space);
        SpacePlatform.beginSession(player);
        SpacePlatform.sendRoom(player, space);
        SpacePlatform.sendResidents(player, space);
        SYNC.put(player.getUUID(), 30);
        Vec3 spawn = space.internal.spawn();
        boolean placed;
        SpaceDomain.push(player);
        try {
            SpacePlatform.teleportPlayer(player, space.level, spawn, player.getYRot(), player.getXRot());
            SpacePlatform.resetMovement(player);
            placed = player.level() == space.level && player.distanceToSqr(spawn) <= 4.0;
        } finally {
            SpaceDomain.pop();
        }
        if (!placed) {
            PLAYERS.remove(player.getUUID());
            SAVED_PLAYERS.remove(player.getUUID());
            SpacePlatform.restoreRoom(player, space);
            return;
        }
        SYNC.put(player.getUUID(), 60);
    }

    /** A valid client position inside the display room: stop forcing the spawn. */
    public static void validMove(ServerPlayer player) {
        SYNC.remove(player.getUUID());
    }

    /** Re-place an inside player into the display room (also used for invalid packets). */
    public static void resync(ServerPlayer player) {
        Space space = PLAYERS.get(player.getUUID());
        if (space == null) return;
        int now = player.level().getServer() == null ? 0 : player.level().getServer().getTickCount();
        Integer last = RESYNC_AT.put(player.getUUID(), now);
        if (last != null && now - last < 5) return;
        SpacePlatform.sendRoom(player, space);
        SpacePlatform.sendResidents(player, space);
        SpaceDomain.push(player);
        try {
            SpacePlatform.teleportPlayer(player, space.level, space.internal.spawn(), player.getYRot(), player.getXRot());
            SpacePlatform.resetMovement(player);
        } finally {
            SpaceDomain.pop();
        }
    }

    /** Runs on level autosave and on shutdown so a crash does not lose the only copy. */
    public static void saved(MinecraftServer server) {
        if (server == null || stopped) return;
        snapshot(server);
        mergeUnparsed(server, SpaceStore.read(server));
        SpaceStore.write(server, ledger());
        SpaceStore.saveNow(server);
    }

    /** Records this build cannot parse must survive every save instead of being overwritten. */
    private static void mergeUnparsed(MinecraftServer server, CompoundTag disk) {
        ListTag list = EntityStorage.list(disk, "Spaces");
        for (Tag child : list) {
            if (!(child instanceof CompoundTag record)) continue;
            String rawId = EntityStorage.string(record, "Id");
            UUID id;
            try {
                id = UUID.fromString(rawId);
            } catch (IllegalArgumentException failure) {
                rememberOrphan(record);
                continue;
            }
            if (SPACES.containsKey(id)) {
                removeOrphan(rawId);
                continue;
            }
            if (SpacePlatform.level(server, EntityStorage.string(record, "Dimension")) == null) {
                rememberOrphan(record);
            }
        }
    }

    private static void rememberOrphan(CompoundTag record) {
        String rawId = EntityStorage.string(record, "Id");
        if (!hasOrphan(rawId)) UNPARSED.add(record.copy());
    }

    private static void removeOrphan(String rawId) {
        UNPARSED.removeIf(tag -> tag instanceof CompoundTag record
                && rawId.equals(EntityStorage.string(record, "Id")));
    }

    private static boolean hasOrphan(String rawId) {
        for (Tag orphan : UNPARSED) {
            if (orphan instanceof CompoundTag record && rawId.equals(EntityStorage.string(record, "Id"))) return true;
        }
        return false;
    }

    /**
     * Only real removal is terminal. A dying body stays hidden but is excluded from
     * the ledger, so a save cannot resurrect it; chunk unload never deletes a record.
     */
    private static void prune(Space space) {
        for (UUID id : new ArrayList<>(space.residentData.keySet())) {
            Entity entity = SpaceDomain.raw(() -> space.level.getEntity(id));
            if (QUARANTINED.containsKey(id)) {
                if (entity != null && entity.isRemoved()) forget(entity);
                continue;
            }
            if (entity == null) continue;
            if (entity.isRemoved()) {
                Entity.RemovalReason reason = entity.getRemovalReason();
                if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
                    forget(entity);
                }
                // UNLOADED_WITH_PLAYER / CHANGED_DIMENSION: keep the last ledger row.
            } else if (entity instanceof Mob mob && (!mob.isAlive() || mob.isDeadOrDying())) {
                quarantine(entity);
            } else {
                space.residentData.put(id, EntityStorage.save(entity));
            }
        }
        for (UUID id : new ArrayList<>(space.derivedData.keySet())) {
            Entity entity = SpaceDomain.raw(() -> space.level.getEntity(id));
            if (QUARANTINED.containsKey(id)) {
                if (entity != null && entity.isRemoved()) forget(entity);
                continue;
            }
            if (entity == null) continue;
            if (entity.isRemoved()) {
                Entity.RemovalReason reason = entity.getRemovalReason();
                if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
                    forget(entity);
                }
            } else if (entity instanceof Mob mob && (!mob.isAlive() || mob.isDeadOrDying())) {
                quarantine(entity);
            } else {
                space.derivedData.put(id, derivedEntry(entity, space));
            }
        }
        // Quarantined corpses are no longer in residentData; clear the row only after
        // vanilla really removed the entity, so a restart cannot resurrect it.
        for (Map.Entry<UUID, Space> entry : new ArrayList<>(QUARANTINED.entrySet())) {
            if (entry.getValue() != space) continue;
            Entity corpse = SpaceDomain.raw(() -> space.level.getEntity(entry.getKey()));
            if (corpse != null && corpse.isRemoved()) forget(corpse);
        }
    }

    private static CompoundTag derivedEntry(Entity entity, Space space) {
        CompoundTag entry = new CompoundTag();
        entry.put("Data", EntityStorage.save(entity));
        Vec3 real = SpaceDomain.raw(entity::position);
        entry.putDouble("LocalX", real.x - space.internal.x());
        entry.putDouble("LocalY", real.y - space.internal.y());
        entry.putDouble("LocalZ", real.z - space.internal.z());
        return entry;
    }

    private static void snapshot(MinecraftServer server) {
        for (Space space : SPACES.values()) prune(space);
    }

    private static CompoundTag ledger() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        java.util.Set<String> written = new java.util.HashSet<>();
        for (Space space : SPACES.values()) {
            String id = space.id.toString();
            if (!written.add(id)) continue;
            list.add(spaceTag(space));
        }
        for (Tag orphan : UNPARSED) {
            if (orphan instanceof CompoundTag record) {
                String id = EntityStorage.string(record, "Id");
                UUID parsed = null;
                try {
                    parsed = UUID.fromString(id);
                } catch (IllegalArgumentException ignored) {
                }
                if (parsed != null && SPACES.containsKey(parsed)) continue;
                if (!written.add(id)) continue;
            }
            list.add(orphan.copy());
        }
        tag.put("Spaces", list);
        return tag;
    }

    public static void stop(MinecraftServer server) {
        stopped = true;
        // The authoritative snapshot was written on ServerStopping while the levels
        // were still open; saving again after ServerStopped would prune every entity
        // (levels already closed) and erase the ledger.
        for (Space space : SPACES.values()) {
            releaseRoomTicket(space);
            releaseAnchorTicket(space);
        }
        PLAYERS.clear();
        RESIDENTS.clear();
        DERIVED.clear();
        SPACES.clear();
        SAVED_PLAYERS.clear();
        QUARANTINED.clear();
        SpaceDomain.reset();
        SpacePlatform.reset();
    }

    private static void hydrate(Space space) {
        forceRoom(space);
        java.util.List<UUID> ordered = orderedResidentIds(space);
        for (int index = 0; index < ordered.size(); index++) {
            UUID id = ordered.get(index);
            CompoundTag data = space.residentData.get(id);
            if (data == null) continue;
            Vec3 spawn = residentSpawn(space, index);
            Mob existing = null;
            Entity found = SpaceDomain.raw(() -> space.level.getEntity(id));
            if (found instanceof Mob mob) existing = mob;
            if (existing != null) {
                Mob reused = existing;
                SpaceDomain.raw(() -> EntityStorage.position(reused, spawn, reused.getXRot()));
                RESIDENTS.put(id, space);
                continue;
            }
            Mob mob = null;
            try {
                Entity created = EntityStorage.create(EntityStorage.string(data, "id"), space.level);
                if (!(created instanceof Mob candidate)) continue;
                mob = candidate;
                EntityStorage.load(mob, data);
                mob.setUUID(id);
                mob.setPersistenceRequired();
                Mob placed = mob;
                SpaceDomain.raw(() -> EntityStorage.position(placed, spawn, placed.getXRot()));
                RESIDENTS.put(id, space);
                if (!space.level.addFreshEntity(placed)) {
                    RESIDENTS.remove(id);
                    throw new IllegalStateException("Rejected");
                }
            } catch (RuntimeException failure) {
                RESIDENTS.remove(id);
                LOGGER.warn("Cannot restore resident {}: {}", id, failure.toString());
                if (mob != null) mob.discard();
            }
        }
        for (Map.Entry<UUID, CompoundTag> entry : new ArrayList<>(space.derivedData.entrySet())) {
            UUID id = entry.getKey();
            CompoundTag record = entry.getValue();
            Entity found = SpaceDomain.raw(() -> space.level.getEntity(id));
            Vec3 local = new Vec3(EntityStorage.decimal(record, "LocalX"), EntityStorage.decimal(record, "LocalY"),
                    EntityStorage.decimal(record, "LocalZ"));
            if (found != null) {
                Entity placedExisting = found;
                SpaceDomain.raw(() -> placedExisting.setPos(space.internal.x() + local.x, space.internal.y() + local.y,
                        space.internal.z() + local.z));
                DERIVED.put(id, space);
                continue;
            }
            Entity created = null;
            try {
                CompoundTag data = EntityStorage.compound(record, "Data");
                created = EntityStorage.create(EntityStorage.string(data, "id"), space.level);
                EntityStorage.load(created, data);
                created.setUUID(id);
                Entity placed = created;
                SpaceDomain.raw(() -> placed.setPos(space.internal.x() + local.x, space.internal.y() + local.y,
                        space.internal.z() + local.z));
                DERIVED.put(id, space);
                if (!space.level.addFreshEntity(created)) {
                    DERIVED.remove(id);
                    throw new IllegalStateException("Rejected");
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Cannot restore derived entity {}: {}", id, failure.toString());
                if (created != null) created.discard();
                space.derivedData.remove(id);
                DERIVED.remove(id);
            }
        }
    }

    private static CompoundTag spaceTag(Space space) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", space.id.toString());
        tag.putString("Dimension", SpacePlatform.dimension(space.level));
        tag.putDouble("InternalX", space.internal.x());
        tag.putDouble("InternalY", space.internal.y());
        tag.putDouble("InternalZ", space.internal.z());
        tag.putDouble("ClientX", space.client.x());
        tag.putDouble("ClientY", space.client.y());
        tag.putDouble("ClientZ", space.client.z());
        if (space.anchorItem != null) tag.putString("AnchorItem", space.anchorItem.toString());
        if (space.anchorPlayer != null) tag.putString("AnchorPlayer", space.anchorPlayer.toString());
        if (space.anchorEntity != null) tag.putString("AnchorEntity", space.anchorEntity.toString());
        if (space.anchorBlock != null) {
            tag.putDouble("AnchorBlockX", space.anchorBlock.getX());
            tag.putDouble("AnchorBlockY", space.anchorBlock.getY());
            tag.putDouble("AnchorBlockZ", space.anchorBlock.getZ());
            tag.putInt("AnchorBlockSlot", space.anchorBlockSlot);
        }
        if (space.lastAnchor != null) {
            tag.putDouble("AnchorX", space.lastAnchor.x);
            tag.putDouble("AnchorY", space.lastAnchor.y);
            tag.putDouble("AnchorZ", space.lastAnchor.z);
        }
        if (space.destroyPending) tag.putBoolean("Destroyed", true);
        tag.putString("AnchorState", space.anchorState);
        ListTag residents = new ListTag();
        ListTag order = new ListTag();
        for (UUID id : orderedResidentIds(space)) {
            if (QUARANTINED.containsKey(id)) continue;
            CompoundTag data = space.residentData.get(id);
            if (data == null) continue;
            CompoundTag resident = new CompoundTag();
            resident.putString("Id", id.toString());
            resident.put("Data", data.copy());
            residents.add(resident);
            order.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        }
        tag.put("Residents", residents);
        tag.put("ResidentOrder", order);
        ListTag derived = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : space.derivedData.entrySet()) {
            if (QUARANTINED.containsKey(entry.getKey())) continue;
            CompoundTag entity = entry.getValue().copy();
            entity.putString("Id", entry.getKey().toString());
            derived.add(entity);
        }
        tag.put("Derived", derived);
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Space> entry : SAVED_PLAYERS.entrySet()) {
            if (entry.getValue() == space) {
                CompoundTag player = new CompoundTag();
                player.putString("Id", entry.getKey().toString());
                players.add(player);
            }
        }
        tag.put("Players", players);
        return tag;
    }

    private static void load(CompoundTag tag, MinecraftServer server) {
        ListTag list = EntityStorage.list(tag, "Spaces");
        for (Tag child : list) {
            if (!(child instanceof CompoundTag roomTag)) continue;
            try {
                UUID id = UUID.fromString(EntityStorage.string(roomTag, "Id"));
                ServerLevel level = SpacePlatform.level(server, EntityStorage.string(roomTag, "Dimension"));
                if (level == null) {
                    rememberOrphan(roomTag);
                    continue;
                }
                RoomGeometry internal = new RoomGeometry(EntityStorage.decimal(roomTag, "InternalX"),
                        EntityStorage.decimal(roomTag, "InternalY"), EntityStorage.decimal(roomTag, "InternalZ"));
                RoomGeometry client = new RoomGeometry(EntityStorage.decimal(roomTag, "ClientX"),
                        EntityStorage.decimal(roomTag, "ClientY"), EntityStorage.decimal(roomTag, "ClientZ"));
                Space space = new Space(id, level, internal, client, 0, 0);
                String anchorItem = EntityStorage.string(roomTag, "AnchorItem");
                if (!anchorItem.isEmpty()) space.anchorItem = UUID.fromString(anchorItem);
                String anchorPlayer = EntityStorage.string(roomTag, "AnchorPlayer");
                if (!anchorPlayer.isEmpty()) space.anchorPlayer = UUID.fromString(anchorPlayer);
                String anchorEntity = EntityStorage.string(roomTag, "AnchorEntity");
                if (!anchorEntity.isEmpty()) space.anchorEntity = UUID.fromString(anchorEntity);
                if (roomTag.contains("AnchorBlockX")) space.anchorBlock = new BlockPos((int) EntityStorage.decimal(roomTag, "AnchorBlockX"),
                        (int) EntityStorage.decimal(roomTag, "AnchorBlockY"), (int) EntityStorage.decimal(roomTag, "AnchorBlockZ"));
                if (roomTag.contains("AnchorBlockSlot")) space.anchorBlockSlot = EntityStorage.integer(roomTag, "AnchorBlockSlot");
                if (roomTag.contains("AnchorX")) space.lastAnchor = new Vec3(EntityStorage.decimal(roomTag, "AnchorX"),
                        EntityStorage.decimal(roomTag, "AnchorY"), EntityStorage.decimal(roomTag, "AnchorZ"));
                ListTag residents = EntityStorage.list(roomTag, "Residents");
                for (Tag residentTag : residents) {
                    if (!(residentTag instanceof CompoundTag resident)) continue;
                    UUID residentId = UUID.fromString(EntityStorage.string(resident, "Id"));
                    if (space.residentData.containsKey(residentId) || space.derivedData.containsKey(residentId)) continue;
                    space.residentData.put(residentId, EntityStorage.compound(resident, "Data").copy());
                    space.residentOrder.add(residentId);
                }
                ListTag storedOrder = EntityStorage.stringList(roomTag, "ResidentOrder");
                if (!storedOrder.isEmpty()) {
                    java.util.List<UUID> ordered = new ArrayList<>();
                    for (Tag orderTag : storedOrder) {
                        if (!(orderTag instanceof net.minecraft.nbt.StringTag text)) continue;
                        try {
                            UUID residentId = UUID.fromString(cc.sighs.cannedmonster.platform.TagAccess.text(text));
                            if (space.residentData.containsKey(residentId) && !ordered.contains(residentId)) {
                                ordered.add(residentId);
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    for (UUID residentId : space.residentOrder) if (!ordered.contains(residentId)) ordered.add(residentId);
                    space.residentOrder.clear();
                    space.residentOrder.addAll(ordered);
                }
                space.destroyPending = EntityStorage.bool(roomTag, "Destroyed");
                String anchorState = EntityStorage.string(roomTag, "AnchorState");
                space.anchorState = anchorState.isEmpty() ? "UNKNOWN" : anchorState;
                ListTag derived = EntityStorage.list(roomTag, "Derived");
                for (Tag derivedTag : derived) {
                    if (!(derivedTag instanceof CompoundTag entry)) continue;
                    UUID derivedId = UUID.fromString(EntityStorage.string(entry, "Id"));
                    if (space.residentData.containsKey(derivedId) || space.derivedData.containsKey(derivedId)) continue;
                    space.derivedData.put(derivedId, entry.copy());
                }
                ListTag players = EntityStorage.list(roomTag, "Players");
                for (Tag playerTag : players) {
                    if (!(playerTag instanceof CompoundTag player)) continue;
                    SAVED_PLAYERS.put(UUID.fromString(EntityStorage.string(player, "Id")), space);
                }
                SPACES.put(id, space);
                removeOrphan(id.toString());
            } catch (RuntimeException failure) {
                LOGGER.warn("Cannot load virtual space: {}", failure.toString());
                rememberOrphan(roomTag);
            }
        }
    }

    public static void stop() {
        for (Space space : new ArrayList<>(SPACES.values())) {
            for (UUID id : ownedEntities(space)) {
                Entity entity = SpaceDomain.raw(() -> space.level.getEntity(id));
                if (entity != null) entity.discard();
            }
        }
        for (Space space : SPACES.values()) {
            releaseRoomTicket(space);
            releaseAnchorTicket(space);
        }
        PLAYERS.clear();
        RESIDENTS.clear();
        DERIVED.clear();
        SPACES.clear();
        QUARANTINED.clear();
        SpaceDomain.reset();
        SpacePlatform.reset();
    }

    private static boolean hasPlayer(Space space) {
        for (Space value : PLAYERS.values()) if (value == space) return true;
        return false;
    }

    /** A quarantined corpse still holds the room ticket until vanilla really removes it. */
    private static boolean hasQuarantine(Space space) {
        for (Space owner : QUARANTINED.values()) if (owner == space) return true;
        return false;
    }

    private static Space spaceFor(ItemStack stack, ServerPlayer player, ServerLevel level) {
        UUID id = CanContents.spaceId(stack);
        Space existing = id == null ? null : SPACES.get(id);

        if (existing != null) return existing.level == level ? existing : null;
        // An id that belongs to an unparsable ledger record must not be re-created:
        // writing a second record with the same id would make the ledger ambiguous.
        if (id != null && hasOrphan(id.toString())) return null;
        int clientY = Math.min(300, SpacePlatform.maxY(level) - 16);
        RoomGeometry client = clientRoom(level, clientY);
        RoomGeometry internal = internalRoom(level, player.position());
        UUID spaceId = id == null ? UUID.randomUUID() : id;
        int chunkX = Math.floorDiv((int) Math.floor(player.getX()), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(player.getZ()), 16);
        Space space = new Space(spaceId, level, internal, client, chunkX, chunkZ);
        space.lastAnchor = player.position();
        SPACES.put(spaceId, space);
        CanContents.storeSpace(stack, spaceId, space.residentData.size());
        forceRoom(space);
        return space;
    }

    /** Every space gets its own client display slot; two players never share a fake room. */
    private static RoomGeometry clientRoom(ServerLevel level, int clientY) {
        for (int slot = 0; slot < 256; slot++) {
            RoomGeometry candidate = new RoomGeometry(16 + (slot % 16) * 32, clientY, 16 + (slot / 16) * 32);
            boolean used = false;
            for (Space other : SPACES.values()) {
                if (other.level == level && other.client.x() == candidate.x() && other.client.z() == candidate.z()) {
                    used = true;
                    break;
                }
            }
            if (!used) return candidate;
        }
        return new RoomGeometry(16, clientY, 16);
    }

    private static RoomGeometry internalRoom(ServerLevel level, Vec3 anchor) {
        int ax = (int) Math.floor(anchor.x), ay = (int) Math.floor(anchor.y), az = (int) Math.floor(anchor.z);
        for (int ring = 1; ring <= 6; ring++) {
            for (int gx = ring; gx >= -ring; gx--) {
                for (int gz = ring; gz >= -ring; gz--) {
                    if (Math.max(Math.abs(gx), Math.abs(gz)) != ring) continue;
                    RoomGeometry candidate = new RoomGeometry(ax + gx * 32, ay, az + gz * 32);
                    if (!overlapsAny(level, candidate)) return candidate;
                }
            }
        }
        return new RoomGeometry(ax + 16, ay, az + 16);
    }

    private static boolean overlapsAny(ServerLevel level, RoomGeometry candidate) {
        for (Space other : SPACES.values()) {
            if (other.level != level) continue;
            RoomGeometry room = other.internal;
            if (candidate.x() < room.x() + RoomGeometry.SIZE_X && candidate.x() + RoomGeometry.SIZE_X > room.x()
                    && candidate.y() < room.y() + RoomGeometry.SIZE_Y && candidate.y() + RoomGeometry.SIZE_Y > room.y()
                    && candidate.z() < room.z() + RoomGeometry.SIZE_Z && candidate.z() + RoomGeometry.SIZE_Z > room.z()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The room is block-aligned: move it, its residents and any inside player by whole
     * blocks only. Moving entities by the raw fractional anchor delta while
     * {@link RoomGeometry} floors its origin makes the room ratchet one block per
     * rebase; residents drift out of the 13x8x13 room, lose virtual collisions and
     * fall/suffocate (observed when a dropped anchor item's Y oscillates).
     */
    private static void rebaseTo(Space space, Vec3 anchor) {
        Vec3 old = space.lastAnchor;
        if (old == null) {
            space.lastAnchor = anchor;
            return;
        }
        double dx = Math.floor(anchor.x) - Math.floor(old.x);
        double dy = Math.floor(anchor.y) - Math.floor(old.y);
        double dz = Math.floor(anchor.z) - Math.floor(old.z);
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            space.lastAnchor = anchor;
            return;
        }
        space.lastAnchor = anchor;
        space.internal = space.internal.offset(dx, dy, dz);
        for (ServerPlayer player : new ArrayList<>(space.level.getServer().getPlayerList().getPlayers())) {
            if (PLAYERS.get(player.getUUID()) != space) continue;
            SpaceDomain.raw(() -> {
                player.setPos(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
            });
            SpacePlatform.resetMovement(player);
        }
        for (UUID id : ownedEntities(space)) {
            Entity entity = SpaceDomain.raw(() -> space.level.getEntity(id));
            if (entity != null) {
                SpaceDomain.raw(() -> entity.setPos(entity.getX() + dx, entity.getY() + dy,
                        entity.getZ() + dz));
            }
        }
        forceRoom(space);
        forceAnchorTicket(space, anchor);
    }

    private static Vec3 externalAnchor(Space space) {
        if (space.anchorPlayer != null) {
            ServerPlayer player = space.level.getServer().getPlayerList().getPlayer(space.anchorPlayer);
            if (player != null && player.level() == space.level) {
                boolean holds = holdsCan(player, space.id);
                if (PLAYERS.get(player.getUUID()) == space) return holds ? space.lastAnchor : null;
                if (holds) return player.position();
            }
        }
        if (space.anchorItem != null) {
            Entity entity = space.level.getEntity(space.anchorItem);
            if (entity instanceof ItemEntity item && !item.isRemoved()) return item.position();
        }
        if (space.anchorEntity != null) {
            Entity entity = space.level.getEntity(space.anchorEntity);
            if (entity instanceof Container container && space.anchorEntitySlot >= 0
                    && space.anchorEntitySlot < container.getContainerSize()
                    && space.id.equals(CanContents.spaceId(container.getItem(space.anchorEntitySlot)))) {
                return entity.position();
            }
        }
        if (space.anchorBlock != null && space.anchorBlockSlot >= 0) {
            SpacePlatform.loadChunk(space.level, space.anchorBlock);
            if (space.level.getBlockEntity(space.anchorBlock) instanceof Container container
                    && space.anchorBlockSlot < container.getContainerSize()
                    && space.id.equals(CanContents.spaceId(container.getItem(space.anchorBlockSlot)))) {
                return Vec3.atBottomCenterOf(space.anchorBlock.above());
            }
        }
        return null;
    }

    private static boolean holdsCan(ServerPlayer player, UUID space) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (space.equals(CanContents.spaceId(inventory.getItem(slot)))) return true;
        }
        return false;
    }

    public static boolean recover(ServerPlayer player) {
        Space space = PLAYERS.get(player.getUUID());
        boolean inside = space != null;
        if (space == null) space = lostSpaceNear(player);
        if (space == null || space.lastAnchor == null || space.destroyPending) return false;
        // A known carrier (including an offline player or unloaded chunk) is not lost.
        // Never mint a second can because the holder is temporarily unavailable.
        if (space.anchorPlayer != null || space.anchorItem != null
                || space.anchorEntity != null || space.anchorBlock != null) {
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_anchor_present"));
            return false;
        }
        if (carrierCount(space) > 0) {
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_anchor_present"));
            return false;
        }
        Vec3 anchor = space.lastAnchor;
        ItemStack can = SpacePlatform.spatialCan();
        CanContents.storeSpace(can, space.id, space.residentData.size());
        ItemEntity item = new ItemEntity(space.level, anchor.x, anchor.y + 0.1, anchor.z, can);
        item.setUnlimitedLifetime();
        item.setDefaultPickUpDelay();
        item.setNoGravity(true);
        if (!space.level.addFreshEntity(item)) {
            item.discard();
            return false;
        }
        space.anchorItem = item.getUUID();
        space.anchorPlayer = null;
        space.anchorEntity = null;
        space.anchorBlock = null;
        saved(space.level.getServer());
        if (inside) return exit(player);
        PlayerAccess.message(player, Component.translatable("message.cannedmonster.space_recovered"));
        return true;
    }

    /** Number of live can carriers; more than zero means recover must not create another. */
    private static int carrierCount(Space space) {
        int count = 0;
        for (ServerPlayer player : space.level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != space.level) continue;
            if (PLAYERS.get(player.getUUID()) == space) continue;
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (space.id.equals(CanContents.spaceId(inventory.getItem(slot)))) count++;
            }
        }
        for (Entity entity : space.level.getAllEntities()) {
            if (entity.isRemoved()) continue;
            if (entity instanceof ItemEntity item && space.id.equals(CanContents.spaceId(item.getItem()))) {
                count++;
                continue;
            }
            if (entity instanceof Container container && !(entity instanceof Player)) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    if (space.id.equals(CanContents.spaceId(container.getItem(slot)))) count++;
                }
            }
        }
        if (space.anchorBlock != null && space.anchorBlockSlot >= 0) {
            SpacePlatform.loadChunk(space.level, space.anchorBlock);
            if (space.level.getBlockEntity(space.anchorBlock) instanceof Container container
                    && space.anchorBlockSlot < container.getContainerSize()
                    && space.id.equals(CanContents.spaceId(container.getItem(space.anchorBlockSlot)))) {
                count++;
            }
        }
        return count;
    }

    /** Nearest space in this level whose can carrier no longer exists (safe rescue target). */
    private static Space lostSpaceNear(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        Space best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Space candidate : SPACES.values()) {
            if (candidate.level != level || candidate.lastAnchor == null) continue;
            if (externalAnchor(candidate) != null) continue;
            double distance = player.position().distanceToSqr(candidate.lastAnchor);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
