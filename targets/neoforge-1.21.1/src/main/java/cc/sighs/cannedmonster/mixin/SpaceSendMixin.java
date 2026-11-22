package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.platform.SpacePlatform;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class SpaceSendMixin {
    /**
     * The drop decision and the projection must both see the original packet: a
     * ModifyVariable-based projection runs before HEAD injections and would make the
     * drop check classify already-projected coordinates a second time.
     */
    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V")
    private void cannedmonster$spaceSend(Packet<?> packet, PacketSendListener listener, Operation<Void> original) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl game)) {
            original.call(packet, listener);
            return;
        }
        if (SpacePlatform.sendingRoom() || SpacePlatform.sendingEntities()) {
            original.call(packet, listener);
            return;
        }
        boolean sound = packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket;
        boolean particle = packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
        if (SpacePlatform.dropChunkPacket(game.player, packet)) {
            if (sound || particle) SpacePlatform.countPacket(true, sound, particle);
            return;
        }
        if (sound || particle) SpacePlatform.countPacket(false, sound, particle);
        original.call(SpacePlatform.translateOutbound(game.player, packet), listener);
    }
}
