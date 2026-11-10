var F = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var Registry = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var RL = Java.loadClass('net.minecraft.resources.ResourceLocation');
var Tag = Java.loadClass('net.minecraft.nbt.CompoundTag');
var JList = Java.loadClass('java.util.List');
var world = AutoJS.level();
var player = AutoJS.player();
var originX = Math.floor(player.getX());
var originZ = Math.floor(player.getZ());
var testY = 200;
AutoJS.command('difficulty normal');
AutoJS.command('gamerule doMobSpawning false');
AutoJS.command('gamerule mobGriefing false');
AutoJS.command('gamerule doDaylightCycle false');
AutoJS.command('time set midnight');
AutoJS.command('weather clear');
AutoJS.tp(player, originX, testY + 9, originZ);
var matrixTypes = ['villager', 'allay', 'bee', 'slime', 'magma_cube', 'guardian', 'axolotl', 'frog', 'enderman', 'creeper', 'sheep', 'shulker', 'ghast', 'phantom', 'warden', 'snow_golem', 'dolphin', 'pufferfish'];
var matrix = [];
var matrixElapsed = 0;
function createType(name) {
    return Registry.ENTITY_TYPE.get(RL.parse('minecraft:' + name)).create(world);
}
function cage(index, water) {
    var cx = originX - 40 + (index % 9) * 10;
    var cz = originZ - 25 + Math.floor(index / 9) * 10;
    AutoJS.command('fill ' + (cx - 4) + ' ' + (testY - 1) + ' ' + (cz - 4) + ' ' + (cx + 4) + ' ' + (testY + 7) + ' ' + (cz + 4) + ' minecraft:glass hollow');
    AutoJS.command('fill ' + (cx - 3) + ' ' + (testY - 1) + ' ' + (cz - 3) + ' ' + (cx + 3) + ' ' + (testY - 1) + ' ' + (cz + 3) + ' minecraft:stone');
    if (water) AutoJS.command('fill ' + (cx - 3) + ' ' + testY + ' ' + (cz - 3) + ' ' + (cx + 3) + ' ' + (testY + 3) + ' ' + (cz + 3) + ' minecraft:water');
    return {x: cx, y: testY, z: cz};
}
function recordState(entry) {
    var host = entry.host;
    var facets = F.facets(host);
    var registered = false;
    for (var member of facets) registered = registered || member.isAddedToLevel();
    return {alive: host.isAlive(), removed: host.isRemoved(), health: host.getHealth(), bodyTicks: host.tickCount - entry.startTick,
        members: facets.size(), facetRegistered: registered, distanceSquared: host.position().distanceToSqr(entry.startPos),
        height: host.getY(), effects: String(host.getActiveEffects()), brain: String(host.getBrain().getActiveActivities())};
}
for (var species of matrixTypes) {
    var wet = ['guardian', 'axolotl', 'dolphin', 'pufferfish'].indexOf(species) >= 0;
    for (var direction = 0; direction < 3; direction++) {
        var location = cage(matrix.length, wet);
        var host = null;
        var facet = null;
        var row = {species: species, mode: direction === 0 ? 'native-control' : direction === 1 ? species + '->cow' : 'cow->' + species,
            location: location, errors: [], observedTicks: 0, upwardTicks: 0};
        try {
            host = createType(direction === 1 ? 'cow' : species);
            host.setPos(location.x + 0.5, location.y, location.z + 0.5);
            if (direction !== 0) {
                facet = createType(direction === 1 ? species : 'cow');
                F.attach(host, JList.of(facet));
            } else {
                host.setPersistenceRequired();
            }
            row.host = host;
            row.facet = facet;
            row.startTick = host.tickCount;
            row.startPos = host.position();
            if (!world.addFreshEntity(host)) throw new Error('world rejected host');
        } catch (error) {
            row.errors.push(String(error));
            if (host) { F.detach(host); host.discard(); }
        }
        matrix.push(row);
    }
}
var matrixGeneration = typeof matrixGeneration === 'undefined' ? 1 : matrixGeneration + 1;
(function(generation) {
ServerEvents.tick(function(event) {
    if (generation !== matrixGeneration || matrixElapsed >= 200) return;
    matrixElapsed++;
    for (var row of matrix) {
        if (!row.host || row.errors.length > 0) continue;
        try {
            row.observedTicks++;
            if (Number(row.host.getDeltaMovement().y) > 0.05) row.upwardTicks++;
            if (matrixElapsed === 200) {
                row.observed = recordState(row);
                var saved = row.host.saveWithoutId(new Tag());
                row.saved = saved;
                if (!row.host.isRemoved()) {
                    var copy = createType(row.mode.indexOf('->cow') >= 0 ? 'cow' : row.species);
                    try {
                        copy.load(saved);
                        row.roundtripMembers = F.facets(copy).size();
                        row.roundtripType = String(copy.getType());
                    } finally {
                        F.detach(copy);
                    }
                }
                row.host.discard();
            }
        } catch (error) {
            row.errors.push(String(error));
            if (row.host) row.host.discard();
        }
    }
});
})(matrixGeneration);
JSON.stringify({spawned: matrix.length, species: matrixTypes, durationTicks: 200});
