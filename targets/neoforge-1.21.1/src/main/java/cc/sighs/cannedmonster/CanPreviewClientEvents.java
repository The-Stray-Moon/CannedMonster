package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.visual.CanPreviewHosts;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CannedMonsterNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class CanPreviewClientEvents {
    private CanPreviewClientEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        CanPreviewHosts.tick();
    }
}
