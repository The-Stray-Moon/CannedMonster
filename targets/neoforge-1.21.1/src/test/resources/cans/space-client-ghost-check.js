// NeoForge 1.21.1 client-side ghost check: after leaving the space the client must
// no longer hold the room entities and the real chunk must be restored.
var BP = Java.loadClass('net.minecraft.core.BlockPos');
var BL = Java.loadClass('net.minecraft.world.level.block.Blocks');
var Cow = Java.loadClass('net.minecraft.world.entity.animal.Cow');

var info = AutoJS.readJson('space-audit-client.json');
var roomX = info.clientX === undefined ? info.x : info.clientX;
var roomY = info.clientY === undefined ? info.y : info.clientY;
var roomZ = info.clientZ === undefined ? info.z : info.clientZ;
var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var level = mc.level;
var checks = [];
function check(name, value) {
    checks.push({ name: name, pass: !!value });
    if (!value) {
        AutoJS.writeJson('space-client-ghost-check.json', { checks: checks, failure: name });
        throw new Error(name);
    }
}
var box = new AB(roomX - 2, roomY - 2, roomZ - 2, roomX + 15, roomY + 10, roomZ + 15);
check('no resident remains after exit', level.getEntitiesOfClass(Cow, box).isEmpty());
check('real chunk restored after exit',
    level.getBlockState(new BP(roomX + 1, roomY + 3, roomZ + 6)).getBlock() === BL.AIR
    && level.getBlockState(new BP(roomX + 6, roomY + 1, roomZ + 6)).getBlock() === BL.AIR);
AutoJS.writeJson('space-client-ghost-check.json', { checks: checks });
