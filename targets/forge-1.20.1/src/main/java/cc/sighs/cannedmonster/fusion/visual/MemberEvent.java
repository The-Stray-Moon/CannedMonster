package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record MemberEvent(FusionAppearance appearance, UUID member, long sequence, byte event) {
    public static void write(FriendlyByteBuf buffer, MemberEvent packet) {
        FusionAppearance.write(buffer, packet.appearance);
        buffer.writeUUID(packet.member);
        buffer.writeVarLong(packet.sequence);
        buffer.writeByte(packet.event);
    }

    public static MemberEvent read(FriendlyByteBuf buffer) {
        return new MemberEvent(FusionAppearance.read(buffer), buffer.readUUID(), buffer.readVarLong(), buffer.readByte());
    }
}
