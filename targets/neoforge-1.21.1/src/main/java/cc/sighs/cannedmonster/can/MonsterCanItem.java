package cc.sighs.cannedmonster.can;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Supplier;

public final class MonsterCanItem extends BaseCanItem {
    public MonsterCanItem(boolean large, boolean throwable, boolean random, boolean spatial,
                          Supplier<? extends EntityType<ThrownCan>> projectileType) {
        super(new Properties(), large, throwable, random, spatial, projectileType);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (spatial()) {
            if (level.isClientSide()) {
                // Creative clients echo their predicted slot back to the server; clearing the
                // local hand prevents that echo from restoring the can after entry.
                player.setItemInHand(hand, ItemStack.EMPTY);
                return InteractionResultHolder.success(ItemStack.EMPTY);
            }
            if (!(player instanceof net.minecraft.server.level.ServerPlayer server)) return InteractionResultHolder.fail(stack);
            // ServerPlayerGameMode writes the returned stack back into the hand. Returning
            // the original stack would undo SpaceManager.enter clearing it.
            return cc.sighs.cannedmonster.space.SpaceManager.enter(server, stack, hand)
                    ? InteractionResultHolder.success(ItemStack.EMPTY) : InteractionResultHolder.fail(stack);
        }
        return beginUse(level, player, hand) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return duration(stack); }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return throwable ? UseAnim.BOW : CanContents.filled(stack) ? UseAnim.EAT : UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTicks) {
        release(stack, level, entity, remainingTicks);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip(stack, tooltip::add);
    }
}
