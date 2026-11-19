package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.fusion.visual.FusionAppearance;
import cc.sighs.cannedmonster.fusion.visual.MemberEvent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;

public final class AppearanceTransport {
    private AppearanceTransport() {}

    private static void broadcast(LivingEntity body, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayersTrackingEntity(body, packet);
        if (body instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, packet);
    }

    public static void broadcast(LivingEntity body, FusionAppearance packet) { broadcast(body, (CustomPacketPayload) packet); }
    public static void broadcast(LivingEntity body, MemberEvent packet) { broadcast(body, (CustomPacketPayload) packet); }
    public static void send(ServerPlayer player, FusionAppearance packet) { PacketDistributor.sendToPlayer(player, packet); }

    public static void tracking(LivingEntity body, cc.sighs.cannedmonster.fusion.RidingState packet) { PacketDistributor.sendToPlayersTrackingEntity(body, packet); }
    public static void send(ServerPlayer player, cc.sighs.cannedmonster.fusion.RidingState packet) { PacketDistributor.sendToPlayer(player, packet); }

    public static <T> SynchedEntityData.DataValue<?> value(LivingEntity member, SynchedEntityData.DataItem<T> item,
                                                         SynchedEntityData.DataValue<?> previous) {
        T current = member.getEntityData().get(item.getAccessor());
        if (previous != null && previous.id() == item.getAccessor().id() && previous.serializer() == item.getAccessor().serializer()
                && Objects.equals(previous.value(), current)) return previous;
        return SynchedEntityData.DataValue.create(item.getAccessor(), current);
    }
}
