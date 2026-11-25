var ItemStack = Java.loadClass('net.minecraft.world.item.ItemStack');
var BuiltInRegistries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation');
var EntityType = Java.loadClass('net.minecraft.world.entity.EntityType');
var InteractionHand = Java.loadClass('net.minecraft.world.InteractionHand');
var ArrayList = Java.loadClass('java.util.ArrayList');
var CanContents = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var CanPreviewSelection = Java.loadClass('cc.sighs.cannedmonster.can.CanPreviewSelection');
var EntityStorage = Java.loadClass('cc.sighs.cannedmonster.platform.EntityStorage');
var FusionRuntime = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var PlayerAccess = Java.loadClass('cc.sighs.cannedmonster.platform.PlayerAccess');
var SpaceManager = Java.loadClass('cc.sighs.cannedmonster.space.SpaceManager');

var spaceChecks = [];
function spaceAssert(condition, message) {
    if (!condition) throw new Error(message);
    spaceChecks.push(message);
}
function spawn(type, offset) {
    var mob = type.create(level);
    mob.setPos(player.getX() + offset, player.getY(), player.getZ());
    if (!level.addFreshEntity(mob)) throw new Error('spawn rejected');
    return mob;
}

var player = AutoJS.player();
var level = player.level;
SpaceManager.abandon(player);
var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse('cannedmonster:spatial_monster_can')));
player.setItemInHand(InteractionHand.MAIN_HAND, stack);
spaceAssert(SpaceManager.capture(player, spawn(EntityType.COW, 1.5), stack), 'cow admitted into the space');
spaceAssert(SpaceManager.capture(player, spawn(EntityType.PIG, 2.5), stack), 'pig admitted into the space');

var fusionBody = EntityType.COW.create(level);
fusionBody.setPos(player.getX() + 3.5, player.getY(), player.getZ());
var fusionMembers = new ArrayList();
fusionMembers.add(EntityType.PIG.create(level));
FusionRuntime.attach(fusionBody, fusionMembers);
if (!level.addFreshEntity(fusionBody)) throw new Error('fusion spawn rejected');
spaceAssert(SpaceManager.capture(player, fusionBody, stack), 'fusion resident admitted into the space');
PlayerAccess.syncInventory(player);

spaceAssert(CanContents.spaceId(stack) !== null, 'space can references the live space');
spaceAssert(CanContents.spaceCount(stack) === 3, 'resident count is current');
var preview = CanContents.spacePreview(stack);
spaceAssert(preview.size() === 3, 'preview snapshot keeps one slot per bounded resident');
spaceAssert(EntityStorage.string(preview.get(0), 'id') === 'minecraft:cow', 'newest resident is first');
spaceAssert(EntityStorage.string(preview.get(1), 'id') === 'minecraft:pig', 'next newest resident is second');
spaceAssert(EntityStorage.string(preview.get(2), 'id') === 'minecraft:cow', 'oldest resident is third');
var newest = preview.get(0);
spaceAssert(!newest.contains('Health') && !newest.contains('Pos') && !newest.contains('UUID'),
        'preview omits health, positions and UUID');
spaceAssert(!newest.contains('Inventory') && !newest.contains('ArmorItems') && !newest.contains('HandItems'),
        'preview omits private inventories and equipment');
var compactFusion = EntityStorage.compound(newest, 'cannedmonster:fusion');
spaceAssert(!compactFusion.isEmpty(), 'fusion resident keeps its own composition');
spaceAssert(!compactFusion.contains('AttributeOrigins'), 'fusion preview omits attribute origins');
var keptMembers = EntityStorage.list(compactFusion, 'Members');
spaceAssert(keptMembers.size() === 1, 'fusion preview keeps its own member count');
spaceAssert(EntityStorage.string(keptMembers.get(0), 'id') === 'minecraft:pig', 'fusion preview keeps its own member');
spaceAssert(!keptMembers.get(0).contains('Health') && !keptMembers.get(0).contains('Inventory'),
        'fusion member preview is sanitized');
spaceAssert(CanContents.spacePreviewRevision(stack) > 0, 'snapshot revision advances with capture');
spaceAssert(CanPreviewSelection.index(3, 0) === 0, 'carousel starts at the newest resident');
spaceAssert(CanPreviewSelection.index(3, CanPreviewSelection.ROTATE_MILLIS) === 1, 'carousel advances after 15s');
spaceAssert(CanPreviewSelection.ROTATE_MILLIS === 15000, 'carousel uses a 15s period');

JSON.stringify({passed: spaceChecks.length, preview: preview.size(), members: keptMembers.size()});
