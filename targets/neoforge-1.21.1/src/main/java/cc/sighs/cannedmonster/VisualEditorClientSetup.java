package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.visual.EditorAck;
import cc.sighs.cannedmonster.fusion.visual.EditorSave;
import cc.sighs.cannedmonster.fusion.visual.EditorTransport;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorClient;
import cc.sighs.cannedmonster.fusion.visual.client.NeoForge121VisualEditorScreen;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CannedMonsterNeoForge121.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VisualEditorClientSetup {
    private VisualEditorClientSetup() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        VisualEditorClient.bind(NeoForge121VisualEditorScreen::open);
        EditorTransport.bind(new EditorTransport.Sender() {
            @Override
            public void save(long session, int revision, int scope, CompoundTag layout) {
                PacketDistributor.sendToServer(new EditorSave(session, revision, scope, layout));
            }

            @Override
            public void ack(long session, int revision, boolean ok, String message) {
                PacketDistributor.sendToServer(new EditorAck(session, revision, ok, message));
            }
        });
    }
}
