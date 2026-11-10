var resourceParent = makeSpecial('slime', 1, 76, false);
resourceParent.member.setSize(4, true);
resourceParent.host.setItemSlot(Slot.MAINHAND, AutoJS.stack('minecraft:diamond', 12));
resourceParent.host.addTag('resource-copy-parent');
var resourcePrior = [];
for (var entity of world.getAllEntities()) resourcePrior.push(entity.getId());
AutoJS.command('kill @e[tag=resource-copy-parent]');
var resourceClock = 0;
var resourceResult = null;
ServerEvents.tick(function(event) {
    if (resourceClock >= 60) return;
    resourceClock++;
    if (resourceClock !== 60) return;
    var children = [];
    for (var entity of world.getAllEntities()) {
        if (entity.getType() !== ET.COW || resourcePrior.indexOf(entity.getId()) >= 0 || !entity.isAlive()
                || Math.abs(entity.getX() - resourceParent.point.x) >= 4 || Math.abs(entity.getZ() - resourceParent.point.z) >= 4) continue;
        if (F.facets(entity).size() !== 1 || !entity.getMainHandItem().isEmpty()) throw new Error('Parent equipment was duplicated to a derived body');
        children.push({members: F.facets(entity).size(), item: String(AutoJS.id(entity.getMainHandItem())), size: F.facets(entity).get(0).getSize()});
    }
    if (children.length < 2 || children.length > 4) throw new Error('Missing resource-copy test children');
    resourceResult = {children: children, noParentInventoryCopy: true};
});
JSON.stringify({parentDiamonds: 12, observingTicks: 60});
