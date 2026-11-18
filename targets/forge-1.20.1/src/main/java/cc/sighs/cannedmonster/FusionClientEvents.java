package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.FusionMotion;
import cc.sighs.cannedmonster.fusion.visual.ClientAppearance;
import cc.sighs.cannedmonster.fusion.visual.ClientRiding;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CannedMonsterForge.MOD_ID, value = Dist.CLIENT)
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
    public static void leave(EntityLeaveLevelEvent event) {
        ClientRiding.leave(event.getEntity());
    }

    @SubscribeEvent
    public static void effects(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        float partialTick = event.getPartialTick();
        var buffers = minecraft.renderBuffers().bufferSource();
        for (var body : ClientAppearance.bodies()) {
            var position = body.getPosition(partialTick);
            poses.pushPose();
            try {
                poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
                cc.sighs.cannedmonster.fusion.visual.FusionRenderer.renderEffects(body, partialTick, poses, buffers, 15728880);
            } finally { poses.popPose(); }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientAppearance.tick();
        ClientRiding.tick();
    }
}
