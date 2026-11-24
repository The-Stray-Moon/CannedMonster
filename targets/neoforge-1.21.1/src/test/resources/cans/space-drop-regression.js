// NeoForge 1.21.1 spatial can: drop/pickup anchor regression.
// The anchor following a dropped item is what exposed the floored-rebase bug:
// rebaseTo moved the room by Math.floor(delta) but entities by the raw delta, so
// the internal room ratcheted down and residents fell out of the 13x8x13 room.
// This script pins: block-only rebase, resident survival, Count/data continuity,
// enter/exit and LIFO release. Run in a disposable world (it spawns mobs).
var SM = Java.loadClass('cc.sighs.cannedmonster.space.SpaceManager');
var CC = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var BR = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var RL = Java.loadClass('net.minecraft.resources.ResourceLocation');
var IS = Java.loadClass('net.minecraft.world.item.ItemStack');
var IH = Java.loadClass('net.minecraft.world.InteractionHand');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var Mob = Java.loadClass('net.minecraft.world.entity.Mob');
var AB = Java.loadClass('net.minecraft.world.phys.AABB');
var V = Java.loadClass('net.minecraft.world.phys.Vec3');
var Comp = Java.loadClass('net.minecraft.network.chat.Component');
var ItemEntity = Java.loadClass('net.minecraft.world.entity.item.ItemEntity');

var p = null, level = null, phase = 'boot', wait = 0, checks = [], failure = null;
var spaceId = null, order = [], initial = null, hp = {}, item = null, can = null, pickupTries = 0;

function check(name, value) {
    checks.push({ name: name, pass: !!value });
    if (!value) { failure = name; phase = 'failed'; report(); throw new Error(name); }
}
function report() {
    var orderOut = [];
    for (var i = 0; i < order.length; i++) orderOut.push(order[i].toString());
    AutoJS.writeJson('space-drop.json', { phase: phase, checks: checks, failure: failure,
        space: spaceId === null ? null : spaceId.toString(), order: orderOut, hp: hp });
}
function advance(next, ticks) { phase = next; wait = ticks || 0; report(); }
function space() { return spaceId === null ? null : SM.spaceById(spaceId); }
function canStack() { return new IS(BR.ITEM.get(RL.tryParse('cannedmonster:spatial_monster_can'))); }
function holdCan() {
    var inv = p.getInventory();
    for (var i = 0; i < 9; i++) {
        var stack = inv.getItem(i);
        if (!stack.isEmpty() && CC.spaceId(stack) !== null) {
            inv.selected = i;
            p.inventoryMenu.broadcastChanges();
            return stack;
        }
    }
    return IS.EMPTY;
}
function entityAt(uuid) {
    var s = space();
    return s === null ? null : SM.findEntity(s, uuid);
}
function residentStats() {
    var s = space();
    var out = [];
    for (var i = 0; i < s.residentOrder().size(); i++) {
        var e = entityAt(s.residentOrder().get(i));
        out.push(e === null ? 'null' : String(e.getName().getString()) + '/hp=' + e.getHealth()
            + '/alive=' + e.isAlive() + '/wall=' + e.isInWall());
    }
    return out;
}
function findDrop() {
    var it = level.getAllEntities().iterator();
    while (it.hasNext()) {
        var e = it.next();
        if (e instanceof ItemEntity && spaceId.equals(CC.spaceId(e.getItem()))) return e;
    }
    return null;
}
function step() {
    if (phase === 'boot') {
        p = AutoJS.player();
        if (p === null) return;
        level = p.level;
        SM.stop();
        p.teleportTo(0.5, -60, 0.5);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        var can = canStack();
        p.getInventory().setItem(0, can);
        p.inventoryMenu.broadcastChanges();
        var leftovers = level.getEntitiesOfClass(ItemEntity, new AB(p.getX() - 12, p.getY() - 8, p.getZ() - 12,
            p.getX() + 12, p.getY() + 8, p.getZ() + 12));
        for (var k = 0; k < leftovers.size(); k++) leftovers.get(k).discard();
        var old = level.getEntitiesOfClass(Mob, new AB(p.getX() - 12, p.getY() - 8, p.getZ() - 12,
            p.getX() + 12, p.getY() + 8, p.getZ() + 12));
        for (var i = 0; i < old.size(); i++) {
            var name = old.get(i).getName().getString();
            if (name === 'DropPanda' || name === 'DropOcelot') old.get(i).discard();
        }
        var panda = ET.PANDA.create(level);
        panda.setPos(1.8, -60, 0.4);
        panda.setCustomName(Comp.literal('DropPanda'));
        panda.setPersistenceRequired();
        level.addFreshEntity(panda);
        var ocelot = ET.OCELOT.create(level);
        ocelot.setPos(1.8, -60, 1.7);
        ocelot.setCustomName(Comp.literal('DropOcelot'));
        ocelot.setPersistenceRequired();
        level.addFreshEntity(ocelot);
        check('capture panda', SM.capture(p, panda, can));
        check('capture ocelot', SM.capture(p, ocelot, can));
        spaceId = CC.spaceId(can);
        var s = space();
        check('space has two residents', s !== null && s.residents().size() === 2);
        initial = s.internal();
        order = [s.residentOrder().get(0), s.residentOrder().get(1)];
        hp[0] = entityAt(order[0]).getHealth();
        hp[1] = entityAt(order[1]).getHealth();
        check('can count is two', CC.spaceCount(can) === 2);
        advance('drop', 2);
        return;
    }
    if (phase === 'drop') {
        // ServerPlayer.drop(boolean) removes the selected stack and drops it; the
        // two-arg Player.drop would leave a copy in the hand and skip the rebase path.
        check('drop accepted', p.drop(false));
        advance('fall', 45);
        return;
    }
    if (phase === 'fall') {
        var s = space();
        check('space survives the drop', s !== null && s.residents().size() === 2);
        check('room y did not ratchet', s.internal().y() === initial.y());
        check('room x moved by a whole block', (s.internal().x() - initial.x()) % 1 === 0);
        for (var i = 0; i < 2; i++) {
            var e = entityAt(order[i]);
            check('resident ' + i + ' alive after drop', e !== null && e.isAlive() && !e.isRemoved());
            check('resident ' + i + ' not in wall', !e.isInWall());
            check('resident ' + i + ' hp unchanged', e.getHealth() === hp[i]);
        }
        advance('pickup', 0);
        return;
    }
    if (phase === 'pickup') {
        item = findDrop();
        if (item === null) {
            // Already picked up (e.g. the drop landed on the player).
            var held = holdCan();
            check('can returned to inventory', !held.isEmpty());
            check('can count kept', CC.spaceCount(held) === 2);
            advance('enter', 2);
            return;
        }
        p.teleportTo(item.getX(), item.getY(), item.getZ());
        pickupTries = 0;
        advance('pickupWait', 1);
        return;
    }
    if (phase === 'pickupWait') {
        item = findDrop();
        if (item !== null) {
            check('drop picked up within 60 ticks', ++pickupTries < 60);
            p.teleportTo(item.getX(), item.getY(), item.getZ());
            wait = 1;
            return;
        }
        check('drop was picked up', findDrop() === null);
        var held = holdCan();
        check('can returned to inventory', !held.isEmpty());
        check('can count kept', CC.spaceCount(held) === 2);
        check('resident data kept', space() !== null && space().residents().size() === 2);
        advance('enter', 2);
        return;
    }
    if (phase === 'enter') {
        var can = holdCan();
        check('enter succeeds', SM.enter(p, can, IH.MAIN_HAND));
        check('player inside', SM.isInside(p));
        check('two residents after enter', space().residents().size() === 2);
        advance('inside', 40);
        return;
    }
    if (phase === 'inside') {
        for (var i = 0; i < 2; i++) {
            var e = entityAt(order[i]);
            check('resident ' + i + ' alive inside', e !== null && e.isAlive() && !e.isInWall());
        }
        check('still two residents', space().residents().size() === 2);
        check('exit succeeds', SM.exit(p));
        check('player outside', !SM.isInside(p));
        advance('exit', 6);
        return;
    }
    if (phase === 'exit') {
        var can = holdCan();
        check('exit returns a can', !can.isEmpty());
        check('count still two after exit', CC.spaceCount(can) === 2);
        advance('releaseOne', 1);
        return;
    }
    if (phase === 'releaseOne') {
        var can = holdCan();
        var pos = new V(p.getX(), p.getY(), p.getZ());
        var released = SM.releaseLast(p, can, level, pos);
        check('first release succeeds', released);
        check('count dropped to one', CC.spaceCount(can) === 1);
        check('space kept with one resident', SM.spaceCount() === 1 && space().residents().size() === 1);
        // LIFO: the second captured resident (ocelot) must leave first.
        var first = null;
        var list = level.getEntitiesOfClass(Mob, new AB(pos.x - 6, pos.y - 6, pos.z - 6, pos.x + 6, pos.y + 6, pos.z + 6));
        for (var i = 0; i < list.size(); i++) {
            if (list.get(i).getName().getString().equals('DropOcelot')) first = list.get(i);
        }
        check('first release is the last captured ocelot', first !== null);
        check('panda still resident', entityAt(order[0]) !== null);
        advance('releaseTwo', 2);
        return;
    }
    if (phase === 'releaseTwo') {
        var can = holdCan();
        var pos = new V(p.getX(), p.getY(), p.getZ());
        check('second release succeeds', SM.releaseLast(p, can, level, pos));
        check('count cleared', CC.spaceCount(can) === 0 && CC.spaceId(can) === null);
        check('space removed', SM.spaceCount() === 0);
        var panda = null;
        var list = level.getEntitiesOfClass(Mob, new AB(pos.x - 6, pos.y - 6, pos.z - 6, pos.x + 6, pos.y + 6, pos.z + 6));
        for (var i = 0; i < list.size(); i++) {
            if (list.get(i).getName().getString().equals('DropPanda')) panda = list.get(i);
        }
        check('second release is the panda', panda !== null);
        check('panda alive', panda !== null && panda.isAlive());
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
        failure = String(error);
        phase = 'failed';
        report();
    }
});
report();
