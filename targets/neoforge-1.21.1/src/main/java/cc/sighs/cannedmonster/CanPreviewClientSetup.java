package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.can.CanPreviewTooltip;
import cc.sighs.cannedmonster.fusion.visual.NeoForgePreviewBounds;
import cc.sighs.cannedmonster.fusion.visual.PreviewBounds;
import cc.sighs.cannedmonster.fusion.visual.client.CanPreviewClientTooltip;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

@EventBusSubscriber(modid = CannedMonsterNeoForge121.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CanPreviewClientSetup {
    private CanPreviewClientSetup() {}

    @SubscribeEvent
    public static void factories(RegisterClientTooltipComponentFactoriesEvent event) {
        PreviewBounds.bind(new NeoForgePreviewBounds());
        event.register(CanPreviewTooltip.class, CanPreviewClientTooltip::new);
    }
}
