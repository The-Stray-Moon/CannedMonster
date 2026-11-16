if (String(AutoJS.server().getWorldData().getLevelName()) !== 'Fusion motion verification') throw new Error('Use the dedicated verification world');
var asmF = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var asmET = Java.loadClass('net.minecraft.world.entity.EntityType');
var asmList = Java.loadClass('java.util.List');
var asmLoader = Java.loadClass('net.neoforged.fml.loading.FMLLoader');
var asmServices = String(asmLoader.modLauncherModList());
if (asmServices.indexOf('cannedmonster_asm') < 0) throw new Error('ASM transformation service not registered');
var asmWorld = AutoJS.level();
if (!AutoJS.player().isAlive() || AutoJS.player().distanceToSqr(4,200,4) > 1024) throw new Error('Living player must be near the prepared test platform');
var asmStale = [];
for (var e of asmWorld.getAllEntities()) if (!e.isRemoved() && e.getTags().contains('asm-verification')) asmStale.push(e);
for (var e of asmStale) e.discard();
var asmBody = asmET.RABBIT.create(asmWorld);
var asmCow = asmET.COW.create(asmWorld);
var asmSheep = asmET.SHEEP.create(asmWorld);
asmBody.setPos(4,200,4);
asmBody.setPersistenceRequired();
asmBody.addTag('asm-verification');
asmF.attach(asmBody,asmList.of(asmCow,asmSheep));
if (!asmWorld.addFreshEntity(asmBody)) throw new Error('Body spawn rejected');
asmCow.setAirSupply(123);
if (asmBody.getAirSupply() !== 123) throw new Error('Shared field redirect missing');
asmBody.setAirSupply(300);
var asmStart = asmBody.tickCount;
var asmGeneration = typeof asmGeneration === 'undefined' ? 1 : asmGeneration + 1;
var asmResult = {done:false};
var asmTick = 0;
(function(generation) {
ServerEvents.tick(function(event) {
    if (generation !== asmGeneration || asmResult.done) return;
    asmTick++;
    if (asmTick < 100) return;
    var members = asmF.facets(asmBody);
    var checks = {
        serviceRegistered:asmServices.indexOf('cannedmonster_asm') >= 0,
        alive:asmBody.isAlive(),
        ticks:asmBody.tickCount - asmStart === 100,
        order:members.size() === 2 && members.get(0).getType() === asmET.COW && members.get(1).getType() === asmET.SHEEP,
        membersUnregistered:!asmCow.isAddedToLevel() && !asmSheep.isAddedToLevel(),
        membersOutsideWorld:asmWorld.getEntity(asmCow.getId()) !== asmCow && asmWorld.getEntity(asmSheep.getId()) !== asmSheep,
        binding:asmCow.cannedmonster$body() === asmBody && asmSheep.cannedmonster$body() === asmBody
    };
    asmResult = {done:true, checks:checks, ticks:asmBody.tickCount-asmStart, uuid:String(asmBody.uuid)};
});
})(asmGeneration);
JSON.stringify({started:true, service:'cannedmonster_asm', uuid:String(asmBody.uuid)});
