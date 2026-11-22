// NeoForge 1.21.1 virtual-space restart verification (round 3).
// Verifies restored spaces, restored derived entities, the preserved unparsable
// ledger record, anchored player/resident positions and prepares the client check.
var SM = Java.loadClass('cc.sighs.cannedmonster.space.SpaceManager');
var SD = Java.loadClass('cc.sighs.cannedmonster.space.SpaceDomain');
var SP = Java.loadClass('cc.sighs.cannedmonster.platform.SpacePlatform');
var STORE = Java.loadClass('cc.sighs.cannedmonster.platform.SpaceStore');
var ES = Java.loadClass('cc.sighs.cannedmonster.platform.EntityStorage');
var CC = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var Mob = Java.loadClass('net.minecraft.world.entity.Mob');
var AB = Java.loadClass('net.minecraft.world.phys.AABB');
var U = Java.loadClass('java.util.UUID');
var NbtIo = Java.loadClass('net.minecraft.nbt.NbtIo');
var NbtAccounter = Java.loadClass('net.minecraft.nbt.NbtAccounter');
var LevelResource = Java.loadClass('net.minecraft.world.level.storage.LevelResource');
var Files = Java.loadClass('java.nio.file.Files');
var Paths = Java.loadClass('java.nio.file.Paths');
var Component = Java.loadClass('net.minecraft.network.chat.Component');
var V = Java.loadClass('net.minecraft.world.phys.Vec3');
var Items = Java.loadClass('net.minecraft.world.item.Items');
var P = null;

var p = AutoJS.player(), level = p.level, server = level.getServer();
var checks = [];

function check(name, value) {
    checks.push({ name: name, pass: !!value });
    if (!value) {
        AutoJS.writeJson('space-restart.json', { checks: checks, failure: name });
        throw new Error(name);
    }
}
function roomBox(room) {
    return new AB(room.x(), room.y(), room.z(), room.x() + 13, room.y() + 8, room.z() + 13);
}
function rawPos(entity) {
    SD.push(entity);
    try { return [entity.getX(), entity.getY(), entity.getZ()]; } finally { SD.pop(); }
}
function distance(entity, vec) {
    var dx = entity.getX() - vec.x, dy = entity.getY() - vec.y, dz = entity.getZ() - vec.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
}
function rawDistance(entity, vec) {
    var pos = rawPos(entity);
    var dx = pos[0] - vec.x, dy = pos[1] - vec.y, dz = pos[2] - vec.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
}
function lookupUuid(targetLevel, uuid) {
    return SM.visibleEntity(targetLevel, uuid);
}

check('a space was restored', SM.spaceCount() >= 1);
check('player remembered as inside', SM.isInside(p));
var mine = SM.spaceForPlayer(p);
check('player rejoined the internal spawn', rawDistance(p, mine.internal().spawn()) < 1.0);
check('external player position is the anchor',
    Math.abs(p.getX() - mine.lastAnchor().x) < 0.01 && Math.abs(p.getZ() - mine.lastAnchor().z) < 0.01);

var keys = mine.residents().keySet().iterator();
var residentId = U.fromString(String(keys.next()));
var resident = SM.findEntity(mine, residentId);
check('resident restored', resident !== null && resident.isAlive() && !resident.isRemoved());
check('external resident position is the anchor',
    Math.abs(resident.getX() - mine.lastAnchor().x) < 0.01);
check('resident is hidden from external lookups', lookupUuid(level, residentId) === null);
var exposedByBox = level.getEntitiesOfClass(Mob, roomBox(mine.internal()));
var exposed = false;
for (var qi = 0; qi < exposedByBox.size(); qi++) if (exposedByBox.get(qi) === resident) exposed = true;
check('resident is not exposed by external AABB queries', !exposed);

// Derived entities survive the restart at their room-local offset.
var derivedFound = 0;
for (var id of SM.ownedEntities(mine)) {
    var entity = SM.findEntity(mine, U.fromString(String(id)));
    if (entity === null || entity.getType() !== ET.ITEM) continue;
    var stack = entity.getItem();
    if (stack.getItem() !== Items.DIRT) continue;
    derivedFound++;
    var pos = rawPos(entity);
    check('derived item restored at its local offset',
        Math.abs(pos[0] - (mine.internal().x() + 3.5)) < 1.5
        && Math.abs(pos[2] - (mine.internal().z() + 3.5)) < 1.5);
    check('derived item external position is the anchor', Math.abs(entity.getX() - mine.lastAnchor().x) < 0.01);
}
check('derived item restored [' + derivedFound + ']', derivedFound >= 1);

// The unparsable record must be preserved, not silently dropped by the next save.
var ledger = STORE.read(server).getList('Spaces', 10);
var bad = 0, good = 0;
for (var i = 0; i < ledger.size(); i++) {
    var entry = ledger.getCompound(i);
    if (String(ES.string(entry, 'Id')) === 'not-a-uuid') bad++;
    else good++;
}
check('unparsable ledger record preserved [' + bad + ']', bad >= 1);
check('good ledger records preserved [' + good + ']', good >= 1);
var orphanId = '11111111-2222-3333-4444-555555555555', orphanRecords = 0;
for (var oi = 0; oi < ledger.size(); oi++) {
    var entry = ledger.getCompound(oi);
    if (String(ES.string(entry, 'Id')) === orphanId) orphanRecords++;
}
check('valid-uuid unparsable record kept exactly once [' + orphanRecords + ']', orphanRecords === 1);

var playerFile = Paths.get(String(server.getWorldPath(LevelResource.PLAYER_DATA_DIR)),
    p.getGameProfile().getId().toString() + '.dat');
check('saveEverything succeeded', server.saveEverything(false, true, true));
check('player data was written to disk', Files.exists(playerFile));
var data = NbtIo.readCompressed(playerFile, NbtAccounter.unlimitedHeap());
var saved = data.getList('Pos', 6);
check('saved player position is the external anchor [disk=' + saved.getDouble(0) + ',' + saved.getDouble(2)
    + ' anchor=' + mine.lastAnchor().x + ',' + mine.lastAnchor().z + ']',
    Math.abs(saved.getDouble(0) - mine.lastAnchor().x) < 0.5
    && Math.abs(saved.getDouble(2) - mine.lastAnchor().z) < 0.5);

// Move and rename the resident so the client can verify movement and data sync.
SD.push(resident);
resident.setPos(resident.getX() + 1.5, resident.getY(), resident.getZ() + 1.0);
SD.pop();
resident.setCustomName(Component.literal('SpaceAuditCow'));
resident.setCustomNameVisible(true);
var rp = rawPos(resident);
var clientPoint = SP.clientPoint(mine, new V(rp[0], rp[1], rp[2]));

AutoJS.writeJson('space-audit-client.json', {
    clientX: Number(mine.client().x()), clientY: Number(mine.client().y()), clientZ: Number(mine.client().z()),
    internalX: Number(mine.internal().x()), internalY: Number(mine.internal().y()), internalZ: Number(mine.internal().z()),
    anchorX: Number(mine.lastAnchor().x), anchorY: Number(mine.lastAnchor().y), anchorZ: Number(mine.lastAnchor().z),
    residentClientX: Number(clientPoint.x), residentClientY: Number(clientPoint.y), residentClientZ: Number(clientPoint.z),
    residentName: 'SpaceAuditCow', spaces: Number(SM.spaceCount())
});
AutoJS.writeJson('space-restart.json', {
    checks: checks, playerPos: [String(p.getX()), String(p.getY()), String(p.getZ())], resident: String(residentId)
});
