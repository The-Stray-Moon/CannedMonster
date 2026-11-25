package cc.sighs.cannedmonster.platform;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
        player.getCooldowns().addCooldown(stack, 10);
    }

    public static void positionProjectile(cc.sighs.cannedmonster.can.ThrownCan projectile, Player player) {
        projectile.snapTo(player.getX(), player.getEyeY() - 0.1, player.getZ());
    }

    public static void message(Player player, Component message) {
        if (player instanceof ServerPlayer server) server.sendSystemMessage(message, true);
        else player.sendSystemMessage(message);
    }

    public static void syncInventory(Player player) {
        if (player instanceof ServerPlayer server) server.inventoryMenu.broadcastChanges();
    }

    public static boolean admin(Player player) {
        return player != null && player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }
}
