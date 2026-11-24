package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceClientView;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Real world updates must never reach the detached space room: ClientLevel applies
 * them through the chunk data source, which is redirected to the room while a
 * session is active. They are applied to the real cached chunk instead (creating
 * block entities and queueing light like vanilla), so the real world is current at
 * exit. Updates for chunks that are not cached are kept and replayed on exit; a
 * chunk that loads later carries its own current data.
 */
@Mixin(ClientPacketListener.class)
public abstract class SpaceClientPacketMixin {
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (!SpaceClientView.active()) return;
        if (!SpaceClientView.applyBlock(packet.getPos(), packet.getBlockState())) {
            SpaceClientView.pending(packet.getPos(), packet.getBlockState());
        }
        ci.cancel();
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$chunkBlocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        if (!SpaceClientView.active()) return;
        packet.runUpdates((pos, state) -> {
            if (!SpaceClientView.applyBlock(pos, state)) SpaceClientView.pending(pos, state);
        });
        ci.cancel();
    }

    @Inject(method = "handleBlockEntityData", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$blockEntity(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        if (!SpaceClientView.active()) return;
        if (!SpaceClientView.applyBlockEntity(packet.getPos(), packet.getType(), packet.getTag())) {
            SpaceClientView.pendingBlockEntity(packet.getPos(), packet.getTag());
        }
        ci.cancel();
    }

    @Inject(method = "handleBlockDestruction", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$blockBreak(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
        if (SpaceClientView.active()) ci.cancel();
    }
}
