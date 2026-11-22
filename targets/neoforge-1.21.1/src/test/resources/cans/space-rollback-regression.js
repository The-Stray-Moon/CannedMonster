// NeoForge 1.21.1 virtual-space transaction regression.
// Spawn rejection must roll back the room origin, the can in hand, a newly
// created space record and its chunk tickets.
var SM = Java.loadClass('cc.sighs.cannedmonster.space.SpaceManager');
var SD = Java.loadClass('cc.sighs.cannedmonster.space.SpaceDomain');
var CC = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var BR = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var RL = Java.loadClass('net.minecraft.resources.ResourceLocation');
var IS = Java.loadClass('net.minecraft.world.item.ItemStack');
var IH = Java.loadClass('net.minecraft.world.InteractionHand');
var AB = Java.loadClass('net.minecraft.world.phys.AABB');
var IE = Java.loadClass('net.minecraft.world.entity.item.ItemEntity');
var U = Java.loadClass('java.util.UUID');
var Items = Java.loadClass('net.minecraft.world.item.Items');
var MinecartChest = Java.loadClass('net.minecraft.world.entity.vehicle.MinecartChest');
var P = Java.loadClass('java.lang.System').getProperties();

var p = AutoJS.player(), level = p.level;
var checks = [], phase = 'boot', wait = 0, failure = null;
var spaceA = null, residentA = null, residentAId = null, originA = null, anchorA = null, cow = null;

function check(name, value) {
    checks.push({ name: name, pass: !!value });
    if (!value) throw new Error(name);
}
function report() {
    P.put('cannedmonster.space.rollback', JSON.stringify({ phase: phase, checks: checks, failure: failure }));
}
function advance(name, ticks) { phase = name; wait = ticks || 0; report(); }
function failed(error) {
    failure = String(error); phase = 'failed'; report();
    console.error('space rollback regression failed: ' + failure);
}
function canStack() { return new IS(BR.ITEM.get(RL.tryParse('cannedmonster:spatial_monster_can'))); }
function rawPos(entity) {
    SD.push(entity);
    try { return [entity.getX(), entity.getY(), entity.getZ()]; } finally { SD.pop(); }
}
function rawDistance(entity, vec) {
    var pos = rawPos(entity);
    var dx = pos[0] - vec.x, dy = pos[1] - vec.y, dz = pos[2] - vec.z;
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
        var stack = items.get(j).getItem();
        if (CC.spaceId(stack) !== null || stack.getItem() === Items.DIRT) {
            items.get(j).discard();
        }
    }
    var everyone = level.getAllEntities();
    var marks = [];
    for (var e of everyone) {
        if (e.getType() === ET.CHEST_MINECART || e.getType() === ET.COW) marks.push(e);
    }
    for (var k = 0; k < marks.length; k++) marks[k].discard();
}

function step() {
    if (phase === 'boot') {
        clearSpaces();
        AutoJS.command('gamemode creative Dev');
        p.teleportTo(0.5, 200, 0.5);
        p.setItemInHand(IH.MAIN_HAND, canStack());
        var mob = ET.COW.create(level);
        mob.setPos(2.5, 200, 0.5);
        level.addFreshEntity(mob);
        check('capture first space', SM.capture(p, mob, p.getMainHandItem()));
        check('enter first space', SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND) && SM.isInside(p));
        spaceA = SM.spaceForPlayer(p);
        originA = spaceA.internal();
        anchorA = spaceA.lastAnchor();
        residentAId = U.fromString(String(spaceA.residents().keySet().iterator().next()));
        residentA = SM.findEntity(spaceA, residentAId);
        check('first resident exists', residentA !== null && residentA.isAlive());
        check('exit first space', SM.exit(p));
        advance('failedReentry', 1);
        return;
    }

    if (phase === 'failedReentry') {
        // Take the can out of the anchor, move away, then reject the anchor spawn
        // inside another room frame. Everything happens in one tick so no carrier
        // update can intervene.
        var anchorItem = SM.findEntity(spaceA, spaceA.anchorItem());
        check('anchor item available', anchorItem !== null);
        var stack = anchorItem.getItem().copy();
        anchorItem.discard();
        p.setItemInHand(IH.MAIN_HAND, stack);
        p.teleportTo(20.5, 200, 20.5);
        var result;
        SD.push(residentA);
        try {
            result = SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND);
        } finally {
            SD.pop();
        }
        check('rejected anchor spawn cancels entry', !result);
        check('failed entry leaves the player outside', !SM.isInside(p));
        check('failed entry keeps one space', SM.spaceCount() === 1);
        check('failed entry keeps the original room origin',
            spaceA.internal().x() === originA.x() && spaceA.internal().z() === originA.z());
        check('failed entry keeps the resident in place',
            rawDistance(residentA, originA.spawn()) < 0.2);
        check('failed entry returns the can with its space id',
            CC.spaceId(p.getMainHandItem()) !== null);
        var held = p.getMainHandItem().copy();
        p.setItemInHand(IH.MAIN_HAND, IS.EMPTY);
        var restored = new IE(level, anchorA.x, anchorA.y + 0.1, anchorA.z, held);
        restored.setUnlimitedLifetime();
        restored.setDefaultPickUpDelay();
        restored.setNoGravity(true);
        check('anchor restored for the next phase', level.addFreshEntity(restored));
        advance('failedCapture', 3);
        return;
    }

    if (phase === 'failedCapture') {
        p.setItemInHand(IH.MAIN_HAND, canStack());
        cow = ET.COW.create(level);
        cow.setPos(10.5, 200, 10.5);
        level.addFreshEntity(cow);
        var result;
        SD.push(residentA);
        try {
            result = SM.capture(p, cow, p.getMainHandItem());
        } finally {
            SD.pop();
        }
        check('rejected resident spawn cancels capture', !result);
        check('failed capture keeps one space', SM.spaceCount() === 1);
        check('failed capture keeps the source mob', cow.isAlive() && !cow.isRemoved());
        check('failed capture clears the can space id', CC.spaceId(p.getMainHandItem()) === null);
        check('failed capture did not add a resident',
            spaceA.residents().size() === 1 && SM.findEntity(spaceA, residentAId) !== null);
        advance('failedNewEntry', 1);
        return;
    }

    if (phase === 'failedNewEntry') {
        p.setItemInHand(IH.MAIN_HAND, canStack());
        var result;
        SD.push(residentA);
        try {
            result = SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND);
        } finally {
            SD.pop();
        }
        check('rejected new anchor spawn cancels entry', !result);
        check('failed new entry keeps one space', SM.spaceCount() === 1);
        check('failed new entry clears the can space id', CC.spaceId(p.getMainHandItem()) === null);
        check('failed new entry leaves the player outside', !SM.isInside(p));
        check('first space survived all rollbacks',
            SM.findEntity(spaceA, residentAId) !== null && !residentA.isRemoved());
        advance('cleanup', 1);
        return;
    }

    if (phase === 'cleanup') {
        if (cow !== null) cow.discard();
        p.setItemInHand(IH.MAIN_HAND, IS.EMPTY);
        SM.stop();
        var items = level.getEntitiesOfClass(IE, new AB(-16, 190, -16, 96, 215, 96));
        for (var i = 0; i < items.size(); i++) {
            if (CC.spaceId(items.get(i).getItem()) !== null) items.get(i).discard();
        }
        SM.saved(level.getServer());
        p.teleportTo(0.5, 200, 0.5);
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
