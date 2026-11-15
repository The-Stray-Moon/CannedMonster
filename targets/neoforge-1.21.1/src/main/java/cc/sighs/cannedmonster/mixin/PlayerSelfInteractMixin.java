package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The client only sends the use packet after block and entity interactions declined to consume the
 * action, so the tail of the server use handler is the point where the whole interaction chain has
 * produced nothing. Give the fused members a chance to answer the player's own right click there.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerSelfInteractMixin {
    @Inject(method = "useItem", at = @At("RETURN"), cancellable = true)
    private void cannedmonster$selfInteract(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != InteractionResult.PASS) return;
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(stack.getItem())) return;
        InteractionResult result = FusionRuntime.selfInteract(player, hand);
        if (!result.consumesAction()) return;
        if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
        if (!player.isUsingItem()) player.inventoryMenu.sendAllDataToRemote();
        if (!result.shouldSwing()) player.swing(hand, true);
        cir.setReturnValue(result);
    }
}
