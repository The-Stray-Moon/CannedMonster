package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.ClientMemberEvents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class ClientMemberEventMixin {
    @Redirect(method = "handleEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;getEntity(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private Entity cannedmonster$member(ClientboundEntityEventPacket packet, Level level) {
        return ClientMemberEvents.eventEntity(packet.getEntity(level));
    }
}
