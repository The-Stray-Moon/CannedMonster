package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.can.MonsterCanItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = CannedMonsterNeoForge261.MOD_ID, value = Dist.CLIENT)
public final class CanUseClientEvents {
    private static Player owner;
    private static boolean awaitingRelease;

    static {
        MonsterCanItem.setClientFinished(CanUseClientEvents::finished);
    }

    private CanUseClientEvents() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(CanUseClientEvents::tick);
        NeoForge.EVENT_BUS.addListener(CanUseClientEvents::input);
    }

    private static void finished(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player) {
            owner = player;
            awaitingRelease = minecraft.options.keyUse.isDown();
        }
    }

    private static void tick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != owner || !minecraft.options.keyUse.isDown()) {
            awaitingRelease = false;
        }
        owner = minecraft.player;
    }

    private static void input(InputEvent.InteractionKeyMappingTriggered event) {
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
