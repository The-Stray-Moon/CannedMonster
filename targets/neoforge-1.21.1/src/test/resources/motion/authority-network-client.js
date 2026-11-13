var authorityPacketBuffer = new (Java.loadClass('net.minecraft.network.FriendlyByteBuf'))(Java.loadClass('io.netty.buffer.Unpooled').buffer());
try {
 authorityPacketBuffer.writeDouble(10000);authorityPacketBuffer.writeDouble(200);authorityPacketBuffer.writeDouble(-20);
 authorityPacketBuffer.writeFloat(0);authorityPacketBuffer.writeFloat(0);
 var authorityPacket=Java.loadClass('net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket').STREAM_CODEC.decode(authorityPacketBuffer);
 Client.player.connection.send(authorityPacket);
} finally { authorityPacketBuffer.release(); }
'sent invalid vehicle position to the local verification server';
