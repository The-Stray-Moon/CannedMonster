package cc.sighs.cannedmonster.can;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MonsterCanItem extends BaseCanItem {
    public MonsterCanItem(Properties properties, boolean large, boolean throwable, boolean random, boolean spatial,
                          Supplier<? extends EntityType<ThrownCan>> projectileType) {
        super(properties, large, throwable, random, spatial, projectileType);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (storesMobs() && storesFusion() && player.isShiftKeyDown() && CanContents.valid(stack)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            return player instanceof net.minecraft.server.level.ServerPlayer server
                    && cc.sighs.cannedmonster.fusion.visual.VisualEditorServer.openHeld(server, hand)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return beginUse(level, player, hand) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return duration(stack); }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return throwable ? ItemUseAnimation.BOW : CanContents.filled(stack) ? ItemUseAnimation.EAT : ItemUseAnimation.NONE;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTicks) {
        return release(stack, level, entity, remainingTicks);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip(stack, tooltip);
    }
}
