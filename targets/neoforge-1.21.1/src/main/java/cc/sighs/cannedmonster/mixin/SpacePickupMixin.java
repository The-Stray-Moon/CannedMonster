package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An inside player's projected anchor box must not pick up world items (especially
 * the anchor can). External players and hoppers still take the can normally, and
 * SpaceManager.updateCarrier then follows the new carrier.
 */
@Mixin(ItemEntity.class)
public abstract class SpacePickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$spacePickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer server
                && SpaceManager.blocksPickup(server, (ItemEntity) (Object) this)) ci.cancel();
    }
}
