var CodecPacket = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.FusionAppearance');
var CodecMember = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.FusionAppearance$Member');
var CodecValues = Java.loadClass('net.minecraft.network.syncher.SynchedEntityData$DataValue');
var CodecList = Java.loadClass('java.util.List');
var CodecArray = Java.loadClass('java.util.ArrayList');
var CodecUUID = Java.loadClass('java.util.UUID');
var CodecTag = Java.loadClass('net.minecraft.nbt.CompoundTag');
var CodecBuf = Java.loadClass('net.minecraft.network.RegistryFriendlyByteBuf');
var CodecRaw = Java.loadClass('io.netty.buffer.Unpooled');
var codecFullValues = new CodecArray();
for (var item of optMember.getEntityData().cannedmonster$items()) codecFullValues.add(CodecValues.create(item.getAccessor(),optMember.getEntityData().get(item.getAccessor())));
var codecId = CodecUUID.randomUUID();
var codecType = Java.loadClass('net.minecraft.resources.ResourceLocation').parse(String(optMember.getType()));
var codecFullMember = new CodecMember(codecId,codecType,codecFullValues,new CodecTag());
var codecFull = new CodecPacket(optBody.getId(),CodecUUID.fromString(String(optBody.uuid)),-1,false,1,true,CodecList.of(codecFullMember));
optMember.setColor(optColor.BLUE);
var codecChanges = new CodecArray();
var codecIndex = 0;
for (var item of optMember.getEntityData().cannedmonster$items()) {
    var next = CodecValues.create(item.getAccessor(),optMember.getEntityData().get(item.getAccessor()));
    if (!next.equals(codecFullValues.get(codecIndex++))) codecChanges.add(next);
}
if (codecChanges.size() !== 1) throw new Error('Expected one wool field delta');
var codecDelta = new CodecPacket(optBody.getId(),CodecUUID.fromString(String(optBody.uuid)),-1,false,2,false,
    CodecList.of(new CodecMember(codecId,codecType,codecChanges,new CodecTag())));
var codecFullBuffer = new CodecBuf(CodecRaw.buffer(),AutoJS.server().registryAccess());
var codecDeltaBuffer = new CodecBuf(CodecRaw.buffer(),AutoJS.server().registryAccess());
var codecResult;
try {
    CodecPacket.CODEC.encode(codecFullBuffer,codecFull);
    CodecPacket.CODEC.encode(codecDeltaBuffer,codecDelta);
    var fullBytes = codecFullBuffer.readableBytes();
    var deltaBytes = codecDeltaBuffer.readableBytes();
    var decoded = CodecPacket.CODEC.decode(codecDeltaBuffer);
    if (decoded.full() || decoded.members().get(0).data().size() !== 1 || deltaBytes >= fullBytes) throw new Error('Delta codec failed');
    codecResult = {fullBytes:fullBytes,deltaBytes:deltaBytes,fullValues:codecFullValues.size(),deltaValues:codecChanges.size(),dirtyPreserved:optMember.getEntityData().isDirty()};
} finally {
    codecFullBuffer.release();
    codecDeltaBuffer.release();
}
JSON.stringify(codecResult);
