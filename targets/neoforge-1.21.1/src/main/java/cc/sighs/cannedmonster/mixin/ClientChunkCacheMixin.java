package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceClientView;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a space session is active the client reads chunks from the space view instead
 * of the real world. Only FULL chunk reads are redirected so the real light engine
 * (which reads EMPTY chunks through the same source) keeps working on real chunks.
 */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
            at = @At("HEAD"), cancellable = true)
    private void cannedmonster$spaceChunk(int x, int z, ChunkStatus status, boolean load,
                                          CallbackInfoReturnable<LevelChunk> cir) {
        if (status != ChunkStatus.FULL) return;
        if (SpaceClientView.realHeight()) return;
        LevelChunk chunk = SpaceClientView.chunk(x, z);
        if (chunk != null) cir.setReturnValue(chunk);
    }
}
