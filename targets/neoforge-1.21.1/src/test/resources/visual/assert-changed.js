var VisualClient = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientAppearance');
var changedBody = null;
for (var entity of AutoJS.level().entitiesForRendering()) {
    if (Math.abs(entity.getX()) < 0.1 && Math.abs(entity.getY() - 100) < 0.1 && Math.abs(entity.getZ()) < 0.1) changedBody = entity;
}
if (changedBody === null) throw new Error('Body not tracked');
var changedMember = VisualClient.members(changedBody).get(0);
if (String(changedMember.getColor()) !== 'green') throw new Error('Color update missing');
if (!changedMember.isSheared()) throw new Error('Shearing update missing');
if (changedMember.isAddedToLevel()) throw new Error('Display instance joined world');
JSON.stringify({passed: 3, color: String(changedMember.getColor()), sheared: changedMember.isSheared()});
