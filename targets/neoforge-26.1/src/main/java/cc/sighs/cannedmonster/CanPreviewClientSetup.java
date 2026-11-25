package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.can.CanPreviewTooltip;
import cc.sighs.cannedmonster.fusion.visual.CanPreviewClientTooltip;
import cc.sighs.cannedmonster.fusion.visual.CanPreviewHosts;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = CannedMonsterNeoForge261.MOD_ID, value = Dist.CLIENT)
public final class CanPreviewClientSetup {
    private CanPreviewClientSetup() {}

    @SubscribeEvent
    public static void factories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(CanPreviewTooltip.class, CanPreviewClientTooltip::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(CanPreviewClientSetup::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        CanPreviewHosts.tick();
    }
}
