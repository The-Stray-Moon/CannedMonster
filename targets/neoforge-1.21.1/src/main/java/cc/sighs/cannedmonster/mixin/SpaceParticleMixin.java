package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceDomain;
import cc.sighs.cannedmonster.space.SpaceManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * The vanilla particle range check uses blockPosition(), which is projected to the
 * external anchor for space entities, so room particles would never reach an inside
 * player. Re-deliver them with the real room position; the send path then projects
 * the packet into the client room.
 */
@Mixin(ServerLevel.class)
public abstract class SpaceParticleMixin {
    @Shadow @Final
    List<ServerPlayer> players;

    @WrapMethod(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I")
    private <T extends ParticleOptions> int cannedmonster$particles(T particle, double x, double y, double z, int count,
                                                                    double xDist, double yDist, double zDist, double speed,
                                                                    Operation<Integer> original) {
        int result = original.call(particle, x, y, z, count, xDist, yDist, zDist, speed);
        if (SpaceManager.spaceCount() == 0) return result;
        ClientboundLevelParticlesPacket packet = null;
        for (ServerPlayer player : players) {
            SpaceManager.Space space = SpaceManager.spaceForPlayer(player);
            if (space == null || !space.internal().contains(x, y, z)) continue;
            // The vanilla range check uses blockPosition(), which is projected to the
            // anchor. If that check already sent the packet, skip the second copy.
            if (player.blockPosition().closerToCenterThan(new Vec3(x, y, z), 32.0)) continue;
            Vec3 real = SpaceDomain.raw(player::position);
            if (real.distanceToSqr(x, y, z) > 1024.0) continue;
            if (packet == null) {
                packet = new ClientboundLevelParticlesPacket(particle, false, x, y, z,
                        (float) xDist, (float) yDist, (float) zDist, (float) speed, count);
            }
            player.connection.send(packet);
        }
        return result;
    }
}
