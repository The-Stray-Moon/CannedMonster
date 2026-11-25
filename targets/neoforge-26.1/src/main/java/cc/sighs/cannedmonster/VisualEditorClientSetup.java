package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.visual.EditorAck;
import cc.sighs.cannedmonster.fusion.visual.EditorSave;
import cc.sighs.cannedmonster.fusion.visual.EditorTransport;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorClient;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorScreen;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = CannedMonsterNeoForge261.MOD_ID, value = Dist.CLIENT)
public final class VisualEditorClientSetup {
    private VisualEditorClientSetup() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        VisualEditorClient.bind(VisualEditorScreen::open);
        EditorTransport.bind(new EditorTransport.Sender() {
            @Override
            public void save(long session, int revision, int scope, CompoundTag layout) {
                ClientPacketDistributor.sendToServer(new EditorSave(session, revision, scope, layout));
            }

            @Override
            public void ack(long session, int revision, boolean ok, String message) {
                ClientPacketDistributor.sendToServer(new EditorAck(session, revision, ok, message));
            }
        });
    }
}
