package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.fusion.visual.EditorAck;
import cc.sighs.cannedmonster.fusion.visual.EditorOpen;
import cc.sighs.cannedmonster.fusion.visual.EditorResult;
import cc.sighs.cannedmonster.fusion.visual.EditorSave;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorClient;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the four editor payloads on every target that uses NeoForge payloads. */
public final class EditorNetwork {
    public static final String VERSION = "4";

    private EditorNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(EditorNetwork::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(EditorOpen.TYPE, EditorOpen.CODEC,
                (packet, context) -> context.enqueueWork(() -> VisualEditorClient.open(packet.hand(),
                        packet.session(), packet.revision(), packet.layout(), packet.admin())));
        registrar.playToClient(EditorResult.TYPE, EditorResult.CODEC,
                (packet, context) -> context.enqueueWork(() -> VisualEditorClient.ack(packet.session(),
                        packet.revision(), packet.ok(), packet.message())));
        registrar.playToServer(EditorSave.TYPE, EditorSave.CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        VisualEditorServer.handleSave(player, packet.session(), packet.revision(),
                                packet.scope(), packet.layout());
                    }
                }));
        registrar.playToServer(EditorAck.TYPE, EditorAck.CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        VisualEditorServer.handleClose(player, packet.session(), packet.revision(),
                                packet.ok(), packet.message());
                    }
                }));
    }
}
