package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.InteractionCalls;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerInteractionMixin {
    @Redirect(method = "interactOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;interactLivingEntity(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult cannedmonster$item(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        return InteractionCalls.item(stack, player, entity, hand);
    }
}
