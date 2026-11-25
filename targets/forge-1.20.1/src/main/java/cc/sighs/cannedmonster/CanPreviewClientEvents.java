package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.visual.CanPreviewHosts;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CannedMonsterForge.MOD_ID, value = Dist.CLIENT)
public final class CanPreviewClientEvents {
    private CanPreviewClientEvents() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) CanPreviewHosts.tick();
    }
}
