package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.space.RoomGeometry;
import cc.sighs.cannedmonster.space.SpaceDomain;
import cc.sighs.cannedmonster.space.SpaceManager;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpacePlatform {
    private static final TicketType<UUID> TICKET = TicketType.create("cannedmonster_virtual_room", Comparator.<UUID>naturalOrder());
    private static final Map<UUID, Map<Integer, Sent>> SENT = new HashMap<>();
    private static final Map<UUID, java.util.Set<UUID>> INFO = new HashMap<>();
    private static boolean sendingRoom;
    private static boolean sendingEntities;
    private static int soundsSent, soundsDropped, particlesSent, particlesDropped;
    private static int addDropped, addSent, teleportDropped, teleportSent, moveDropped, moveSent;

    private record Sent(double x, double y, double z, byte yRot, byte xRot, byte head) {}

    private SpacePlatform() {}

    public static void reset() {
        SENT.clear();
        INFO.clear();
        resetPacketStats();
    }

    /** A new room session must re-add every visible entity exactly once. */
    public static void beginSession(ServerPlayer player) {
        SENT.remove(player.getUUID());
        INFO.remove(player.getUUID());
    }

    public static boolean supported() {
        return true;
    }

    public static boolean sendingEntities() {
        return sendingEntities;
    }

    public static boolean sendingRoom() {
        return sendingRoom;
    }

    public static void resetPacketStats() {
        soundsSent = 0;
        soundsDropped = 0;
        particlesSent = 0;
        particlesDropped = 0;
        addDropped = 0;
        addSent = 0;
        teleportDropped = 0;
        teleportSent = 0;
        moveDropped = 0;
        moveSent = 0;
    }

    /** Real send-chain counters: only packets that actually passed through ServerCommonPacketListenerImpl.send. */
    public static int[] packetStats() {
        return new int[]{soundsSent, soundsDropped, particlesSent, particlesDropped};
    }

    public static int[] positionStats() {
        return new int[]{addDropped, addSent, teleportDropped, teleportSent, moveDropped, moveSent};
    }

    public static void countPacket(boolean dropped, boolean sound, boolean particle) {
        if (sound) {
            if (dropped) soundsDropped++;
            else soundsSent++;
        }
        if (particle) {
            if (dropped) particlesDropped++;
            else particlesSent++;
        }
    }

    public static net.minecraft.world.level.entity.LevelEntityGetter<Entity> filtered(
            net.minecraft.world.level.entity.LevelEntityGetter<Entity> delegate) {
        return new SpaceEntityGetter(delegate);
    }

    public static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static void forceTicking(ServerLevel level, BlockPos center, UUID space) {
        level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(center), 2, space, true);
    }

    public static void forceAnchor(ServerLevel level, BlockPos center, UUID space) {
        level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(center), 2, space, true);
    }

    public static void release(ServerLevel level, BlockPos center, UUID space) {
        level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(center), 2, space);
    }

    public static void releaseTickets(ServerLevel level, BlockPos room, BlockPos anchor, UUID space) {
        release(level, room, space);
        if (!room.equals(anchor)) release(level, anchor, space);
    }

    public static void loadChunk(ServerLevel level, BlockPos pos) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
    }

    public static SpaceManager.ContainerCarrier findContainer(ServerLevel level, BlockPos center, UUID space) {
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof Container container)) continue;
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        if (space.equals(cc.sighs.cannedmonster.can.CanContents.spaceId(container.getItem(slot)))) {
                            return new SpaceManager.ContainerCarrier(entry.getKey(), slot);
                        }
                    }
                }
            }
        }
        return null;
    }

    public static ItemStack spatialCan() {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("cannedmonster:spatial_monster_can"));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static int maxY(ServerLevel level) {
        return level.getMaxBuildHeight();
    }

    public static boolean teleportPlayer(ServerPlayer player, ServerLevel level, Vec3 position, float yaw, float pitch) {
        player.teleportTo(level, position.x, position.y, position.z, yaw, pitch);
        return player.level() == level;
    }

    public static void resetMovement(ServerPlayer player) {
        SpaceDomain.raw(() -> player.connection.resetPosition());
    }

    public static void sendRoom(ServerPlayer player, SpaceManager.Space space) {
        ServerLevel level = space.level();
        RoomGeometry room = space.client();
        int chunkX = (int) room.x() >> 4;
        int chunkZ = (int) room.z() >> 4;
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
        LevelChunk chunk = new LevelChunk(level, pos);
        for (int lx = 0; lx < RoomGeometry.SIZE_X; lx++) {
            for (int ly = 0; ly < RoomGeometry.SIZE_Y; ly++) {
                for (int lz = 0; lz < RoomGeometry.SIZE_Z; lz++) {
                    BlockPos global = BlockPos.containing(room.x() + lx, room.y() + ly, room.z() + lz);
                    chunk.setBlockState(global, room.blockLocal(lx, ly, lz), false);
                }
            }
        }
        sendingRoom = true;
        try {
            player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
        } finally {
            sendingRoom = false;
        }
    }

    public static void sendResidents(ServerPlayer player, SpaceManager.Space space) {
        Map<Integer, Sent> sent = SENT.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
        sendingEntities = true;
        try {
            for (UUID id : SpaceManager.ownedEntities(space)) {
                Entity entity = SpaceManager.findEntity(space, id);
                if (entity != null && !entity.isRemoved() && !sent.containsKey(entity.getId())) {
                    addEntity(player, space, entity);
                }
            }
            for (ServerPlayer other : SpaceManager.playersIn(space, player)) {
                if (!sent.containsKey(other.getId())) addEntity(player, space, other);
            }
        } finally {
            sendingEntities = false;
        }
    }

    /** Per-tick client-space movement/rotation/head sync and visible-set reconciliation. */
    public static void sendEntityUpdates(ServerPlayer player, SpaceManager.Space space) {
        Map<Integer, Sent> sent = SENT.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
        java.util.Set<Integer> desired = new java.util.HashSet<>();
        sendingEntities = true;
        try {
            for (UUID id : SpaceManager.ownedEntities(space)) {
                Entity entity = SpaceManager.findEntity(space, id);
                if (entity == null || entity.isRemoved()) continue;
                desired.add(entity.getId());
                updateEntity(player, space, entity, sent);
            }
            for (ServerPlayer other : SpaceManager.playersIn(space, player)) {
                desired.add(other.getId());
                updateEntity(player, space, other, sent);
            }
            List<Integer> stale = new ArrayList<>();
            for (Integer id : sent.keySet()) {
                if (!desired.contains(id)) stale.add(id);
            }
            if (!stale.isEmpty()) {
                removeEntities(player, stale);
                for (Integer id : stale) sent.remove(id);
            }
        } finally {
            sendingEntities = false;
        }
    }

    private static void updateEntity(ServerPlayer player, SpaceManager.Space space, Entity entity, Map<Integer, Sent> sent) {
        Vec3 point = clientPoint(space, SpaceDomain.raw(entity::position));
        Sent last = sent.get(entity.getId());
        if (last == null) {
            addEntity(player, space, entity);
            return;
        }
        double dx = point.x - last.x();
        double dy = point.y - last.y();
        double dz = point.z - last.z();
        byte yRot = (byte) ((int) (entity.getYRot() * 256.0F / 360.0F));
        byte xRot = (byte) ((int) (entity.getXRot() * 256.0F / 360.0F));
        boolean rotated = yRot != last.yRot() || xRot != last.xRot();
        double moved = dx * dx + dy * dy + dz * dz;
        if (moved > 49.0) {
            teleportEntity(player, space, entity, point);
        } else if (moved > 1.0E-6) {
            short qx = (short) (dx * 4096.0);
            short qy = (short) (dy * 4096.0);
            short qz = (short) (dz * 4096.0);
            player.connection.send(rotated
                    ? new ClientboundMoveEntityPacket.PosRot(entity.getId(), qx, qy, qz, yRot, xRot, entity.onGround())
                    : new ClientboundMoveEntityPacket.Pos(entity.getId(), qx, qy, qz, entity.onGround()));
            point = new Vec3(last.x() + qx / 4096.0, last.y() + qy / 4096.0, last.z() + qz / 4096.0);
        } else if (rotated) {
            player.connection.send(new ClientboundMoveEntityPacket.Rot(entity.getId(), yRot, xRot, entity.onGround()));
        } else {
            point = new Vec3(last.x(), last.y(), last.z());
        }
        byte head = (byte) ((int) (entity.getYHeadRot() * 256.0F / 360.0F));
        if (head != last.head()) player.connection.send(new ClientboundRotateHeadPacket(entity, head));
        sent.put(entity.getId(), new Sent(point.x, point.y, point.z, yRot, xRot, head));
    }

    private static void addEntity(ServerPlayer player, SpaceManager.Space space, Entity entity) {
        if (entity instanceof ServerPlayer other) {
            java.util.Set<UUID> known = INFO.computeIfAbsent(player.getUUID(), key -> new java.util.HashSet<>());
            if (known.add(other.getUUID())) {
                player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(other)));
            }
        }
        Vec3 point = clientPoint(space, SpaceDomain.raw(entity::position));
        player.connection.send(new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), point.x, point.y, point.z,
                entity.getXRot(), entity.getYRot(), entity.getType(), 0, entity.getDeltaMovement(), entity.getYHeadRot()));
        player.connection.send(new ClientboundSetEntityDataPacket(entity.getId(),
                entity.getEntityData().getNonDefaultValues()));
        if (entity instanceof LivingEntity living) {
            List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) slots.add(Pair.of(slot, living.getItemBySlot(slot)));
            player.connection.send(new ClientboundSetEquipmentPacket(entity.getId(), slots));
        }
        // The client may already hold this network id from a native (pre-space) add;
        // a teleport makes the projected position authoritative in that case too.
        teleportEntity(player, space, entity, point);
        byte yRot = (byte) ((int) (entity.getYRot() * 256.0F / 360.0F));
        byte xRot = (byte) ((int) (entity.getXRot() * 256.0F / 360.0F));
        byte head = (byte) ((int) (entity.getYHeadRot() * 256.0F / 360.0F));
        SENT.computeIfAbsent(player.getUUID(), key -> new HashMap<>())
                .put(entity.getId(), new Sent(point.x, point.y, point.z, yRot, xRot, head));
    }

    private static void seedSent(ServerPlayer player, Entity entity, Vec3 point) {
        byte yRot = (byte) ((int) (entity.getYRot() * 256.0F / 360.0F));
        byte xRot = (byte) ((int) (entity.getXRot() * 256.0F / 360.0F));
        byte head = (byte) ((int) (entity.getYHeadRot() * 256.0F / 360.0F));
        SENT.computeIfAbsent(player.getUUID(), key -> new HashMap<>())
                .put(entity.getId(), new Sent(point.x, point.y, point.z, yRot, xRot, head));
    }

    private static void teleportEntity(ServerPlayer player, SpaceManager.Space space, Entity entity, Vec3 point) {
        ClientboundTeleportEntityPacket packet = new ClientboundTeleportEntityPacket(entity);
        set(packet, "x", point.x);
        set(packet, "y", point.y);
        set(packet, "z", point.z);
        player.connection.send(packet);
    }

    private static void removeEntities(ServerPlayer player, List<Integer> ids) {
        int[] raw = new int[ids.size()];
        for (int i = 0; i < raw.length; i++) raw[i] = ids.get(i);
        player.connection.send(new ClientboundRemoveEntitiesPacket(raw));
    }

    /** Internal room coordinates mapped into the client-side display room. */
    public static Vec3 clientPoint(SpaceManager.Space space, Vec3 real) {
        return new Vec3(space.client().x() + (real.x - space.internal().x()),
                space.client().y() + (real.y - space.internal().y()),
                space.client().z() + (real.z - space.internal().z()));
    }

    private static boolean atAnchor(SpaceManager.Space space, double x, double y, double z) {
        Vec3 anchor = space.lastAnchor();
        return anchor != null && anchor.distanceToSqr(x, y, z) < 4.0;
    }

    public static void restoreRoom(ServerPlayer player, SpaceManager.Space space) {
        Map<Integer, Sent> visible = SENT.remove(player.getUUID());
        INFO.remove(player.getUUID());
        List<Integer> ids = new ArrayList<>();
        if (visible != null) ids.addAll(visible.keySet());
        sendingEntities = true;
        try {
            if (!ids.isEmpty()) removeEntities(player, ids);
        } finally {
            sendingEntities = false;
        }
        RoomGeometry room = space.client();
        int chunkX = (int) room.x() >> 4;
        int chunkZ = (int) room.z() >> 4;
        LevelChunk real = (LevelChunk) space.level().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
        player.connection.send(new ClientboundLevelChunkWithLightPacket(real, space.level().getLightEngine(), null, null));
    }

    public static Packet<?> translateOutbound(ServerPlayer player, Packet<?> packet) {
        if (!SpaceManager.isInside(player)) return packet;
        SpaceManager.Space space = SpaceManager.spaceForPlayer(player);
        Vec3 offset = SpaceManager.packetOffset(player);
        if (packet instanceof ClientboundPlayerPositionPacket position) {
            if (space.internal().contains(position.getX(), position.getY(), position.getZ())) {
                return new ClientboundPlayerPositionPacket(position.getX() - offset.x, position.getY() - offset.y,
                        position.getZ() - offset.z, position.getYRot(), position.getXRot(),
                        position.getRelativeArguments(), position.getId());
            }
            if (atAnchor(space, position.getX(), position.getY(), position.getZ())) {
                Vec3 point = clientPoint(space, SpaceDomain.raw(player::position));
                return new ClientboundPlayerPositionPacket(point.x, point.y, point.z, position.getYRot(),
                        position.getXRot(), position.getRelativeArguments(), position.getId());
            }
            return packet;
        }
        if (packet instanceof ClientboundAddEntityPacket add) {
            SpaceManager.Space owner = SpaceManager.trackedSpace(add.getUUID());
            if (owner == space && space != null) {
                Entity entity = SpaceDomain.raw(() -> space.level().getEntity(add.getId()));
                if (entity != null) {
                    Vec3 point = clientPoint(space, SpaceDomain.raw(entity::position));
                    seedSent(player, entity, point);
                    return new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), point.x, point.y, point.z,
                            entity.getXRot(), entity.getYRot(), entity.getType(), 0, entity.getDeltaMovement(),
                            entity.getYHeadRot());
                }
            }
            return packet;
        }
        if (packet instanceof ClientboundSoundPacket sound) {
            if (space.internal().contains(sound.getX(), sound.getY(), sound.getZ())) {
                return new ClientboundSoundPacket(sound.getSound(), sound.getSource(), sound.getX() - offset.x,
                        sound.getY() - offset.y, sound.getZ() - offset.z, sound.getVolume(), sound.getPitch(), sound.getSeed());
            }
            if (atAnchor(space, sound.getX(), sound.getY(), sound.getZ())) {
                Vec3 point = space.client().spawn();
                return new ClientboundSoundPacket(sound.getSound(), sound.getSource(), point.x, point.y, point.z,
                        sound.getVolume(), sound.getPitch(), sound.getSeed());
            }
            return packet;
        }
        if (packet instanceof ClientboundLevelParticlesPacket particles) {
            if (space.internal().contains(particles.getX(), particles.getY(), particles.getZ())) {
                return new ClientboundLevelParticlesPacket(particles.getParticle(), particles.isOverrideLimiter(),
                        particles.getX() - offset.x, particles.getY() - offset.y, particles.getZ() - offset.z,
                        particles.getXDist(), particles.getYDist(), particles.getZDist(), particles.getMaxSpeed(), particles.getCount());
            }
            if (atAnchor(space, particles.getX(), particles.getY(), particles.getZ())) {
                Vec3 point = space.client().spawn();
                return new ClientboundLevelParticlesPacket(particles.getParticle(), particles.isOverrideLimiter(),
                        point.x, point.y, point.z, particles.getXDist(), particles.getYDist(), particles.getZDist(),
                        particles.getMaxSpeed(), particles.getCount());
            }
            return packet;
        }
        return packet;
    }

    public static boolean dropChunkPacket(ServerPlayer player, Packet<?> packet) {
        if (sendingRoom || sendingEntities) {
            return false;
        }
        SpaceManager.Space space = SpaceManager.spaceForPlayer(player);
        if (packet instanceof ClientboundBundlePacket bundle) {
            // NeoForge pairs a newly tracked entity as one bundle; without this check the
            // inner AddEntity bypasses the per-packet filter and recreates the resident
            // at the external anchor. Manual sync supplies the projected instance.
            for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets()) {
                if (sub instanceof ClientboundAddEntityPacket add
                        && SpaceManager.trackedSpace(add.getUUID()) != null) {
                    addDropped++;
                    return true;
                }
                if (sub instanceof ClientboundTeleportEntityPacket teleport
                        && SpaceManager.spaceOfEntityId(player, teleport.getId()) != null) {
                    teleportDropped++;
                    return true;
                }
            }
            return false;
        }
        if (packet instanceof ClientboundAddEntityPacket add) {
            SpaceManager.Space owner = SpaceManager.trackedSpace(add.getUUID());
            // Inside the owning space the native add is projected by translateOutbound;
            // for external viewers and other spaces it must stay hidden.
            boolean drop = owner != null && owner != space;
            if (drop) addDropped++;
            else addSent++;
            return drop;
        }
        if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            // Same-space and cross-space teleports are all driven by the manual update
            // path; forwarding the native packet would duplicate the sync.
            boolean drop = SpaceManager.spaceOfEntityId(player, teleport.getId()) != null;
            if (drop) teleportDropped++;
            else teleportSent++;
            return drop;
        }
        if (packet instanceof ClientboundMoveEntityPacket move) {
            Entity moved = SpaceDomain.raw(() -> move.getEntity(player.level()));
            boolean drop = moved != null && SpaceManager.isTracked(moved);
            if (drop) moveDropped++;
            else moveSent++;
            return drop;
        }
        if (packet instanceof ClientboundRotateHeadPacket head) {
            Entity turned = SpaceDomain.raw(() -> head.getEntity(player.level()));
            return turned != null && SpaceManager.isTracked(turned);
        }
        if (packet instanceof ClientboundSetEntityDataPacket data) {
            SpaceManager.Space owner = SpaceManager.spaceOfEntityId(player, data.id());
            return owner != null && owner != space;
        }
        if (packet instanceof ClientboundSetEquipmentPacket equipment) {
            SpaceManager.Space owner = SpaceManager.spaceOfEntityId(player, equipment.getEntity());
            return owner != null && owner != space;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            SpaceManager.Space owner = SpaceManager.spaceOfEntityId(player, motion.getId());
            return owner != null && owner != space;
        }
        if (space == null) {
            if (packet instanceof ClientboundSoundPacket sound) {
                return SpaceManager.insideAnyRoom((ServerLevel) player.level(),
                        new Vec3(sound.getX(), sound.getY(), sound.getZ()));
            }
            if (packet instanceof ClientboundLevelParticlesPacket particles) {
                return SpaceManager.insideAnyRoom((ServerLevel) player.level(),
                        new Vec3(particles.getX(), particles.getY(), particles.getZ()));
            }
            return false;
        }
        RoomGeometry room = space.client();
        int cx = (int) room.x() >> 4;
        int cz = (int) room.z() >> 4;
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            return chunk.getX() == cx && chunk.getZ() == cz;
        }
        if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            return forget.pos().x == cx && forget.pos().z == cz;
        }
        if (packet instanceof ClientboundSoundPacket sound) {
            Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            if (space.internal().contains(at)) {
                return SpaceManager.spacesAt((ServerLevel) player.level(), at).size() > 1;
            }
            return !atAnchor(space, sound.getX(), sound.getY(), sound.getZ());
        }
        if (packet instanceof ClientboundLevelParticlesPacket particles) {
            Vec3 at = new Vec3(particles.getX(), particles.getY(), particles.getZ());
            if (space.internal().contains(at)) {
                return SpaceManager.spacesAt((ServerLevel) player.level(), at).size() > 1;
            }
            return !atAnchor(space, particles.getX(), particles.getY(), particles.getZ());
        }
        return false;
    }

    public static ServerboundMovePlayerPacket translateInbound(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        if (!SpaceManager.isInside(player)) return packet;
        Vec3 offset = SpaceManager.packetOffset(player);
        SpaceManager.Space space = SpaceManager.spaceForPlayer(player);
        if (packet instanceof ServerboundMovePlayerPacket.Pos pos) {
            if (!space.client().contains(pos.getX(0), pos.getY(0), pos.getZ(0))) return packet;
            return new ServerboundMovePlayerPacket.Pos(pos.getX(0) + offset.x, pos.getY(0) + offset.y,
                    pos.getZ(0) + offset.z, pos.isOnGround());
        }
        if (packet instanceof ServerboundMovePlayerPacket.PosRot posRot) {
            if (!space.client().contains(posRot.getX(0), posRot.getY(0), posRot.getZ(0))) return packet;
            return new ServerboundMovePlayerPacket.PosRot(posRot.getX(0) + offset.x, posRot.getY(0) + offset.y,
                    posRot.getZ(0) + offset.z, posRot.getYRot(0), posRot.getXRot(0), posRot.isOnGround());
        }
        return packet;
    }

    private static void set(Object packet, String field, double value) {
        try {
            Field target = ClientboundTeleportEntityPacket.class.getDeclaredField(field);
            target.setAccessible(true);
            target.setDouble(packet, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot project space entity position", failure);
        }
    }
}
