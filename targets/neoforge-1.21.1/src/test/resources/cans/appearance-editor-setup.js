// Server-side setup for the fusion appearance editor / rotating can tooltip E2E.
//
// Run with AutoJS eval_js on side=server inside the disposable world
// "Can Editor E2E" (created in the dedicated run-space-test game directory).
// The script refuses to touch any other world.
//
// It builds a small stone platform at (0, 100, 0), gives the player:
//   slot 0: large can with a two-member chicken -> cow fusion (no saved layout)
//   slot 1: empty large can
//   slot 2: ordinary can with one plain cow
//   slot 3: empty ordinary can
//   slot 4: random can
//   slot 5: large can with a different two-member pig -> sheep fusion (no saved layout)
// and writes appearance-editor-setup.json next to the game directory for the
// client/assert scripts.

var SETUP_WORLD = 'Can Editor E2E';
var RF = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var RT = Java.loadClass('net.minecraft.world.entity.EntityType');
var RC = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var RA = Java.loadClass('cc.sighs.cannedmonster.can.CanActions');
var RR = Java.loadClass('cc.sighs.cannedmonster.CanRegistration');
var RV = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualViews');
var RD = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualDefaults');
var RES = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualEditorServer');
var RS = Java.loadClass('cc.sighs.cannedmonster.platform.CanStorage');
var RE = Java.loadClass('cc.sighs.cannedmonster.platform.EntityStorage');
var RGameType = Java.loadClass('net.minecraft.world.level.GameType');
var RStack = Java.loadClass('net.minecraft.world.item.ItemStack');
var RHand = Java.loadClass('net.minecraft.world.InteractionHand');
var RBlocks = Java.loadClass('net.minecraft.world.level.block.Blocks');
var RPos = Java.loadClass('net.minecraft.core.BlockPos');
var RAABB = Java.loadClass('net.minecraft.world.phys.AABB');
var RList = Java.loadClass('java.util.List');
var RLevelResource = Java.loadClass('net.minecraft.world.level.storage.LevelResource');
var RFiles = Java.loadClass('java.nio.file.Files');
var RPaths = Java.loadClass('java.nio.file.Paths');
var RVec3 = Java.loadClass('net.minecraft.world.phys.Vec3');
var RCarried = Java.loadClass('net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket');

var setup = AutoJS.server();
var player = AutoJS.player();
var level = AutoJS.level();
var checks = [];

if (!setup || !player || !level) throw new Error('No integrated server player in this world');
if (level.isClientSide()) throw new Error('Use side=server');
var worldName = String(setup.getWorldData().getLevelName());
if (worldName !== SETUP_WORLD) {
    throw new Error('Refusing to modify world "' + worldName + '"; expected the disposable world "' + SETUP_WORLD + '"');
}

function check(name, ok, detail) {
    checks.push({ name: name, pass: !!ok, detail: detail == null ? '' : String(detail) });
    if (!ok) throw new Error(name + (detail == null ? '' : ': ' + detail));
}

function stack(item) {
    return new RStack(item);
}

function clearArea() {
    var box = new RAABB(-12.0, 90.0, -12.0, 12.0, 112.0, 12.0);
    var entities = level.getEntities(player, box);
    for (var i = 0; i < entities.size(); i++) {
        var entity = entities.get(i);
        if (entity !== player) entity.discard();
    }
}

function buildPlatform() {
    for (var x = -4; x <= 4; x++) {
        for (var z = -4; z <= 4; z++) {
            level.setBlockAndUpdate(new RPos(x, 99, z), RBlocks.SMOOTH_STONE.defaultBlockState());
            for (var y = 100; y <= 102; y++) level.setBlockAndUpdate(new RPos(x, y, z), RBlocks.AIR.defaultBlockState());
        }
    }
}

function fusionCan(firstType, bodyType) {
    var first = firstType.create(level);
    var body = bodyType.create(level);
    check('create ' + firstType + ' member', first != null);
    check('create ' + bodyType + ' body', body != null);
    body.setPos(0.5, 100.0, 0.5);
    RF.attach(body, RList.of(first));
    check('fusion body accepted', level.addFreshEntity(body));
    var can = stack(RR.LARGE_MONSTER_CAN.get());
    check('large can captures fusion', RA.onMob(can, body, player));
    check('fusion can is filled', RC.filled(can));
    check('fusion can keeps both members', RC.count(can) === 2, RC.count(can));
    check('captured body left the world', body.isRemoved());
    return { can: can, uuid: RE.identity(RC.entity(can)).toString(), name: RC.name(can) };
}

function plainCan(type) {
    var mob = type.create(level);
    check('create plain ' + type, mob != null);
    mob.setPos(2.0, 100.0, 2.0);
    check('plain mob accepted', level.addFreshEntity(mob));
    var can = stack(RR.MONSTER_CAN.get());
    check('ordinary can captures mob', RA.onMob(can, mob, player));
    check('ordinary can is filled', RC.filled(can));
    check('ordinary can count is one', RC.count(can) === 1);
    return { can: can, uuid: RE.identity(RC.entity(can)).toString(), name: RC.name(can) };
}

clearArea();
buildPlatform();
if (!RF.facets(player).isEmpty()) RF.detach(player);
RES.stop();
RV.clear();
RD.clear();
var defaultsRoot = setup.getWorldPath(RLevelResource.ROOT).toAbsolutePath().normalize();
var defaultsFile = RPaths.get(String(defaultsRoot), 'data', 'cannedmonster_layouts.dat');
RFiles.deleteIfExists(defaultsFile);
RD.clear();
check('defaults file removed for clean scopes', !RFiles.exists(defaultsFile), defaultsFile);
player.setGameMode(RGameType.CREATIVE);
player.getInventory().clearContent();
player.removeAllEffects();
player.setHealth(20.0);
player.getFoodData().setFoodLevel(20);
player.setDeltaMovement(RVec3.ZERO);
if (!player.hasPermissions(2)) setup.getPlayerList().op(player.getGameProfile());
setup.getCommands().performPrefixedCommand(player.createCommandSourceStack(), 'tp @s 0.5 100.0 0.5 30 0');
setup.getCommands().performPrefixedCommand(player.createCommandSourceStack(), 'gamerule doMobSpawning false');
setup.getCommands().performPrefixedCommand(player.createCommandSourceStack(), 'gamerule doMobLoot false');
setup.getCommands().performPrefixedCommand(player.createCommandSourceStack(), 'gamerule randomTickSpeed 0');
level.setDayTime(6000);
level.setWeatherParameters(6000, 0, false, false);
clearArea();

var fusionA = fusionCan(RT.CHICKEN, RT.COW);
var plainC = plainCan(RT.COW);
var fusionF = fusionCan(RT.PIG, RT.SHEEP);

var emptyLarge = stack(RR.LARGE_MONSTER_CAN.get());
var emptySmall = stack(RR.MONSTER_CAN.get());
var randomCan = stack(RR.RANDOM_MONSTER_CAN.get());
check('empty large can is empty', !RC.filled(emptyLarge));
check('empty ordinary can is empty', !RC.filled(emptySmall));
check('random can stores no mob', !RC.filled(randomCan));

var inventory = player.getInventory();
inventory.setItem(0, fusionA.can);
inventory.setItem(1, emptyLarge);
inventory.setItem(2, plainC.can);
inventory.setItem(3, emptySmall);
inventory.setItem(4, randomCan);
inventory.setItem(5, fusionF.can);
inventory.selected = 0;
player.setItemInHand(RHand.MAIN_HAND, fusionA.can);
inventory.setChanged();
player.inventoryMenu.sendAllDataToRemote();
player.connection.send(new RCarried(0));

check('player is administrator for the server default test', player.hasPermissions(2));
check('slot 0 holds the editor can', RC.filled(player.getItemInHand(RHand.MAIN_HAND)) && RC.count(player.getItemInHand(RHand.MAIN_HAND)) === 2);
check('editor can starts without a saved layout', RS.compound(fusionA.can, RV.SAVE_KEY).isEmpty());
check('player is not fused before the player-default scope', RF.facets(player).isEmpty());

var info = {
    world: SETUP_WORLD,
    fusionACanSlot: 0,
    emptyLargeSlot: 1,
    plainCanSlot: 2,
    emptySmallSlot: 3,
    randomCanSlot: 4,
    fusionFCanSlot: 5,
    fusionAUuid: fusionA.uuid,
    fusionFUUid: fusionF.uuid,
    plainUUid: plainC.uuid,
    fusionAName: fusionA.name,
    fusionFName: fusionF.name,
    playerMembers: 0,
    platform: { x: 0.5, y: 100.0, z: 0.5 },
    checks: checks
};
AutoJS.writeJson('appearance-editor-setup.json', info);
JSON.stringify({ ok: true, world: worldName, checks: checks, setup: info });
