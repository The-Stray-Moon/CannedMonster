package cc.sighs.cannedmonster.platform;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlayerAccess {
    private PlayerAccess() {}

    public static void copyContents(ItemStack source, ItemStack target) {
        target.set(DataComponents.CUSTOM_DATA, source.get(DataComponents.CUSTOM_DATA));
    }

    public static boolean same(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameComponents(first, second);
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

    public static void syncInventory(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            server.inventoryMenu.broadcastChanges();
        }
    }
}
