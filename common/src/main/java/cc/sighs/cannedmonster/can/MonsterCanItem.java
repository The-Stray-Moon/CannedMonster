package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.fusion.PlayerFusion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class MonsterCanItem extends Item {
    private static final int EAT_DURATION = 32;
    private static final Map<Player, Press> PRESSES = new WeakHashMap<>();
    private static java.util.function.Consumer<Player> clientFinished = player -> {};

    public static void setClientFinished(java.util.function.Consumer<Player> listener) {
        clientFinished = listener;
    }

    private static Press takePress(Player player, ItemStack stack) {
        Press press = PRESSES.remove(player);
        if (press == null || press.original != stack || player.getItemInHand(press.hand) != stack
                || !ItemStack.isSameItemSameComponents(press.snapshot, stack)
                || press.empty == CanContents.filled(stack)) return null;
        return press;
    }

    private record Press(boolean empty, InteractionHand hand, ItemStack original, ItemStack snapshot) {}

    private final boolean large;
    private final boolean throwable;
    private final boolean random;
    private final boolean spatial;
    private final Supplier<? extends EntityType<ThrownCan>> projectileType;

    public MonsterCanItem(boolean large, boolean throwable, boolean random, boolean spatial,
                          Supplier<? extends EntityType<ThrownCan>> projectileType) {
        super(new Properties().stacksTo(1));
        this.large = large;
        this.throwable = throwable;
        this.random = random;
        this.spatial = spatial;
        this.projectileType = projectileType;
    }

    public boolean storesFusion() { return large; }
    public boolean throwable() { return throwable; }
    public boolean random() { return random; }
    public boolean storesMobs() { return !random && !spatial; }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (throwable || spatial) return InteractionResult.PASS;
        if (!(target instanceof Mob mob)) return InteractionResult.FAIL;
        if (target.level().isClientSide) return InteractionResult.SUCCESS;
        return CanActions.onMob(stack, mob, player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (throwable || spatial) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        if (!CanContents.filled(stack)) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Vec3 position = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        return CanActions.release(stack, level, position) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (throwable) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (spatial || random) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) PRESSES.put(player, new Press(!CanContents.filled(stack), hand, stack, stack.copy()));
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (throwable || !storesMobs() || !(entity instanceof Player player)) return stack;
        if (level.isClientSide) {
            clientFinished.accept(player);
            return stack;
        }
        boolean completed = player.isUsingItem() && player.getUseItem() == stack && player.getUseItemRemainingTicks() <= 0;
        Press press = takePress(player, stack);
        if (!completed || press == null || press.empty) return stack;
        if (PlayerFusion.fuse(player, stack)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        if (!storesMobs() && !throwable) return 0;
        return !throwable && CanContents.filled(stack) ? EAT_DURATION : 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return throwable ? UseAnim.BOW : CanContents.filled(stack) ? UseAnim.EAT : UseAnim.NONE;
    }

    public static float power(int ticks) {
        float time = Math.max(0, ticks) / 20.0F;
        return Math.min(1.0F, (time * time + time * 2.0F) / 3.0F);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTicks) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player) || stack.isEmpty()) return;
        if (throwable) {
            float strength = power(getUseDuration(stack, entity) - remainingTicks);
            if (strength < 0.1F) return;
            ThrownCan projectile = new ThrownCan(projectileType.get(), level);
            projectile.setOwner(player);
            projectile.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
            projectile.setItem(stack.copyWithCount(1));
            float spread = player.getXRot() <= -60.0F ? 0.0F : 0.5F;
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, strength * 1.8F, spread);
            if (!server.addFreshEntity(projectile)) return;
            stack.shrink(1);
            player.getCooldowns().addCooldown(this, 10);
            player.awardStat(Stats.ITEM_USED.get(this));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.9F + strength * 0.3F);
            return;
        }
        if (spatial || random) return;
        Press press = takePress(player, stack);
        if (press == null || !press.empty) return;
        if (PlayerFusion.extract(player, stack)) {
            player.getCooldowns().addCooldown(this, 10);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity instanceof Player player && !player.level().isClientSide) PRESSES.remove(player);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return CanContents.filled(stack) || super.isFoil(stack); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (spatial) {
            tooltip.add(Component.translatable("tooltip.cannedmonster.spatial_pending").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (random) {
            tooltip.add(Component.translatable("tooltip.cannedmonster.random").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(CanContents.filled(stack)
                    ? Component.translatable("tooltip.cannedmonster.contains", CanContents.name(stack)).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("tooltip.cannedmonster.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(large ? "tooltip.cannedmonster.large" : "tooltip.cannedmonster.small").withStyle(ChatFormatting.GRAY));
            if (!throwable) tooltip.add(Component.translatable("tooltip.cannedmonster.eat").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(throwable ? "tooltip.cannedmonster.throw" : "tooltip.cannedmonster.hand").withStyle(ChatFormatting.GRAY));
    }
}
