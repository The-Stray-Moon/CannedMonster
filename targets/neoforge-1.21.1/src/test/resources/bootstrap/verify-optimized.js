if (String(AutoJS.server().getWorldData().getLevelName()) !== 'Fusion motion verification') throw new Error('Wrong verification world');
var restoredF = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var restoredBody = null;
for (var e of AutoJS.level().getAllEntities()) if (!e.isRemoved() && e.getTags().contains('optimized-verification')) {
    if (restoredBody !== null) throw new Error('Multiple optimized verification bodies');
    restoredBody = e;
}
if (restoredBody === null) throw new Error('Missing persisted optimized body');
var restoredMembers = restoredF.facets(restoredBody);
if (restoredMembers.size() !== 1 || String(restoredMembers.get(0).getType()) !== 'minecraft:sheep') throw new Error('Restored composition mismatch');
if (!Java.loadClass('cc.sighs.cannedmonster.fusion.BodyLinks').same(restoredBody,restoredMembers.get(0)) || restoredMembers.get(0).isAddedToLevel()) throw new Error('Restored session binding mismatch');
JSON.stringify({restored:true,uuid:String(restoredBody.uuid),member:String(restoredMembers.get(0).getType()),color:String(restoredMembers.get(0).getColor()),sheared:restoredMembers.get(0).isSheared(),tick:restoredBody.tickCount});
