// NeoForge 1.21.1 client-side check for the synthetic virtual room (round 3).
// Run with the coordinates published by space-restart-check.js.
var BP = Java.loadClass('net.minecraft.core.BlockPos');
var BL = Java.loadClass('net.minecraft.world.level.block.Blocks');
var AB = Java.loadClass('net.minecraft.world.phys.AABB');
var Cow = Java.loadClass('net.minecraft.world.entity.animal.Cow');

var info = AutoJS.readJson('space-audit-client.json');
var roomX = info.clientX === undefined ? info.x : info.clientX;
var roomY = info.clientY === undefined ? info.y : info.clientY;
var roomZ = info.clientZ === undefined ? info.z : info.clientZ;
var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var p = mc.player, level = mc.level;
var checks = [];
function check(name, value) {
    checks.push({ name: name, pass: !!value });
    if (!value) {
        AutoJS.writeJson('space-client-check.json', { checks: checks, failure: name });
        throw new Error(name);
    }
}

var inside = p.getX() >= roomX && p.getX() < roomX + 13
    && p.getY() >= roomY && p.getY() < roomY + 8
    && p.getZ() >= roomZ && p.getZ() < roomZ + 13;
check('client player is in the synthetic room', inside);
check('client player is not at the internal coordinates',
    Math.abs(p.getX() - info.internalX) > 1 || Math.abs(p.getZ() - info.internalZ) > 1);

var wall = level.getBlockState(new BP(roomX + 1, roomY + 3, roomZ + 6)).getBlock();
var floor = level.getBlockState(new BP(roomX + 6, roomY + 1, roomZ + 6)).getBlock();
var middle = level.getBlockState(new BP(roomX + 6, roomY + 3, roomZ + 6)).getBlock();
check('client room wall is rendered', wall === BL.BARRIER || wall === BL.SMOOTH_QUARTZ);
check('client room floor is rendered', floor === BL.SMOOTH_QUARTZ);
check('client room interior is air', middle === BL.AIR);

var box = new AB(roomX, roomY, roomZ, roomX + 13, roomY + 8, roomZ + 13);
var cows = level.getEntitiesOfClass(Cow, box);
check('resident is visible on the client', !cows.isEmpty());
var cow = cows.get(0);
var name = cow.getCustomName() === null ? '' : String(cow.getCustomName().getString());
check('resident data sync reached the client [' + name + ']', name === info.residentName);
check('resident movement sync reached the client [' + cow.getX() + ',' + cow.getZ() + ']',
    Math.abs(cow.getX() - info.residentClientX) < 1.5 && Math.abs(cow.getZ() - info.residentClientZ) < 1.5);
check('client sees the internal coordinates as air',
    level.getBlockState(new BP(info.internalX + 1, info.internalY + 3, info.internalZ + 6)).getBlock() === BL.AIR);

AutoJS.writeJson('space-client-check.json', { checks: checks,
    position: [String(p.getX()), String(p.getY()), String(p.getZ())], cows: Number(cows.size()),
    cowName: name, cowPos: [String(cow.getX()), String(cow.getZ())] });
