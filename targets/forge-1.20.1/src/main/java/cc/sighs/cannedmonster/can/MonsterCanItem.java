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

import javax.annotation.Nullable;
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
        if (storesMobs() && storesFusion() && player.isShiftKeyDown() && CanContents.valid(stack)) {
            if (level.isClientSide()) return InteractionResultHolder.success(stack);
            return player instanceof net.minecraft.server.level.ServerPlayer server
                    && cc.sighs.cannedmonster.fusion.visual.VisualEditorServer.openHeld(server, hand)
                    ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
        }
        return beginUse(level, player, hand) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) { return duration(stack); }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return throwable ? UseAnim.BOW : CanContents.filled(stack) ? UseAnim.EAT : UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTicks) {
        release(stack, level, entity, remainingTicks);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip(stack, tooltip::add);
    }
}
