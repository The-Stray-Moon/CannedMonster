// NeoForge 1.21.1 virtual-space regression.
// Dual coordinate space: residents live at local internal coordinates, the can
// anchor keeps the external position, and the client sees a synthetic room.
// Each phase runs from a server tick handler so rebasing/carrier discovery has
// real ticks; progress is published in a System property for polling.
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
var BL = Java.loadClass('net.minecraft.world.level.block.Blocks');
var AB = Java.loadClass('net.minecraft.world.phys.AABB');
var IE = Java.loadClass('net.minecraft.world.entity.item.ItemEntity');
var V = Java.loadClass('net.minecraft.world.phys.Vec3');
var Mob = Java.loadClass('net.minecraft.world.entity.Mob');
var Dir = Java.loadClass('net.minecraft.core.Direction');
var Hit = Java.loadClass('net.minecraft.world.phys.BlockHitResult');
var UseOn = Java.loadClass('net.minecraft.network.protocol.game.ServerboundUseItemOnPacket');
var U = Java.loadClass('java.util.UUID');
var Items = Java.loadClass('net.minecraft.world.item.Items');
var MinecartChest = Java.loadClass('net.minecraft.world.entity.vehicle.MinecartChest');
var SNDP = Java.loadClass('net.minecraft.network.protocol.game.ClientboundSoundPacket');
var PART = Java.loadClass('net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket');
var SE = Java.loadClass('net.minecraft.sounds.SoundEvents');
var SS = Java.loadClass('net.minecraft.sounds.SoundSource');
var PT = Java.loadClass('net.minecraft.core.particles.ParticleTypes');
var ADD = Java.loadClass('net.minecraft.network.protocol.game.ClientboundAddEntityPacket');
var HOLDER = Java.loadClass('net.minecraft.core.Holder');
var P = Java.loadClass('java.lang.System').getProperties();
// getEntity(UUID) is overloaded with getEntity(int) and Rhino picks int for a UUID;
// the Java helper keeps the filtered UUID lookup path.
function lookupUuid(targetLevel, uuid) { return SM.visibleEntity(targetLevel, uuid); }

var p = AutoJS.player(), level = p.level;
var checks = [], phase = 'boot', wait = 0, failure = null;
var space = null, spaceB = null, residentA = null, residentAId = null, residentAInt = 0, clientOrigin = null, lastAnchor = null;

function check(name, value) {
    checks.push({ name: name, pass: !!value });
    if (!value) throw new Error(name);
}
function report() {
    P.put('cannedmonster.space.result', JSON.stringify({ phase: phase, checks: checks, failure: failure }));
}
function advance(name, ticks) { phase = name; wait = ticks || 0; report(); }
function failed(error) { failure = String(error); phase = 'failed'; report(); console.error('space regression failed: ' + failure); }
function canStack() { return new IS(BR.ITEM.get(RL.tryParse('cannedmonster:spatial_monster_can'))); }
function roomBox(room) {
    return new AB(room.x(), room.y(), room.z(), room.x() + 13, room.y() + 8, room.z() + 13);
}
function hasEntity(iterable, entity) {
    var it = iterable.iterator();
    while (it.hasNext()) if (it.next() === entity) return true;
    return false;
}
function distance(entity, vec) {
    var dx = entity.getX() - vec.x, dy = entity.getY() - vec.y, dz = entity.getZ() - vec.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
}
// Internal coordinates are only visible inside a space frame; external reads are the anchor.
function rawPos(entity) {
    SD.push(entity);
    try { return [entity.getX(), entity.getY(), entity.getZ()]; } finally { SD.pop(); }
}
function rawDistance(entity, vec) {
    var pos = rawPos(entity);
    var dx = pos[0] - vec.x, dy = pos[1] - vec.y, dz = pos[2] - vec.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
}
function addPacket(entity, uuid) {
    return new ADD(entity.getId(), uuid, 0, 200, 0, 0, 0, entity.getType(), 0, entity.getDeltaMovement(), 0);
}
function projected(entity, space) {
    return Math.abs(entity.getX() - space.lastAnchor().x) < 0.01
        && Math.abs(entity.getZ() - space.lastAnchor().z) < 0.01;
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
function soundAt(room, dx, dy, dz) {
    return new SNDP(HOLDER.direct(SE.COW_AMBIENT), SS.NEUTRAL, room.x() + dx, room.y() + dy, room.z() + dz, 1.0, 1.0, 0);
}
function particlesAt(room, dx, dy, dz) {
    return new PART(PT.CLOUD, false, room.x() + dx, room.y() + dy, room.z() + dz, 0.0, 0.0, 0.0, 0.0, 3);
}

function step() {
    if (phase === 'boot') {
        P.put('cm.boot.step', 'clear');
        clearSpaces();
        P.put('cm.boot.step', 'commands');
        AutoJS.command('gamerule doMobSpawning false');
        AutoJS.command('gamerule mobGriefing false');
        AutoJS.command('gamerule doFireTick false');
        AutoJS.command('fill -8 199 -8 96 199 96 minecraft:stone');
        P.put('cm.boot.step', 'teleport');
        p.teleportTo(0.5, 200, 0.5);
        p.setHealth(p.getMaxHealth());
        P.put('cm.boot.step', 'hand');
        p.setItemInHand(IH.MAIN_HAND, canStack());
        var cow = ET.COW.create(level);
        cow.setPos(2.5, 200, 0.5);
        level.addFreshEntity(cow);
        P.put('cm.boot.step', 'capture');
        check('capture', SM.capture(p, cow, p.getMainHandItem()));
        P.put('cm.boot.step', 'entered');
        check('one space recorded', SM.spaceCount() === 1);
        check('enter', SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND) && SM.isInside(p));
        space = SM.spaceForPlayer(p);
        clientOrigin = space.client();
        check('internal and client origins differ',
            space.internal().x() !== space.client().x() || space.internal().z() !== space.client().z());
        check('server player at internal spawn', rawDistance(p, space.internal().spawn()) < 0.05);
        check('external player position is the anchor', projected(p, space));
        check('external position is not the virtual room',
            Math.abs(p.getX() - space.internal().spawn().x) > 20 && Math.abs(p.getZ() - space.internal().spawn().z) > 20);
        check('server stays in the same dimension', p.level === level);
        var keys = space.residents().keySet().iterator();
        residentAId = U.fromString(String(keys.next()));
        residentA = SM.findEntity(space, residentAId);
        residentAInt = residentA.getId();
        check('resident is a real world entity', residentA !== null && residentA.isAddedToLevel());
        check('resident is at the internal spawn', rawDistance(residentA, space.internal().spawn()) < 0.05);
        check('external resident position is the anchor', projected(residentA, space));

        var wall = new BP(space.internal().x() + 1, space.internal().y() + 3, space.internal().z() + 6);
        var realBlock = level.getBlockState(wall).getBlock();
        SD.push(p);
        var virtualBlock = level.getBlockState(wall).getBlock();
        var moved = p.getBoundingBox().move(wall.getX() + 0.5 - p.getX(), wall.getY() - p.getY(),
            wall.getZ() + 0.5 - p.getZ());
        var collision = !level.noCollision(p, moved);
        var wrote = level.setBlock(wall, BL.STONE.defaultBlockState(), 3);
        var outside = ET.COW.create(level);
        outside.setPos(space.internal().x() + 100, space.internal().y(), space.internal().z() + 100);
        var addedOutside = level.addFreshEntity(outside);
        SD.pop();
        check('real world has no room blocks', realBlock !== BL.BARRIER && realBlock !== BL.SMOOTH_QUARTZ);
        check('virtual room block is authoritative', virtualBlock === BL.SMOOTH_QUARTZ || virtualBlock === BL.BARRIER);
        check('virtual collision is authoritative', collision);
        check('real world writes are rejected', !wrote && level.getBlockState(wall).getBlock() === realBlock);
        check('outside entity spawn is rejected', !addedOutside);

        var rid = residentAId, rint = residentAInt;
        check('external all-entities hides resident', !hasEntity(level.getAllEntities(), residentA));
        check('external AABB hides resident', level.getEntitiesOfClass(Mob, roomBox(space.internal())).isEmpty());
        check('external uuid lookup hides resident', lookupUuid(level, rid) === null);
        check('external id lookup hides resident', level.getEntity(rint) === null);
        check('external player list hides inside player', level.players.isEmpty());
        check('runtime lookup still finds resident', SM.findEntity(space, rid) !== null);
        var anchorItem = SM.findEntity(space, space.anchorItem());
        check('anchor item is outside the room', anchorItem !== null
            && !space.internal().contains(anchorItem.getX(), anchorItem.getY(), anchorItem.getZ()));
        check('external lookup sees the anchor item', lookupUuid(level, space.anchorItem()) === anchorItem);
        advance('wallExit', 0);
        return;
    }

    if (phase === 'wallExit') {
        var hit = new Hit(new V(space.client().x() + 6.5, space.client().y() + 2.5, space.client().z() + 11.5),
            Dir.NORTH, new BP(Math.floor(space.client().x()) + 6, Math.floor(space.client().y()) + 2,
                Math.floor(space.client().z()) + 11), false);
        p.connection.handleUseItemOn(new UseOn(IH.MAIN_HAND, hit, 1));
        check('wall packet exits', !SM.isInside(p));
        check('exit lands at the external anchor', distance(p, space.lastAnchor()) < 4);
        advance('reenter', 1);
        return;
    }

    if (phase === 'reenter') {
        var item = SM.findEntity(space, space.anchorItem());
        check('anchor item survived the exit', item !== null);
        p.setItemInHand(IH.MAIN_HAND, item.getItem().copy());
        item.discard();
        check('reenter', SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND) && SM.isInside(p));
        advance('moveAnchor', 1);
        return;
    }

    if (phase === 'moveAnchor') {
        var before = space.internal();
        lastAnchor = space.lastAnchor();
        var item = SM.findEntity(space, space.anchorItem());
        check('anchor item exists before moving', item !== null);
        item.setPos(50.5, item.getY(), 50.5);
        advance('rebase', 6);
        return;
    }

    if (phase === 'rebase') {
        check('internal room followed the anchor [' + space.internal().x() + ',' + space.internal().z()
            + ' anchor ' + space.lastAnchor().x + ',' + space.lastAnchor().z + ']',
            Math.abs(space.internal().x() - 82) < 0.01 && Math.abs(space.internal().z() - 82) < 0.01);
        check('client room did not move', space.client().x() === clientOrigin.x() && space.client().z() === clientOrigin.z());
        check('inside player moved with the room [' + rawDistance(p, space.internal().spawn()) + ']',
            rawDistance(p, space.internal().spawn()) < 1.5);
        check('resident moved with the room [' + rawDistance(residentA, space.internal().spawn()) + ']',
            rawDistance(residentA, space.internal().spawn()) < 1.5);
        check('external position follows the moved anchor', projected(p, space) && Math.abs(p.getX() - 50.5) < 1.0);
        var it = SM.findEntity(space, space.anchorItem());
        check('anchor item stays external', it !== null && Math.abs(it.getX() - 50.5) < 0.01);
        AutoJS.command('setblock 52 200 52 minecraft:chest');
        var chest = level.getBlockEntity(new BP(52, 200, 52));
        check('chest block entity exists', chest !== null);
        var stack = it.getItem().copy();
        it.discard();
        chest.setItem(0, stack);
        chest.setChanged();
        advance('chestCarrier', 6);
        return;
    }

    if (phase === 'chestCarrier') {
        check('exit with a block container carries the space', SM.exit(p));
        check('block container exit lands above the chest',
            Math.abs(p.getX() - 52.5) < 0.6 && Math.abs(p.getZ() - 52.5) < 0.6 && p.getY() > 200.4 && p.getY() < 201.6);
        var chest = level.getBlockEntity(new BP(52, 200, 52));
        var stack = chest.removeItem(0, 1);
        chest.setChanged();
        p.setItemInHand(IH.MAIN_HAND, stack);
        var cart = ET.CHEST_MINECART.create(level);
        cart.setPos(54.5, 200, 54.5);
        check('chest minecart spawned', level.addFreshEntity(cart));
        check('enter with the recovered stack', SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND) && SM.isInside(p));
        var item = SM.findEntity(space, space.anchorItem());
        check('new anchor item exists', item !== null);
        cart.setItem(0, item.getItem().copy());
        item.discard();
        advance('entityCarrier', 6);
        return;
    }

    if (phase === 'entityCarrier') {
        check('exit with a container entity carries the space', SM.exit(p));
        check('container entity exit lands on the minecart',
            Math.abs(p.getX() - 54.5) < 0.6 && Math.abs(p.getZ() - 54.5) < 0.6);
        var cart = null, seen = 0;
        for (var e of level.getAllEntities()) {
            if (e.getType() !== ET.CHEST_MINECART) continue;
            seen++;
            if (CC.spaceId(e.getItem(0)) !== null) cart = e;
        }
        check('minecart still holds the can [' + seen + ' carts]', cart !== null);
        var stack = cart.removeItem(0, 1);
        p.setItemInHand(IH.MAIN_HAND, stack);
        check('reenter for the lost anchor case', SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND) && SM.isInside(p));
        var item = SM.findEntity(space, space.anchorItem());
        check('anchor item exists before losing it', item !== null);
        item.discard();
        advance('lostAnchor', 6);
        return;
    }

    if (phase === 'lostAnchor') {
        check('lost anchor refuses to exit', !SM.exit(p));
        check('player stays inside without an anchor', SM.isInside(p));
        var lostPos = rawPos(p);
        check('player is still inside the internal room',
            space.internal().contains(lostPos[0], lostPos[1], lostPos[2]));
        check('recover rescues the resident', SM.recover(p));
        check('recover leaves the space', !SM.isInside(p));
        check('recover drops the can at the last external anchor', distance(p, space.lastAnchor()) < 4);
        var cans = level.getEntitiesOfClass(IE, new AB(52, 198, 52, 58, 203, 58));
        var found = false;
        for (var i = 0; i < cans.size(); i++) if (CC.spaceId(cans.get(i).getItem()) !== null) found = true;
        check('recover produced a spatial can', found);
        advance('outsideView', 1);
        return;
    }

    if (phase === 'outsideView') {
        check('outside all-entities hides the resident', !hasEntity(level.getAllEntities(), residentA));
        check('outside AABB hides the resident', level.getEntitiesOfClass(Mob, roomBox(space.internal())).isEmpty());
        check('outside uuid lookup hides the resident', lookupUuid(level, residentAId) === null);
        check('outside player list is not empty', !level.players.isEmpty());
        check('outside sound from the room is dropped', SP.dropChunkPacket(p, soundAt(space.internal(), 6.5, 2, 6.5)));
        check('outside particle from the room is dropped', SP.dropChunkPacket(p, particlesAt(space.internal(), 6.5, 2, 6.5)));
        check('outside sound at the anchor is kept', !SP.dropChunkPacket(p, soundAt(space.internal(), -16, 0, -16)));
        advance('secondSpace', 1);
        return;
    }

    if (phase === 'secondSpace') {
        p.setItemInHand(IH.MAIN_HAND, canStack());
        var cow = ET.COW.create(level);
        cow.setPos(-2.5, 200, 0.5);
        level.addFreshEntity(cow);
        check('capture second space', SM.capture(p, cow, p.getMainHandItem()));
        check('two spaces recorded', SM.spaceCount() === 2);
        check('enter second space', SM.enter(p, p.getMainHandItem(), IH.MAIN_HAND) && SM.isInside(p));
        spaceB = SM.spaceForPlayer(p);
        check('second space has its own room', spaceB.id() !== space.id());
        check('second room does not overlap the first',
            !spaceB.internal().contains(space.internal().spawn().x, space.internal().spawn().y, space.internal().spawn().z));

        check('inside all-entities hides the other resident', !hasEntity(level.getAllEntities(), residentA));
        check('inside AABB hides the other resident', level.getEntitiesOfClass(Mob, roomBox(space.internal())).isEmpty());
        check('inside uuid lookup hides the other resident', lookupUuid(level, residentAId) === null);
        check('runtime lookup still finds the other resident', SM.findEntity(space, residentAId) !== null);
        check('other resident is still alive', residentA.isAlive() && !residentA.isRemoved());
        SD.push(p);
        var ownResidents = level.getEntitiesOfClass(Mob, roomBox(spaceB.internal()));
        var otherRoom = level.getEntitiesOfClass(Mob, roomBox(space.internal()));
        SD.pop();
        check('space frame sees its own resident', !ownResidents.isEmpty());
        check('space frame hides the other room', otherRoom.isEmpty());

        check('packet for the other space resident is dropped', SP.dropChunkPacket(p, addPacket(residentA, residentAId)));
        check('packet for the own resident is kept',
            !SP.dropChunkPacket(p, addPacket(ownResidents.get(0), U.fromString(String(spaceB.residents().keySet().iterator().next())))));

        var offset = SM.packetOffset(p);
        var own = SP.translateOutbound(p, soundAt(spaceB.internal(), 6.5, 2, 6.5));
        check('own space sound is projected to the client room',
            Math.abs(own.getX() - (spaceB.internal().x() + 6.5 - offset.x)) < 0.01
            && Math.abs(own.getZ() - (spaceB.internal().z() + 6.5 - offset.z)) < 0.01);
        check('own space sound is sent', !SP.dropChunkPacket(p, soundAt(spaceB.internal(), 6.5, 2, 6.5)));
        check('other space sound is isolated', SP.dropChunkPacket(p, soundAt(space.internal(), 6.5, 2, 6.5)));
        check('external sound is isolated', SP.dropChunkPacket(p, soundAt(spaceB.internal(), -32, 0, -32)));
        var ownParticles = SP.translateOutbound(p, particlesAt(spaceB.internal(), 6.5, 2, 6.5));
        check('own space particles are projected to the client room',
            Math.abs(ownParticles.getX() - (spaceB.internal().x() + 6.5 - offset.x)) < 0.01);
        check('own space particles are sent', !SP.dropChunkPacket(p, particlesAt(spaceB.internal(), 6.5, 2, 6.5)));
        check('other space particles are isolated', SP.dropChunkPacket(p, particlesAt(space.internal(), 6.5, 2, 6.5)));
        check('external particles are isolated', SP.dropChunkPacket(p, particlesAt(spaceB.internal(), -32, 0, -32)));

        // Overlap the two internal rooms and prove identity is not coordinates.
        var anchorA = SM.findEntity(space, space.anchorItem());
        check('first space still has an anchor to move', anchorA !== null);
        var targetX = spaceB.internal().x() + 4, targetZ = spaceB.internal().z() + 4;
        var dx = targetX - space.internal().x(), dz = targetZ - space.internal().z();
        anchorA.setPos(anchorA.getX() + dx, anchorA.getY(), anchorA.getZ() + dz);
        advance('overlap', 6);
        return;

        advance('overlap', 0);
        return;
    }

    if (phase === 'overlap') {
        var probe = [spaceB.internal().x() + 6, spaceB.internal().y() + 2, spaceB.internal().z() + 6];
        var hits = SM.spacesAt(level, new V(probe[0], probe[1], probe[2]));
        check('rooms now overlap geometrically [' + hits.size() + ']', hits.size() >= 2);
        check('resident identity stays with its own space', SM.spaceOf(residentA) === space);
        check('player identity stays with the second space', SM.spaceForPlayer(p) === spaceB);

        var wallPoint = [space.internal().x() + 1, space.internal().y() + 3, space.internal().z() + 6];
        SD.push(residentA);
        var fromA = level.getBlockState(new BP(wallPoint[0], wallPoint[1], wallPoint[2])).getBlock();
        SD.pop();
        SD.push(p);
        var fromB = level.getBlockState(new BP(wallPoint[0], wallPoint[1], wallPoint[2])).getBlock();
        var boxed = level.getEntitiesOfClass(Mob, roomBox(space.internal()));
        var bHidesA = true;
        for (var bi = 0; bi < boxed.size(); bi++) if (boxed.get(bi) === residentA) bHidesA = false;
        SD.pop();
        check('first room resolves its own wall in the overlap',
            fromA === BL.SMOOTH_QUARTZ || fromA === BL.BARRIER);
        check('second room resolves the same world position independently', fromB === BL.AIR);
        check('second room still hides the first resident in the overlap', bHidesA);

        var secondId = U.fromString(String(spaceB.residents().keySet().iterator().next()));
        var movedB = SM.findEntity(spaceB, secondId);
        check('second resident still belongs to the second space',
            movedB !== null && SM.spaceOf(movedB) === spaceB);
        check('overlap does not leak external queries',
            !hasEntity(level.getAllEntities(), residentA) && !hasEntity(level.getAllEntities(), movedB));
        check('overlap keeps sound isolation', SP.dropChunkPacket(p, soundAt(space.internal(), 6.5, 2, 6.5)));

        P.put('cannedmonster.space.client', JSON.stringify({
            x: spaceB.client().x(), y: spaceB.client().y(), z: spaceB.client().z(),
            internalX: spaceB.internal().x(), internalY: spaceB.internal().y(), internalZ: spaceB.internal().z(),
            spaceA: space.id().toString(), spaceB: spaceB.id().toString()
        }));
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
