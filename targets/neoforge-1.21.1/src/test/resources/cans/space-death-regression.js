// NeoForge 1.21.1 virtual-space death regression.
// A death with a lost can carrier must still drop loot at the last external
// anchor, never at the internal room coordinates, and must stay recoverable.
var SM = Java.loadClass('cc.sighs.cannedmonster.space.SpaceManager');
var SD = Java.loadClass('cc.sighs.cannedmonster.space.SpaceDomain');
var CC = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var BR = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var RL = Java.loadClass('net.minecraft.resources.ResourceLocation');
var IS = Java.loadClass('net.minecraft.world.item.ItemStack');
var IH = Java.loadClass('net.minecraft.world.InteractionHand');
var BP = Java.loadClass('net.minecraft.core.BlockPos');
var AB = Java.loadClass('net.minecraft.world.phys.AABB');
var IE = Java.loadClass('net.minecraft.world.entity.item.ItemEntity');
var V = Java.loadClass('net.minecraft.world.phys.Vec3');
var U = Java.loadClass('java.util.UUID');
var Items = Java.loadClass('net.minecraft.world.item.Items');
var MinecartChest = Java.loadClass('net.minecraft.world.entity.vehicle.MinecartChest');
var P = Java.loadClass('java.lang.System').getProperties();

var p = AutoJS.player(), level = p.level;
var checks = [], phase = 'boot', wait = 0, failure = null, space = null, lastAnchor = null;

function check(name, value) {
    checks.push({ name: name, pass: !!value });
    if (!value) throw new Error(name);
}
function report() {
    P.put('cannedmonster.space.death', JSON.stringify({ phase: phase, checks: checks, failure: failure }));
}
function advance(name, ticks) { phase = name; wait = ticks || 0; report(); }
function failed(error) {
    failure = String(error); phase = 'failed'; report();
    console.error('space death regression failed: ' + failure);
}
function canStack() { return new IS(BR.ITEM.get(RL.tryParse('cannedmonster:spatial_monster_can'))); }
function rawPos(entity) {
    SD.push(entity);
    try { return [entity.getX(), entity.getY(), entity.getZ()]; } finally { SD.pop(); }
}
function distance(entity, vec) {
    var dx = entity.getX() - vec.x, dy = entity.getY() - vec.y, dz = entity.getZ() - vec.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
}
function clearSpaces() {
    var ids = SM.spaceIds();
    for (var i = 0; i < ids.size(); i++) {
        var s = SM.spaceById(ids.get(i));
        if (s === null) continue;
        var keys = s.residents().keySet().iterator();
        while (keys.hasNext()) {
            var e = SM.findEntity(s, U.fromString(String(keys.next())));
            if (e !== null) e.discard();
        }
    }
    SM.stop();
    var items = level.getEntitiesOfClass(IE, new AB(-16, 190, -16, 96, 215, 96));
    for (var j = 0; j < items.size(); j++) {
        var st = items.get(j).getItem();
        if (CC.spaceId(st) !== null || st.getItem() === Items.DIRT) items.get(j).discard();
    }
    var everyone = level.getAllEntities();
    var marks = [];
    for (var e of everyone) {
        if (e.getType() === ET.CHEST_MINECART || e.getType() === ET.COW) marks.push(e);
    }
    for (var k = 0; k < marks.length; k++) marks[k].discard();
}
function dirtNear(box) {
    var items = level.getEntitiesOfClass(IE, box);
    var total = 0;
    for (var i = 0; i < items.size(); i++) {
        if (items.get(i).getItem().getItem() === Items.DIRT) total += items.get(i).getItem().getCount();
    }
    return total;
}

function step() {
    if (phase === 'boot') {
        clearSpaces();
        AutoJS.command('gamemode survival Dev');
        p.teleportTo(0.5, 200, 0.5);
        p.setHealth(p.getMaxHealth());
        p.invulnerableTime = 0;
        p.setItemInHand(IH.MAIN_HAND, canStack());
        var cow = ET.COW.create(level);
        cow.setPos(2.5, 200, 0.5);
        level.addFreshEntity(cow);
        check('capture', SM.capture(p, cow, p.getMainHandItem()));
        check('enter', SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND) && SM.isInside(p));
        space = SM.spaceForPlayer(p);
        lastAnchor = space.lastAnchor();
        p.getInventory().add(new IS(Items.DIRT, 3));
        check('dirt carried', p.getInventory().countItem(Items.DIRT) === 3);
        check('external position is the anchor', Math.abs(p.getX() - lastAnchor.x) < 0.01);
        p.setRespawnPosition(p.level.dimension, new BP(0, 200, 0), 0.0, true, false);
        var anchorItem = SM.findEntity(space, space.anchorItem());
        check('anchor item exists', anchorItem !== null);
        anchorItem.discard();
        advance('lostCarrier', 3);
        return;
    }

    if (phase === 'lostCarrier') {
        check('lost carrier refuses exit', !SM.exit(p));
        check('player still inside', SM.isInside(p));
        var pos = rawPos(p);
        check('player still in the internal room', space.internal().contains(pos[0], pos[1], pos[2]));
        AutoJS.command('damage Dev 1000 minecraft:generic_kill');
        advance('dead', 3);
        return;
    }

    if (phase === 'dead') {
        check('player died', p.isDeadOrDying());
        check('death left the space session', !SM.isInside(p));
        check('space survives death', SM.spaceCount() === 1);
        var keys = space.residents().keySet().iterator();
        var id = U.fromString(String(keys.next()));
        var resident = SM.findEntity(space, id);
        check('resident survives death', resident !== null && resident.isAlive() && !resident.isRemoved());
        check('external death position is the lost anchor',
            Math.abs(p.getX() - lastAnchor.x) < 0.01 && Math.abs(p.getZ() - lastAnchor.z) < 0.01);
        var atAnchor = dirtNear(new AB(lastAnchor.x - 8, lastAnchor.y - 8, lastAnchor.z - 8,
            lastAnchor.x + 8, lastAnchor.y + 8, lastAnchor.z + 8));
        var inRoom = dirtNear(new AB(space.internal().x(), space.internal().y(), space.internal().z(),
            space.internal().x() + 13, space.internal().y() + 8, space.internal().z() + 13));
        check('all dropped dirt is at the external anchor [' + atAnchor + ']', atAnchor >= 3);
        check('no loot leaked into the internal room [' + inRoom + ']', inRoom === 0);
        advance('respawn', 1);
        return;
    }

    if (phase === 'respawn') {
        var C = Java.loadClass('net.minecraft.network.protocol.game.ServerboundClientCommandPacket');
        p.connection.handleClientCommand(new C(C.Action.PERFORM_RESPAWN));
        advance('afterRespawn', 3);
        return;
    }

    if (phase === 'afterRespawn') {
        var np = AutoJS.player();
        check('respawned outside the space', !SM.isInside(np));
        check('respawned at the respawn point',
            Math.abs(np.getX() - 0.5) < 2.0 && Math.abs(np.getZ() - 0.5) < 2.0);
        check('space still exists after respawn', SM.spaceCount() === 1);
        var keys = space.residents().keySet().iterator();
        var id = U.fromString(String(keys.next()));
        var resident = SM.findEntity(space, id);
        check('resident still alive after respawn', resident !== null && resident.isAlive());
        check('lost space can be recovered from outside', SM.recover(np));
        var cans = level.getEntitiesOfClass(IE, new AB(lastAnchor.x - 8, lastAnchor.y - 8, lastAnchor.z - 8,
            lastAnchor.x + 8, lastAnchor.y + 8, lastAnchor.z + 8));
        var found = false;
        for (var i = 0; i < cans.size(); i++) if (CC.spaceId(cans.get(i).getItem()) !== null) found = true;
        check('recovery dropped a can at the last anchor', found);
        AutoJS.command('gamemode creative Dev');
        SM.stop();
        var leftovers = level.getEntitiesOfClass(IE, new AB(-16, 190, -16, 96, 215, 96));
        for (var j = 0; j < leftovers.size(); j++) {
            if (CC.spaceId(leftovers.get(j).getItem()) !== null) leftovers.get(j).discard();
        }
        SM.saved(level.getServer());
        np.teleportTo(0.5, 200, 0.5);
        advance('done', 0);
        return;
    }
}

ServerEvents.tick(function (event) {
    if (phase === 'done' || phase === 'failed') return;
    try {
        if (wait > 0) { wait--; return; }
        step();
    } catch (error) {
        failed(error);
    }
});
report();
JSON.stringify({ started: true, phase: phase });
