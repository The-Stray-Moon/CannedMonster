var world = AutoJS.level();
if (String(AutoJS.server().getWorldData().getLevelName()) !== 'Fusion motion verification') throw new Error('Use the dedicated motion verification world');
var F = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var Lists = Java.loadClass('java.util.List');
var Hand = Java.loadClass('net.minecraft.world.InteractionHand');
var Mode = Java.loadClass('net.minecraft.world.level.GameType');
var motionPlayer = AutoJS.player();
AutoJS.command('gamerule doMobSpawning false');
AutoJS.command('gamerule mobGriefing false');
AutoJS.command('gamerule doDaylightCycle false');
AutoJS.command('time set midnight');
AutoJS.command('fill -25 199 -25 25 199 25 minecraft:stone');
AutoJS.command('fill -25 200 -25 25 207 25 minecraft:air');
AutoJS.command('fill -25 208 -25 25 215 25 minecraft:air');
if (!world.getBlockState(AutoJS.pos(0,200,0)).isAir()) throw new Error('Motion arena did not clear');
AutoJS.tp(motionPlayer, 0, 200, 0);
motionPlayer.setGameMode(Mode.CREATIVE);
motionPlayer.setItemInHand(Hand.MAIN_HAND, AutoJS.stack('minecraft:carrot_on_a_stick', 1));
var motionGeneration = typeof motionGeneration === 'undefined' ? 1 : motionGeneration + 1;
var motionRows = [];
function motionSpawn(label, firstType, bodyType, x, y, z) {
    var body = bodyType.create(world);
    var member = firstType == null ? null : firstType.create(world);
    body.setPos(x, y, z);
    body.setPersistenceRequired();
    if (member != null) F.attach(body, Lists.of(member));
    if (!world.addFreshEntity(body)) throw new Error('spawn rejected: ' + label);
    body.addTag('motion-verification');
    var row = {label:label, body:body, member:member, start:[x,y,z], tick:body.tickCount};
    motionRows.push(row);
    return row;
}
function motionSnapshot(row) {
    var b = row.body;
    return {label:row.label, alive:b.isAlive(), ticks:b.tickCount-row.tick,
        x:b.getX(), y:b.getY(), z:b.getZ(), distance:b.distanceTo(motionPlayer),
        moved:Math.sqrt(Math.pow(b.getX()-row.start[0],2)+Math.pow(b.getZ()-row.start[2],2)),
        target:String(b.getTarget()), path:!b.getNavigation().isDone(),
        memberPath:row.member != null && !row.member.getNavigation().isDone(),
        memberRegistered:row.member != null && row.member.isAddedToLevel()};
}
var staleMotionEntities = [];
for (var old of world.getAllEntities()) if (old != null && !old.isRemoved() && old.getTags().contains('motion-verification')) staleMotionEntities.push(old);
for (var old of staleMotionEntities) old.discard();
var temptNative = motionSpawn('native pig', null, ET.PIG, -8,200,-4);
var temptPigBody = motionSpawn('creeper -> pig', ET.CREEPER, ET.PIG, 8,200,0);
var temptCreeperBody = motionSpawn('pig -> creeper', ET.PIG, ET.CREEPER, -8,200,4);
var motionElapsed = 0;
var motionObserved = [];
(function(observerGeneration) {
ServerEvents.tick(function(event) {
    if (observerGeneration !== motionGeneration || motionElapsed >= 160) return;
    motionElapsed++;
    if (motionElapsed % 20 === 0) motionObserved.push({tick:motionElapsed, rows:motionRows.map(motionSnapshot)});
});
})(motionGeneration);
JSON.stringify({started:true, world:String(AutoJS.server().getWorldData().getLevelName()), rows:motionRows.map(motionSnapshot)});
