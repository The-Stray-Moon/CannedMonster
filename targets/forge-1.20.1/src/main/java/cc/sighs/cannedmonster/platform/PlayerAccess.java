package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.can.CanContents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlayerAccess {
    private PlayerAccess() {}

    public static void copyContents(ItemStack source, ItemStack target) {
        CanContents.write(target, source.getTag().getCompound(CanContents.KEY).copy());
    }

    public static boolean same(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameTags(first, second);
    }

    public static void cooldown(Player player, ItemStack stack, net.minecraft.world.item.Item item) {
        player.getCooldowns().addCooldown(item, 10);
    }

    public static void positionProjectile(cc.sighs.cannedmonster.can.ThrownCan projectile, Player player) {
        projectile.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
    }

    public static void message(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}
