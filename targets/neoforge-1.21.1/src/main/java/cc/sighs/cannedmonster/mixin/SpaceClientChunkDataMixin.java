package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceClientView;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Real chunk and biome packets are decoded into the real chunk cache. Decoding loops
 * over the chunk's section array, so it must see the real dimension height even while
 * the client view reports its own profile. Only the room reads use the view height.
 */
@Mixin(ClientPacketListener.class)
public abstract class SpaceClientChunkDataMixin {
    @WrapMethod(method = "handleLevelChunkWithLight")
    private void cannedmonster$realChunk(ClientboundLevelChunkWithLightPacket packet, Operation<Void> original) {
        SpaceClientView.pushRealHeight();
        try {
            original.call(packet);
        } finally {
            SpaceClientView.popRealHeight();
        }
    }

    @WrapMethod(method = "handleChunksBiomes")
    private void cannedmonster$realBiomes(ClientboundChunksBiomesPacket packet, Operation<Void> original) {
        SpaceClientView.pushRealHeight();
        try {
            original.call(packet);
        } finally {
            SpaceClientView.popRealHeight();
        }
    }
}
