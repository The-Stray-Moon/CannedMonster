package cc.sighs.cannedmonster;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CannedMonsterNeoForge261.MOD_ID, value = Dist.CLIENT)
public final class CanClient {
    private CanClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CanRegistration.THROWN_CAN.get(), ThrownItemRenderer::new);
    }
}
