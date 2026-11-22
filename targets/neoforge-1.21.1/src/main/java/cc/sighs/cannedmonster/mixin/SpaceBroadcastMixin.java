package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceDomain;
import cc.sighs.cannedmonster.space.SpaceManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Sound/particle range checks compare against the projected external anchor, so an
 * inside player would never receive a sound emitted at the room's own coordinates.
 * Re-deliver those packets using the real room position; the send path then projects
 * them into the client room.
 */
@Mixin(PlayerList.class)
public abstract class SpaceBroadcastMixin {
    @WrapMethod(method = "broadcast")
    private void cannedmonster$broadcastInRoom(Player except, double x, double y, double z, double range,
                                               ResourceKey<Level> dimension, Packet<?> packet, Operation<Void> original) {
        original.call(except, x, y, z, range, dimension, packet);
        if (range <= 0.0 || SpaceManager.spaceCount() == 0) return;
        for (ServerPlayer player : ((PlayerList) (Object) this).getPlayers()) {
            if (player == except || player.level().dimension() != dimension) continue;
            SpaceManager.Space space = SpaceManager.spaceForPlayer(player);
            if (space == null || !space.internal().contains(x, y, z)) continue;
            // Mirror the vanilla check against the projected anchor: if it already
            // delivered the packet, do not deliver a second copy.
            Vec3 projected = new Vec3(player.getX(), player.getY(), player.getZ());
            if (projected.distanceToSqr(x, y, z) < range * range) continue;
            Vec3 real = SpaceDomain.raw(player::position);
            if (real.distanceToSqr(x, y, z) < range * range) player.connection.send(packet);
        }
    }
}
