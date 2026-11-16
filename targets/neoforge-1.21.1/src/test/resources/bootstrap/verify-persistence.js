if (String(AutoJS.server().getWorldData().getLevelName()) !== 'Fusion motion verification') throw new Error('Wrong verification world');
var persistedF = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var persistedET = Java.loadClass('net.minecraft.world.entity.EntityType');
var persistedBodies = [];
for (var e of AutoJS.level().getAllEntities()) if (!e.isRemoved() && e.getTags().contains('asm-verification')) persistedBodies.push(e);
if (persistedBodies.length !== 1) throw new Error('Expected one persisted ASM test body, got ' + persistedBodies.length);
var persistedBody = persistedBodies[0];
var persistedMembers = persistedF.facets(persistedBody);
if (persistedBody.getType() !== persistedET.RABBIT || persistedMembers.size() !== 2
    || persistedMembers.get(0).getType() !== persistedET.COW || persistedMembers.get(1).getType() !== persistedET.SHEEP) throw new Error('Persisted member order changed');
for (var member of persistedMembers) {
    if (member.isAddedToLevel() || member.cannedmonster$body() !== persistedBody) throw new Error('Persisted member binding invalid');
}
JSON.stringify({restored:true, uuid:String(persistedBody.uuid), members:['cow','sheep'], body:'rabbit', tick:persistedBody.tickCount});
