package cc.sighs.cannedmonster.fusion;

import cc.sighs.cannedmonster.CannedMonsterForge;
import cc.sighs.cannedmonster.fusion.visual.AppearanceSync;
import cc.sighs.cannedmonster.fusion.visual.ClientAppearance;
import cc.sighs.cannedmonster.fusion.visual.ClientRiding;
import cc.sighs.cannedmonster.fusion.visual.EditorAck;
import cc.sighs.cannedmonster.fusion.visual.EditorOpen;
import cc.sighs.cannedmonster.fusion.visual.EditorResult;
import cc.sighs.cannedmonster.fusion.visual.EditorSave;
import cc.sighs.cannedmonster.fusion.visual.FusionAppearance;
import cc.sighs.cannedmonster.fusion.visual.MemberEvent;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorClient;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class FusionNetwork {
    private static final String VERSION = "4";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CannedMonsterForge.MOD_ID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private FusionNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, FusionAppearance.class,
                (packet, buffer) -> FusionAppearance.write(buffer, packet), FusionAppearance::read,
                FusionNetwork::appearance, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, RidingState.class,
                (packet, buffer) -> RidingState.write(buffer, packet), RidingState::read,
                FusionNetwork::riding, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, MemberEvent.class,
                (packet, buffer) -> MemberEvent.write(buffer, packet), MemberEvent::read,
                FusionNetwork::memberEvent, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, EditorOpen.class,
                (packet, buffer) -> EditorOpen.write(buffer, packet), EditorOpen::read, FusionNetwork::editorOpen,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, EditorResult.class,
                (packet, buffer) -> EditorResult.write(buffer, packet), EditorResult::read, FusionNetwork::editorResult,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, EditorSave.class,
                (packet, buffer) -> EditorSave.write(buffer, packet), EditorSave::read, FusionNetwork::editorSave,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, EditorAck.class,
                (packet, buffer) -> EditorAck.write(buffer, packet), EditorAck::read, FusionNetwork::editorAck,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static void appearance(FusionAppearance packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientAppearance.receive(packet));
        context.get().setPacketHandled(true);
    }

    private static void riding(RidingState packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientRiding.receive(packet));
        context.get().setPacketHandled(true);
    }

    private static void memberEvent(MemberEvent packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientAppearance.receive(packet));
        context.get().setPacketHandled(true);
    }

    private static void editorOpen(EditorOpen packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> VisualEditorClient.open(packet.hand(), packet.session(),
                packet.revision(), packet.layout(), packet.admin()));
        context.get().setPacketHandled(true);
    }

    private static void editorResult(EditorResult packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> VisualEditorClient.ack(packet.session(), packet.revision(),
                packet.ok(), packet.message()));
        context.get().setPacketHandled(true);
    }

    private static void editorSave(EditorSave packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        context.get().enqueueWork(() -> {
            if (player != null) {
                VisualEditorServer.handleSave(player, packet.session(), packet.revision(),
                        packet.scope(), packet.layout());
            }
        });
        context.get().setPacketHandled(true);
    }

    private static void editorAck(EditorAck packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        context.get().enqueueWork(() -> {
            if (player != null) {
                VisualEditorServer.handleClose(player, packet.session(), packet.revision(),
                        packet.ok(), packet.message());
            }
        });
        context.get().setPacketHandled(true);
    }

    public static void sendToTracking(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
