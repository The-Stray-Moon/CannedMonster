package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.can.CanPreviewTooltip;
import cc.sighs.cannedmonster.fusion.visual.LegacyPreviewBounds;
import cc.sighs.cannedmonster.fusion.visual.PreviewBounds;
import cc.sighs.cannedmonster.fusion.visual.client.CanPreviewClientTooltip;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CannedMonsterForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CanPreviewClientSetup {
    private CanPreviewClientSetup() {}

    @SubscribeEvent
    public static void factories(RegisterClientTooltipComponentFactoriesEvent event) {
        PreviewBounds.bind(new LegacyPreviewBounds());
        event.register(CanPreviewTooltip.class, CanPreviewClientTooltip::new);
    }
}
