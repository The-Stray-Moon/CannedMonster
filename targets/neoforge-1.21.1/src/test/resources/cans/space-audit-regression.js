// NeoForge 1.21.1 virtual-space audit regression (round 3).
// Covers external bbox/blockPosition, derived entities, real send-chain sound/
// particle filtering, real EntityJoinLevelEvent cancellation, tick death, resident
// death cleanup, all six exit faces, and the restart setup for persistence.
var SM = Java.loadClass('cc.sighs.cannedmonster.space.SpaceManager');
var SD = Java.loadClass('cc.sighs.cannedmonster.space.SpaceDomain');
var SP = Java.loadClass('cc.sighs.cannedmonster.platform.SpacePlatform');
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
var SE = Java.loadClass('net.minecraft.sounds.SoundEvents');
var SS = Java.loadClass('net.minecraft.sounds.SoundSource');
var PT = Java.loadClass('net.minecraft.core.particles.ParticleTypes');
var PART = Java.loadClass('net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket');
var Dir = Java.loadClass('net.minecraft.core.Direction');
var Hit = Java.loadClass('net.minecraft.world.phys.BlockHitResult');
var UseOn = Java.loadClass('net.minecraft.network.protocol.game.ServerboundUseItemOnPacket');
var Items = Java.loadClass('net.minecraft.world.item.Items');
var STORE = Java.loadClass('cc.sighs.cannedmonster.platform.SpaceStore');
var Tag = Java.loadClass('net.minecraft.nbt.CompoundTag');

var p = null, level = null;
var checks = [], phase = 'boot', wait = 0, failure = null;
var space = null, resident = null, residentId = null, lastAnchor = null, derivedId = null;
var derivedItem = null, anchorItem = null, derivedBefore = null, anchorBefore = null, derivedLocalBefore = null, internalBefore = null;
var faces = [Dir.DOWN, Dir.UP, Dir.NORTH, Dir.SOUTH, Dir.WEST, Dir.EAST], faceIndex = 0;

function check(name, value) {
    checks.push({ name: name, pass: !!value });
    if (!value) throw new Error(name);
}
function report() {
    AutoJS.writeJson('space-audit.json', { phase: phase, checks: checks, failure: failure });
}
function advance(name, ticks) { phase = name; wait = ticks || 0; report(); }
function failed(error) {
    failure = String(error); phase = 'failed'; report();
    console.error('space audit regression failed: ' + failure);
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
function hasEntity(iterable, entity) {
    var it = iterable.iterator();
    while (it.hasNext()) if (it.next() === entity) return true;
    return false;
}
function clearSpaces() {
    var ids = SM.spaceIds();
    for (var i = 0; i < ids.size(); i++) {
        var s = SM.spaceById(ids.get(i));
        if (s === null) continue;
        var owned = SM.ownedEntities(s).iterator();
        while (owned.hasNext()) {
            var e = SM.findEntity(s, U.fromString(String(owned.next())));
            if (e !== null) e.discard();
        }
    }
    SM.stop();
    var items = level.getEntitiesOfClass(IE, new AB(-32, 180, -32, 120, 220, 120));
    for (var j = 0; j < items.size(); j++) {
        var stack = items.get(j).getItem();
        if (CC.spaceId(stack) !== null || stack.getItem() === Items.DIRT) items.get(j).discard();
    }
    var marks = [];
    for (var e of level.getAllEntities()) {
        if (e.getType() === ET.CHEST_MINECART || e.getType() === ET.COW) marks.push(e);
    }
    for (var k = 0; k < marks.length; k++) marks[k].discard();
}
function enterFresh(cowX) {
    p.setItemInHand(IH.MAIN_HAND, canStack());
    var cow = ET.COW.create(level);
    cow.setPos(cowX, 200, 0.5);
    level.addFreshEntity(cow);
    check('capture', SM.capture(p, cow, p.getMainHandItem()));
    check('enter', SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND) && SM.isInside(p));
    space = SM.spaceForPlayer(p);
    lastAnchor = space.lastAnchor();
    residentId = U.fromString(String(space.residents().keySet().iterator().next()));
    resident = SM.findEntity(space, residentId);
}
function clickFace(dir) {
    var room = space.client();
    var x = Math.floor(room.x()) + 6, y = Math.floor(room.y()) + 4, z = Math.floor(room.z()) + 6;
    var face = dir;
    if (dir === Dir.DOWN) { y = Math.floor(room.y()); face = Dir.UP; }
    else if (dir === Dir.UP) { y = Math.floor(room.y()) + 7; face = Dir.DOWN; }
    else if (dir === Dir.NORTH) { z = Math.floor(room.z()); face = Dir.SOUTH; }
    else if (dir === Dir.SOUTH) { z = Math.floor(room.z()) + 12; face = Dir.NORTH; }
    else if (dir === Dir.WEST) { x = Math.floor(room.x()); face = Dir.EAST; }
    else { x = Math.floor(room.x()) + 12; face = Dir.WEST; }
    var hit = new Hit(new V(x + 0.5, y + 0.5, z + 0.5), face, new BP(x, y, z), false);
    p.connection.handleUseItemOn(new UseOn(IH.MAIN_HAND, hit, 1));
}

function step() {
    if (phase === 'boot') {
        p = AutoJS.player();
        if (p === null) return;
        level = p.level;
        clearSpaces();
        AutoJS.command('gamemode creative Dev');
        p.teleportTo(0.5, 200, 0.5);
        p.setHealth(p.getMaxHealth());
        enterFresh(2.5);
        advance('bootCheck', 5);
        return;
    }

    if (phase === 'bootCheck') {
        resident = SM.findEntity(space, residentId);
        check('resident exists', resident !== null);
        resident.setSilent(true);
        var center = resident.getBoundingBox().getCenter();
        check('external bbox center is at the anchor',
            Math.abs(center.x - lastAnchor.x) < 0.01 && Math.abs(center.z - lastAnchor.z) < 0.01);
        var bp = resident.blockPosition();
        check('external block position is the anchor block',
            bp.getX() === Math.floor(lastAnchor.x) && bp.getZ() === Math.floor(lastAnchor.z));
        check('external bbox is not in the room', !space.internal().contains(center.x, center.y, center.z));
        SD.push(p);
        SD.push(resident);
        SD.invalidate(p);
        var residentFrameKept = SD.frame().active();
        SD.pop();
        var playerFrameGone = !SD.frame().active();
        SD.pop();
        check('invalidating the player frame keeps the resident frame', residentFrameKept && playerFrameGone);
        advance('derived', 1);
        return;
    }

    if (phase === 'derived') {
        SD.push(resident);
        var item = null;
        try {
            item = new IE(level, space.internal().x() + 4.5, space.internal().y() + 4,
                space.internal().z() + 4.5, new IS(Items.DIRT, 5));
            check('derived item added inside the room', level.addFreshEntity(item));
        } finally {
            SD.pop();
        }
        derivedId = U.fromString(String(space.derived().keySet().iterator().next()));
        check('derived entity is space owned', SM.spaceOf(item) === space);
        check('derived entity is hidden externally', !hasEntity(level.getAllEntities(), item));
        check('derived entity external position is the anchor', Math.abs(item.getX() - lastAnchor.x) < 0.01);
        SD.push(resident);
        try { item.setPos(space.internal().x() + 5.5, space.internal().y() + 4, space.internal().z() + 5.5); }
        finally { SD.pop(); }
        derivedItem = item;
        anchorItem = SM.findEntity(space, space.anchorItem());
        check('anchor item carrier exists', anchorItem !== null);
        derivedBefore = rawPos(item);
        anchorBefore = space.lastAnchor();
        derivedLocalBefore = [derivedBefore[0] - space.internal().x(), derivedBefore[2] - space.internal().z()];
        internalBefore = [space.internal().x(), space.internal().z()];
        SD.push(resident);
        try { anchorItem.setPos(anchorItem.getX() + 10, anchorItem.getY(), anchorItem.getZ() + 6); }
        finally { SD.pop(); }
        advance('derivedMoveCheck', 2);
        return;
    }

    if (phase === 'derivedMoveCheck') {
        var after = rawPos(derivedItem);
        var anchorAfter = space.lastAnchor();
        check('anchor moved by the carrier delta',
            Math.abs(anchorAfter.x - anchorBefore.x - 10) < 0.5 && Math.abs(anchorAfter.z - anchorBefore.z - 6) < 0.5);
        check('derived entity moved with the whole space',
            Math.abs(after[0] - derivedBefore[0] - 10) < 0.5 && Math.abs(after[2] - derivedBefore[2] - 6) < 0.5);
        var localX = after[0] - space.internal().x(), localZ = after[2] - space.internal().z();
        var insideMoved = space.internal().contains(after[0], after[1], after[2]);
        check('derived entity stays inside the moved room ' + JSON.stringify({after: after,
            internalAfter: [space.internal().x(), space.internal().z()], local: [localX, localZ]}), insideMoved);
        advance('soundChain', 2);
        return;
    }

    if (phase === 'soundChain') {
        SP.resetPacketStats();
        var ix = space.internal().x(), iy = space.internal().y(), iz = space.internal().z();
        // Big range: vanilla already reaches the projected anchor; the redelivery must not duplicate.
        level.playSound(null, ix + 1.0, iy + 2, iz + 6.5, SE.COW_AMBIENT, SS.NEUTRAL, 8.0, 1.0);
        // Small range: vanilla misses the projected anchor; redelivery sends exactly once.
        level.playSound(null, ix + 6.5, iy + 2, iz + 6.5, SE.COW_AMBIENT, SS.NEUTRAL, 1.0, 1.0);
        // Anchor sound: vanilla sends once.
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SE.COW_AMBIENT, SS.NEUTRAL, 8.0, 1.0);
        // Outside the room but within the projected range: sent by vanilla, then dropped by the send chain.
        level.playSound(null, ix + 20.0, iy + 2, iz + 6.5, SE.COW_AMBIENT, SS.NEUTRAL, 8.0, 1.0);
        advance('soundResult', 2);
        return;
    }

    if (phase === 'soundResult') {
        var stats = SP.packetStats();
        check('real send chain sends each room/anchor sound once [' + stats[0] + ']', stats[0] === 3);
        check('real send chain drops the outside sound once [' + stats[1] + ']', stats[1] === 1);
        SP.resetPacketStats();
        level.sendParticles(PT.CLOUD, space.internal().x() + 6.5, space.internal().y() + 2,
            space.internal().z() + 6.5, 3, 0, 0, 0, 0);
        advance('particleResult', 2);
        return;
    }

    if (phase === 'particleResult') {
        var stats = SP.packetStats();
        check('real send chain redelivers room particles once [' + stats[2] + ']', stats[2] === 1);
        SP.resetPacketStats();
        p.connection.send(new PART(PT.CLOUD, false, space.internal().x() + 20.0, space.internal().y() + 2,
            space.internal().z() + 6.5, 0, 0, 0, 0, 3));
        advance('particleOutside', 2);
        return;
    }

    if (phase === 'particleOutside') {
        var stats = SP.packetStats();
        check('real send chain drops the outside particles once [' + stats[3] + ']', stats[3] === 1);
        if (SM.isInside(p)) SM.exit(p);
        SM.stop();
        p.teleportTo(0.5, 200, 0.5);
        advance('eventCancel', 1);
        return;
    }

    if (phase === 'eventCancel') {
        AutoJS.command('cannedmonster space canceljoin minecraft:item 1');
        p.setItemInHand(IH.MAIN_HAND, canStack());
        var before = SM.spaceCount();
        var ok = SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND);
        check('real EntityJoinLevelEvent cancellation cancels entry', !ok);
        check('canceled entry leaves the player outside', !SM.isInside(p));
        check('canceled entry keeps the space count', SM.spaceCount() === before);
        check('canceled entry clears the can id', CC.spaceId(p.getMainHandItem()) === null);
        var cow = ET.COW.create(level);
        cow.setPos(2.5, 200, 0.5);
        level.addFreshEntity(cow);
        AutoJS.command('cannedmonster space canceljoin minecraft:cow 1');
        p.setItemInHand(IH.MAIN_HAND, canStack());
        var spaceCount = SM.spaceCount();
        var captured = SM.capture(p, cow, p.getMainHandItem());
        check('real event cancellation cancels capture', !captured);
        check('canceled capture keeps the space count', SM.spaceCount() === spaceCount);
        check('canceled capture clears the can id', CC.spaceId(p.getMainHandItem()) === null);
        check('canceled capture keeps the source mob', cow.isAlive() && !cow.isRemoved());
        cow.discard();
        advance('faces', 1);
        return;
    }

    if (phase === 'faces') {
        if (faceIndex >= faces.length) {
            advance('recover', 1);
            return;
        }
        if (SM.isInside(p)) SM.exit(p);
        p.teleportTo(0.5, 200, 0.5);
        p.setItemInHand(IH.MAIN_HAND, canStack());
        var cow = ET.COW.create(level);
        cow.setPos(2.5, 200, 0.5);
        level.addFreshEntity(cow);
        check('capture face ' + faceIndex, SM.capture(p, cow, p.getMainHandItem()));
        check('enter face ' + faceIndex, SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND));
        space = SM.spaceForPlayer(p);
        clickFace(faces[faceIndex]);
        check('exit through ' + faces[faceIndex], !SM.isInside(p));
        faceIndex++;
        advance('faces', 1);
        return;
    }

    if (phase === 'recover') {
        clearSpaces();
        p.teleportTo(0.5, 200, 0.5);
        enterFresh(2.5);
        var anchorId = space.anchorItem();
        check('recover refused while a valid anchor exists', !SM.recover(p));
        var cans = 0;
        var anchorNow = space.lastAnchor();
        var list = level.getEntitiesOfClass(IE, new AB(anchorNow.x - 16, anchorNow.y - 8, anchorNow.z - 16,
            anchorNow.x + 16, anchorNow.y + 8, anchorNow.z + 16));
        for (var ci = 0; ci < list.size(); ci++) {
            if (CC.spaceId(list.get(ci).getItem()) !== null) cans++;
        }
        check('refused recover did not mint a second can [' + cans + ']', cans === 1);
        var carrier = SM.findEntity(space, anchorId);
        check('the original anchor carrier is still present', carrier !== null);
        carrier.discard();
        advance('recoverAfterLoss', 2);
        return;
    }

    if (phase === 'recoverAfterLoss') {
        check('recover works after the carrier is gone', SM.recover(p));
        check('recovered player left the space', !SM.isInside(p));
        var anchorNow = space.lastAnchor();
        var cans = 0;
        var list = level.getEntitiesOfClass(IE, new AB(anchorNow.x - 16, anchorNow.y - 8, anchorNow.z - 16,
            anchorNow.x + 16, anchorNow.y + 8, anchorNow.z + 16));
        for (var ci = 0; ci < list.size(); ci++) {
            if (CC.spaceId(list.get(ci).getItem()) !== null) cans++;
        }
        check('recover created exactly one anchor can [' + cans + ']', cans === 1);
        advance('unload', 1);
        return;
    }

    if (phase === 'unload') {
        clearSpaces();
        p.teleportTo(0.5, 200, 0.5);
        enterFresh(2.5);
        var ER = Java.loadClass('net.minecraft.world.entity.Entity');
        SD.push(resident);
        try { resident.setRemoved(ER.RemovalReason.UNLOADED_WITH_PLAYER); } finally { SD.pop(); }
        SM.saved(level.getServer());
        check('resident row survives a player-unload removal', space.residents().containsKey(residentId));
        var ledger = STORE.read(level.getServer()).getList('Spaces', 10);
        var kept = 0;
        for (var ui = 0; ui < ledger.size(); ui++) {
            var room = ledger.getCompound(ui);
            if (String(room.getString('Id')) !== String(space.id())) continue;
            kept = room.getList('Residents', 10).size();
        }
        check('ledger keeps the resident after player unload [' + kept + ']', kept === 1);
        resident.discard();
        advance('residentDeath', 1);
        return;
    }

    if (phase === 'residentDeath') {
        clearSpaces();
        p.teleportTo(0.5, 200, 0.5);
        enterFresh(2.5);
        SD.push(resident);
        try { resident.kill(); } finally { SD.pop(); }
        advance('residentDeathCheck', 3);
        return;
    }

    if (phase === 'residentDeathCheck') {
        check('resident died', resident.isDeadOrDying() || resident.isRemoved());
        check('dead resident stays quarantined', SM.spaceOf(resident) === space && SM.isQuarantined(resident));
        SM.saved(level.getServer());
        var ledger = STORE.read(level.getServer()).getList('Spaces', 10);
        var savedResidents = -1;
        for (var i = 0; i < ledger.size(); i++) {
            var room = ledger.getCompound(i);
            if (String(room.getString('Id')) !== String(space.id())) continue;
            savedResidents = room.getList('Residents', 10).size();
        }
        check('quarantined resident is not written to the ledger [' + savedResidents + ']', savedResidents === 0);
        advance('residentLoot', 3);
        return;
    }

    if (phase === 'residentLoot') {
        var loot = 0;
        for (var id of SM.ownedEntities(space)) {
            var e = SM.findEntity(space, U.fromString(String(id)));
            if (e !== null && e.getType() === ET.ITEM) loot++;
        }
        check('resident death drops stay space-owned [' + loot + ']', loot >= 1);
        SM.saved(level.getServer());
        advance('residentRemoval', 25);
        return;
    }

    if (phase === 'residentRemoval') {
        check('vanilla removal ends the quarantine', SM.spaceOf(resident) === null && !SM.isQuarantined(resident));
        check('terminal removal clears resident data', space.residents().isEmpty());
        advance('tickDeath', 1);
        return;
    }

    if (phase === 'tickDeath') {
        clearSpaces();
        AutoJS.command('gamemode survival Dev');
        p.teleportTo(0.5, 200, 0.5);
        p.setHealth(p.getMaxHealth());
        enterFresh(2.5);
        p.getInventory().add(new IS(Items.DIRT, 3));
        p.setHealth(3.0);
        p.invulnerableTime = 0;
        p.setRemainingFireTicks(400);
        wait = 0;
        advance('deadTick', 0);
        return;
    }

    if (phase === 'deadTick') {
        if (!p.isDeadOrDying() && p.isAlive() && wait < 40) {
            wait++;
            return;
        }
        check('player died inside the tick', p.isDeadOrDying() || !p.isAlive());
        check('death left the space', !SM.isInside(p));
        var anchor = space.lastAnchor();
        var atAnchor = 0, inRoom = 0;
        for (var e of level.getAllEntities()) {
            if (e.getType() !== ET.ITEM) continue;
            var stack = e.getItem();
            if (stack.getItem() !== Items.DIRT) continue;
            if (Math.abs(e.getX() - anchor.x) < 8 && Math.abs(e.getZ() - anchor.z) < 8) atAnchor += stack.getCount();
            if (space.internal().contains(e.getX(), e.getY(), e.getZ())) inRoom += stack.getCount();
        }
        check('tick death keeps the inventory at the anchor [' + atAnchor + ']', atAnchor >= 3);
        check('tick death leaks nothing into the room [' + inRoom + ']', inRoom === 0);
        advance('restartSetup', 1);
        return;
    }

    if (phase === 'restartSetup') {
        var C = Java.loadClass('net.minecraft.network.protocol.game.ServerboundClientCommandPacket');
        p.connection.handleClientCommand(new C(C.Action.PERFORM_RESPAWN));
        AutoJS.command('gamemode creative Dev');
        p = AutoJS.player();
        level = p.level;
        p.teleportTo(0.5, 200, 0.5);
        clearSpaces();
        // An unparsable record with a valid UUID must never be shadowed by a new space
        // with the same id (that would create two ledger records).
        var orphanId = '11111111-2222-3333-4444-555555555555';
        var disk = STORE.read(level.getServer());
        var diskList = disk.getList('Spaces', 10);
        var orphan = new Tag();
        orphan.putString('Id', orphanId);
        orphan.putString('Dimension', 'cannedmonster:missing_dimension');
        diskList.add(orphan);
        var diskTag = new Tag();
        diskTag.put('Spaces', diskList);
        STORE.write(level.getServer(), diskTag);
        STORE.saveNow(level.getServer());
        SM.saved(level.getServer());
        var beforeOrphan = SM.spaceCount();
        var orphanCan = canStack();
        CC.storeSpace(orphanCan, U.fromString(orphanId), 0);
        p.setItemInHand(IH.MAIN_HAND, orphanCan);
        check('entry with an unparsable id is refused', !SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND));
        check('refused orphan id did not create a space', SM.spaceCount() === beforeOrphan);
        p.setItemInHand(IH.MAIN_HAND, IS.EMPTY);
        SM.saved(level.getServer());
        var afterOrphan = STORE.read(level.getServer()).getList('Spaces', 10);
        var orphanRecords = 0;
        for (var oi = 0; oi < afterOrphan.size(); oi++) {
            if (String(afterOrphan.getCompound(oi).getString('Id')) === orphanId) orphanRecords++;
        }
        check('unparsable id has exactly one ledger record [' + orphanRecords + ']', orphanRecords === 1);
        enterFresh(2.5);
        SD.push(resident);
        var item = null;
        try {
            item = new IE(level, space.internal().x() + 3.5, space.internal().y() + 4,
                space.internal().z() + 3.5, new IS(Items.DIRT, 2));
            check('restart derived item added', level.addFreshEntity(item));
        } finally {
            SD.pop();
        }
        check('restart derived item owned', SM.spaceOf(item) === space);
        // Append a deliberately unloadable record; it must survive the next save.
        var ledger = STORE.read(level.getServer());
        var list = ledger.getList('Spaces', 10);
        var bad = new Tag();
        bad.putString('Id', 'not-a-uuid');
        bad.putString('Dimension', 'cannedmonster:missing_dimension');
        list.add(bad);
        var tag = new Tag();
        tag.put('Spaces', list);
        STORE.write(level.getServer(), tag);
        STORE.saveNow(level.getServer());
        SM.saved(level.getServer());
        AutoJS.writeJson('space-audit-client.json', {
            x: space.client().x(), y: space.client().y(), z: space.client().z(),
            internalX: space.internal().x(), internalY: space.internal().y(), internalZ: space.internal().z(),
            space: space.id().toString()
        });
        advance('done', 0);
        return;
    }
}

ServerEvents.tick(function (event) {
    if (phase === 'done' || phase === 'failed') return;
    if (p === null && phase !== 'boot') return;
    try {
        if (wait > 0) { wait--; return; }
        step();
    } catch (error) {
        failed(error);
    }
});
report();
JSON.stringify({ started: true, phase: phase });
