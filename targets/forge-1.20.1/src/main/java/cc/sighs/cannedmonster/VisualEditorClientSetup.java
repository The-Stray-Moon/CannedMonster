package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.FusionNetwork;
import cc.sighs.cannedmonster.fusion.visual.EditorAck;
import cc.sighs.cannedmonster.fusion.visual.EditorSave;
import cc.sighs.cannedmonster.fusion.visual.EditorTransport;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorClient;
import cc.sighs.cannedmonster.fusion.visual.client.Forge1201VisualEditorScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CannedMonsterForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VisualEditorClientSetup {
    private VisualEditorClientSetup() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        VisualEditorClient.bind(Forge1201VisualEditorScreen::open);
        EditorTransport.bind(new EditorTransport.Sender() {
            @Override
            public void save(long session, int revision, int scope, CompoundTag layout) {
                FusionNetwork.sendToServer(new EditorSave(session, revision, scope, layout));
            }

            @Override
            public void ack(long session, int revision, boolean ok, String message) {
                FusionNetwork.sendToServer(new EditorAck(session, revision, ok, message));
            }
        });
    }
}
