package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.can.MonsterCanItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = CannedMonsterNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class CanUseClientEvents {
    private static Player owner;
    private static boolean awaitingRelease;

    static {
        MonsterCanItem.setClientFinished(CanUseClientEvents::finished);
    }

    private CanUseClientEvents() {}

    private static void finished(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player) {
            owner = player;
            awaitingRelease = minecraft.options.keyUse.isDown();
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != owner || !minecraft.options.keyUse.isDown()) {
            awaitingRelease = false;
        }
        owner = minecraft.player;
    }

    @SubscribeEvent
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || minecraft.player == null) return;
        if (minecraft.player.getItemInHand(event.getHand()).getItem() instanceof MonsterCanItem item
                && item.storesMobs() && !item.throwable()) {
            if (awaitingRelease) {
                event.setSwingHand(false);
                event.setCanceled(true);
            } else {
                owner = minecraft.player;
                awaitingRelease = minecraft.options.keyUse.isDown();
            }
        }
    }
}
