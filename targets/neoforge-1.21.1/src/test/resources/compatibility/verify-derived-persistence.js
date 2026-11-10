var F = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var world = AutoJS.level();
var restoredDerived = [];
for (var entity of world.getAllEntities()) {
    if (!entity.getTags().contains('derived-persist')) continue;
    var members = F.facets(entity);
    if (members.size() !== 1 || !entity.isAlive() || members.get(0).isAddedToLevel()) throw new Error('Derived fusion lost after restart');
    var slime = entity.getType() === ET.SLIME ? entity : members.get(0);
    if (slime.getSize() !== 2) throw new Error('Derived member size lost');
    if (world.getEntity(entity.getId()) !== entity) throw new Error('Derived body identity lost');
    restoredDerived.push({name: entity.getCustomName().getString(), body: String(entity.getType()), member: String(members.get(0).getType()), size: slime.getSize()});
}
if (restoredDerived.length !== 2) throw new Error('Expected 2 persisted derived bodies, found ' + restoredDerived.length);
JSON.stringify(restoredDerived);
