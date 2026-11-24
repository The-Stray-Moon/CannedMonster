// NeoForge 1.21.1 spatial can interaction regression (server-side order/destroy).
// Real client packet paths (entity interact, air use, block use) were exercised
// separately via AutoJS client eval; this script pins the server-side order,
// LIFO release and shatter invariants after those changes.
var SM = Java.loadClass('cc.sighs.cannedmonster.space.SpaceManager');
var CC = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var BR = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var RL = Java.loadClass('net.minecraft.resources.ResourceLocation');
var IS = Java.loadClass('net.minecraft.world.item.ItemStack');
var IH = Java.loadClass('net.minecraft.world.InteractionHand');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var Mob = Java.loadClass('net.minecraft.world.entity.Mob');
var AB = Java.loadClass('net.minecraft.world.phys.AABB');
var U = Java.loadClass('java.util.UUID');

var p = null, level = null, phase = 'boot', wait = 0, checks = [], failure = null;
var spaceId = null, released = [];

function check(name, value) {
    checks.push({ name: name, pass: !!value });
    if (!value) { failure = name; phase = 'failed'; report(); throw new Error(name); }
}
function report() {
    AutoJS.writeJson('space-interaction.json', { phase: phase, checks: checks, failure: failure, released: released });
}
function advance(next, ticks) { phase = next; wait = ticks || 0; report(); }
function canStack() {
    return new IS(BR.ITEM.get(RL.tryParse('cannedmonster:spatial_monster_can')));
}
function mobNames(box) {
    var list = level.getEntitiesOfClass(Mob, box);
    var names = [];
    for (var i = 0; i < list.size(); i++) names.push(String(list.get(i).getType()));
    return names;
}
function step() {
    if (phase === 'boot') {
        p = AutoJS.player();
        if (p === null) return;
        level = p.level;
        SM.stop();
        p.teleportTo(0.5, 200, 0.5);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.inventoryMenu.broadcastChanges();
        var can = canStack();
        p.getInventory().setItem(0, can);
        p.inventoryMenu.broadcastChanges();
        var types = [ET.COW, ET.PIG, ET.SHEEP];
        for (var i = 0; i < types.length; i++) {
            var mob = types[i].create(level);
            mob.setPos(1.5 + i * 0.7, 200, 0.5);
            mob.setNoAi(true);
            level.addFreshEntity(mob);
            check('capture ' + i, SM.capture(p, mob, can));
        }
        check('enter with three residents', SM.enter(p, can, IH.MAIN_HAND));
        var space = SM.spaceForPlayer(p);
        check('three residents retained', space.residents().size() === 3);
        var order = [];
        var it = space.residentOrder().iterator();
        while (it.hasNext()) {
            var entity = SM.findEntity(space, it.next());
            order.push(entity === null ? null : entity.getName().getString());
        }
        check('admission order Cow,Pig,Sheep', order.join(',') === 'Cow,Pig,Sheep');
        spaceId = space.id();
        advance('release', 2);
        return;
    }
    if (phase === 'release') {
        check('still inside before release', SM.isInside(p));
        SM.exit(p);
        var can = canStack();
        CC.storeSpace(can, spaceId, SM.spaceById(spaceId).residents().size());
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, can);
        p.inventoryMenu.broadcastChanges();
        for (var i = 0; i < 3; i++) {
            var before = SM.spaceById(spaceId).residents().size();
            var V = Java.loadClass('net.minecraft.world.phys.Vec3');
            var ok = SM.releaseLast(p, can, level, new V(1.5, 200, 0.5));
            check('release ' + i + ' succeeds', ok);
            check('release ' + i + ' reduces residents', SM.spaceById(spaceId) === null || SM.spaceById(spaceId).residents().size() === before - 1);
        }
        check('space removed after last release', SM.spaceById(spaceId) === null);
        released = mobNames(new AB(-2, 195, -2, 6, 205, 6));
        check('three released mobs present', released.length >= 3);
        advance('destroy', 2);
        return;
    }
    if (phase === 'destroy') {
        p.teleportTo(0.5, 200, 0.5);
        var can = canStack();
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, can);
        p.inventoryMenu.broadcastChanges();
        for (var i = 0; i < 2; i++) {
            var mob = (i === 0 ? ET.COW : ET.PIG).create(level);
            mob.setPos(1.5 + i * 0.7, 200, 0.5);
            mob.setNoAi(true);
            level.addFreshEntity(mob);
            SM.capture(p, mob, can);
        }
        check('second space entered', SM.enter(p, can, IH.MAIN_HAND));
        var space = SM.spaceForPlayer(p);
        var anchor = SM.findEntity(space, space.anchorItem());
        check('anchor item exists', anchor !== null);
        if (anchor !== null) anchor.discard();
        advance('destroyCheck', 5);
        return;
    }
    if (phase === 'destroyCheck') {
        check('space shattered after anchor destruction', SM.spaceCount() === 0);
        check('player ejected from shattered space', !SM.isInside(p));
        var mobs = mobNames(new AB(-2, 195, -2, 6, 205, 6));
        check('residents ejected after shatter', mobs.length >= 2);
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
