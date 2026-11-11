var VisualClient = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientAppearance');
var VisualRenderer = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.FusionRenderer');
var visualChecks = [];
function visualAssert(condition, message) {
    if (!condition) throw new Error(message);
    visualChecks.push(message);
}
var visualFound = 0;
for (var entity of AutoJS.level().entitiesForRendering()) {
    if (Math.abs(entity.getY() - 100) > 0.1 || Math.abs(entity.getZ()) > 0.1
            || [0, -4, 4].indexOf(Number(entity.getX())) < 0) continue;
    visualFound++;
    var members = VisualClient.members(entity);
    var count = entity.getX() < -2 ? 2 : 1;
    visualAssert(members.size() === count, 'member count at ' + entity.getX());
    for (var member of members) {
        visualAssert(!member.isAddedToLevel(), 'display instance not registered');
        visualAssert(!member.isRemoved(), 'display instance not removed');
    }
    var sheep = members.get(0);
    var expected = entity.getX() < -2 ? 'blue' : 'red';
    visualAssert(String(sheep.getColor()) === expected, 'independent color ' + expected);
    if (entity.getX() > 2) visualAssert(String(entity.getColor()) === 'blue', 'same-type body color not overwritten');
}
visualAssert(visualFound === 3, 'three bodies tracked');
visualAssert(VisualRenderer.renderedFrames() > 0, 'world rendering ran');
var counts = VisualRenderer.lastVertexCounts();
visualAssert(counts.size() >= 2, 'multiple slices submitted');
for (var count of counts) visualAssert(count > 0, 'slice submitted nonempty geometry');
JSON.stringify({passed: visualChecks.length, frames: VisualRenderer.renderedFrames(), vertices: String(counts)});
