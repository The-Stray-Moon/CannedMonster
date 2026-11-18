package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.FusionMotion;
import cc.sighs.cannedmonster.fusion.visual.ClientAppearance;
import cc.sighs.cannedmonster.fusion.visual.ClientRiding;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = CannedMonsterNeoForge261.MOD_ID, value = Dist.CLIENT)
public final class FusionClientEvents {
    static {
        FusionMotion.setClientView(new FusionMotion.ClientView() {
            @Override
            public boolean fusion(Mob body) { return ClientRiding.fusion(body); }
            @Override
            public int controller(Mob body) { return ClientRiding.controller(body); }
        });
    }

    private FusionClientEvents() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(FusionClientEvents::leave);
        NeoForge.EVENT_BUS.addListener(FusionClientEvents::tick);
    }

    private static void leave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        ClientRiding.leave(event.getEntity());
    }

    private static void tick(ClientTickEvent.Post event) {
        ClientAppearance.tick();
        ClientRiding.tick();
    }
}
