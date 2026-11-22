package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.platform.SpacePlatform;
import cc.sighs.cannedmonster.space.SpaceDomain;
import cc.sighs.cannedmonster.space.SpaceManager;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpacePacketMixin {
    @Shadow private double firstGoodX;
    @Shadow private double firstGoodY;
    @Shadow private double firstGoodZ;
    @Shadow private double lastGoodX;
    @Shadow private double lastGoodY;
    @Shadow private double lastGoodZ;

    @Unique
    private int cannedmonster$spaceFrames;

    @Unique
    private void cannedmonster$pushFrame() {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        if (listener.player.level().getServer() == null || !listener.player.level().getServer().isSameThread()) return;
        SpaceDomain.push(listener.player);
        cannedmonster$spaceFrames++;
    }

    @Unique
    private void cannedmonster$popFrame() {
        if (cannedmonster$spaceFrames <= 0) return;
        SpaceDomain.pop();
        cannedmonster$spaceFrames--;
    }

    /**
     * The listener resets firstGood/lastGood every tick from the entity position and
     * then snaps the player to them. That must use the real room coordinates, not the
     * external anchor projection, or the server player is pulled out of the room.
     */
    @Inject(method = "resetPosition", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$resetPositionRaw(CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        net.minecraft.world.phys.Vec3 real = SpaceDomain.raw(listener.player::position);
        firstGoodX = real.x;
        firstGoodY = real.y;
        firstGoodZ = real.z;
        lastGoodX = real.x;
        lastGoodY = real.y;
        lastGoodZ = real.z;
        ci.cancel();
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void cannedmonster$anchorMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        if (listener.player.level().getServer() == null || !listener.player.level().getServer().isSameThread()) return;
        if (SpaceManager.isInside(listener.player)) {
            net.minecraft.world.phys.Vec3 real = SpaceDomain.raw(listener.player::position);
            firstGoodX = real.x;
            firstGoodY = real.y;
            firstGoodZ = real.z;
            lastGoodX = real.x;
            lastGoodY = real.y;
            lastGoodZ = real.z;
        }
    }

    /** A packet handled from the network thread is re-dispatched; its frame must not leak. */
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void cannedmonster$pushMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        cannedmonster$pushFrame();
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void cannedmonster$popMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        cannedmonster$popFrame();
    }

    /**
     * Inside a space the only legitimate client position is inside the client display
     * room. Anything else (a stale pre-teleport packet at the anchor, or a spoofed
     * position) must be dropped instead of moving the server player out of the room.
     */
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$rejectOutsideRoom(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        boolean hasPos = packet instanceof ServerboundMovePlayerPacket.Pos
                || packet instanceof ServerboundMovePlayerPacket.PosRot;
        if (!hasPos || !SpaceManager.isInside(listener.player)) return;
        SpaceManager.Space space = SpaceManager.spaceForPlayer(listener.player);
        if (space == null) return;
        // The argument may already be translated to room coordinates by projectMove:
        // accept both the raw client-room position and the translated room position.
        if (space.client().contains(packet.getX(0), packet.getY(0), packet.getZ(0))
                || space.internal().contains(packet.getX(0), packet.getY(0), packet.getZ(0))) {
            SpaceManager.validMove(listener.player);
            return;
        }
        // On the network thread the packet is re-dispatched to the server thread,
        // where the same check cancels it and re-places the client in the room.
        if (listener.player.level().getServer() == null || !listener.player.level().getServer().isSameThread()) return;
        ci.cancel();
        SpaceManager.resync(listener.player);
    }

    @ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
    private ServerboundMovePlayerPacket cannedmonster$projectMove(ServerboundMovePlayerPacket packet) {
        return SpacePlatform.translateInbound(((ServerGamePacketListenerImpl) (Object) this).player, packet);
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"))
    private void cannedmonster$pushUse(ServerboundUseItemPacket packet, CallbackInfo ci) {
        cannedmonster$pushFrame();
    }

    @Inject(method = "handleUseItem", at = @At("RETURN"))
    private void cannedmonster$popUse(ServerboundUseItemPacket packet, CallbackInfo ci) {
        cannedmonster$popFrame();
    }

    @Inject(method = "handleInteract", at = @At("HEAD"))
    private void cannedmonster$pushInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        cannedmonster$pushFrame();
    }

    @Inject(method = "handleInteract", at = @At("RETURN"))
    private void cannedmonster$popInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        cannedmonster$popFrame();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$spaceAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        if (!SpaceManager.isInside(listener.player)) return;
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK
                || action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$spaceUseOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        if (!SpaceManager.isInside(listener.player)) return;
        if (SpaceManager.clickedAt(listener.player, packet.getHitResult().getBlockPos())) {
            SpaceManager.exit(listener.player);
        }
        ci.cancel();
    }
}
