var FusionRuntime = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var DyeColor = Java.loadClass('net.minecraft.world.item.DyeColor');
var visualBody = null;
for (var entity of AutoJS.level().getAllEntities()) {
    if (entity.getTags().contains('visual-slices-test') && Math.abs(entity.getX()) < 0.1) visualBody = entity;
}
if (visualBody === null) throw new Error('Missing visual test body');
var visualSheep = FusionRuntime.facets(visualBody).get(0);
visualSheep.setColor(DyeColor.GREEN);
visualSheep.setSheared(true);
JSON.stringify({body: visualBody.getId(), color: String(visualSheep.getColor()), sheared: visualSheep.isSheared()});
