package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.space.SpaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Spatial client/chunk transport is only implemented on NeoForge 1.21.1. */
public final class SpacePlatform {
    private SpacePlatform() {}

    public static boolean supported() {
        return false;
    }

    public static ServerLevel level(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    public static void forceTicking(ServerLevel level, BlockPos center, UUID space) {}

    public static void forceAnchor(ServerLevel level, BlockPos center, UUID space) {}

    public static void release(ServerLevel level, BlockPos center, UUID space) {}

    public static void releaseTickets(ServerLevel level, BlockPos room, BlockPos anchor, UUID space) {}

    public static boolean sendingRoom() { return false; }

    public static boolean sendingEntities() { return false; }

    public static void beginSession(ServerPlayer player) {}

    public static void reset() {}

    public static void sendEntityUpdates(ServerPlayer player, SpaceManager.Space space) {}

    public static void loadChunk(ServerLevel level, BlockPos pos) {}

    public static SpaceManager.ContainerCarrier findContainer(ServerLevel level, BlockPos center, UUID space) { return null; }

    public static net.minecraft.world.item.ItemStack spatialCan() { return net.minecraft.world.item.ItemStack.EMPTY; }


    public static void resetMovement(ServerPlayer player) {}

    public static boolean teleportPlayer(ServerPlayer player, ServerLevel level, net.minecraft.world.phys.Vec3 position, float yaw, float pitch) {
        return player.teleportTo(level, position.x, position.y, position.z, java.util.Set.of(), yaw, pitch, true);
    }

    public static void sendRoom(ServerPlayer player, SpaceManager.Space space) {}

    public static void restoreRoom(ServerPlayer player, SpaceManager.Space space) {}

    public static void sendResidents(ServerPlayer player, SpaceManager.Space space) {}

    /** Space transport is unsupported here, so no resident can become a real world entity. */
    public static void retrack(net.minecraft.world.entity.Entity entity) {}

    public static Packet<?> translateOutbound(ServerPlayer player, Packet<?> packet) { return packet; }

    public static boolean dropChunkPacket(ServerPlayer player, Packet<?> packet) { return false; }

    public static ServerboundMovePlayerPacket translateInbound(ServerPlayer player, ServerboundMovePlayerPacket packet) { return packet; }
}
