package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionRiding;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RidingNetworkMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientVehicleIsFloating;
    @Shadow private int aboveGroundVehicleTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cannedmonster$serverPhysics(CallbackInfo callback) {
        if (FusionRiding.authoritative(player.getRootVehicle())) {
            clientVehicleIsFloating = false;
            aboveGroundVehicleTickCount = 0;
        }
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$position(ServerboundMoveVehiclePacket packet, CallbackInfo callback) {
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet,
                (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
        if (FusionRiding.authoritative(player.getRootVehicle())) callback.cancel();
    }
}
