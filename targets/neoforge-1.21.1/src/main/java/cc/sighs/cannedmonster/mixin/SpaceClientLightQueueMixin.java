package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceClientView;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Queued light updates are applied outside packet handling. They address real light
 * sections, so they must run with the real height profile; the room has its own light
 * values and never depends on this queue.
 */
@Mixin(ClientLevel.class)
public abstract class SpaceClientLightQueueMixin {
    @WrapMethod(method = "pollLightUpdates")
    private void cannedmonster$realLightSections(Operation<Void> original) {
        SpaceClientView.pushRealHeight();
        try {
            original.call();
        } finally {
            SpaceClientView.popRealHeight();
        }
    }
}
