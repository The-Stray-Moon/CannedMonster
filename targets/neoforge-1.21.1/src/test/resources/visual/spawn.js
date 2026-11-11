var FusionRuntime = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var EntityType = Java.loadClass('net.minecraft.world.entity.EntityType');
var DyeColor = Java.loadClass('net.minecraft.world.item.DyeColor');
var level = AutoJS.level();
var player = AutoJS.player();
if (String(AutoJS.server().getWorldData().getLevelName()) !== 'Fusion visual slices verification') throw new Error('Use the dedicated visual test world');
for (var old of level.getAllEntities()) {
    if (old.getTags().contains('visual-slices-test') && !old.isRemoved()) old.discard();
}
AutoJS.command('gamerule doMobSpawning false');
AutoJS.command('gamerule doDaylightCycle false');
AutoJS.command('time set noon');
AutoJS.command('weather clear');
AutoJS.command('fill -12 99 -12 12 99 12 minecraft:stone');
AutoJS.command('fill -12 100 -12 12 106 12 minecraft:air');
player.teleportTo(0, 100, 6);
var visualBodies = [];
function spawnVisual(types, x) {
    var body = types[types.length - 1].create(level);
    body.addTag('visual-slices-test');
    body.setPos(x, 100, 0);
    var members = new (Java.loadClass('java.util.ArrayList'))();
    for (var i = 0; i < types.length - 1; i++) members.add(types[i].create(level));
    FusionRuntime.attach(body, members);
    body.setNoAi(true);
    body.setInvulnerable(true);
    if (!level.addFreshEntity(body)) throw new Error('Spawn rejected');
    visualBodies.push(body);
    return body;
}
var visualCow = spawnVisual([EntityType.SHEEP, EntityType.COW], 0);
FusionRuntime.facets(visualCow).get(0).setColor(DyeColor.RED);
var visualTriple = spawnVisual([EntityType.SHEEP, EntityType.PIG, EntityType.COW], -4);
FusionRuntime.facets(visualTriple).get(0).setColor(DyeColor.BLUE);
var visualSame = spawnVisual([EntityType.SHEEP, EntityType.SHEEP], 4);
FusionRuntime.facets(visualSame).get(0).setColor(DyeColor.RED);
visualSame.setColor(DyeColor.BLUE);
AutoJS.lookAt(visualCow);
JSON.stringify(visualBodies.map(function (body) {
    return {id: body.getId(), uuid: String(body.uuid), members: FusionRuntime.facets(body).size()};
}));
