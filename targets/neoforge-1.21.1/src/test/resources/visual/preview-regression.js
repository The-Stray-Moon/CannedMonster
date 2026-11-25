var ItemStack = Java.loadClass('net.minecraft.world.item.ItemStack');
var Items = Java.loadClass('net.minecraft.world.item.Items');
var CompoundTag = Java.loadClass('net.minecraft.nbt.CompoundTag');
var ListTag = Java.loadClass('net.minecraft.nbt.ListTag');
var UUID = Java.loadClass('java.util.UUID');
var CanContents = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var CanPreview = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.CanPreview');
var CanPreviewSource = Java.loadClass('cc.sighs.cannedmonster.can.CanPreviewSource');
var CanPreviewSelection = Java.loadClass('cc.sighs.cannedmonster.can.CanPreviewSelection');
var CanStorage = Java.loadClass('cc.sighs.cannedmonster.platform.CanStorage');
var VisualLayout = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualLayout');
var VisualViews = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualViews');
var ClientAppearance = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientAppearance');

var previewChecks = [];
function previewAssert(condition, message) {
    if (!condition) throw new Error(message);
    previewChecks.push(message);
}
function entity(id, health) {
    var tag = new CompoundTag();
    tag.putString('id', id);
    tag.putFloat('Health', health);
    return tag;
}

var empty = new ItemStack(Items.STONE);
previewAssert(!CanPreviewSource.has(empty), 'empty stack has no preview');
previewAssert(CanPreview.host(empty) === null, 'empty stack host is null');

var single = new ItemStack(Items.STONE);
CanContents.store(single, entity('minecraft:cow', 10), 'Cow', 1);
previewAssert(CanPreviewSource.has(single), 'saved entity becomes a preview source');
var cow = CanPreview.host(single);
previewAssert(cow !== null, 'cow host created');
previewAssert(!cow.isAddedToLevel(), 'preview host never joins the level');
previewAssert(CanPreview.host(single) === cow, 'host is cached between frames');
previewAssert(VisualViews.stored(cow) !== null, 'preview host carries a layout');
previewAssert(VisualViews.stored(cow).layerCount() === 1, 'single body layout has one layer');

var fused = new ItemStack(Items.STONE);
var fusion = new CompoundTag();
fusion.putInt('Version', 2);
var savedMembers = new ListTag();
savedMembers.add(entity('minecraft:pig', 8));
fusion.put('Members', savedMembers);
var body = entity('minecraft:cow', 10);
body.put('cannedmonster:fusion', fusion);
CanContents.store(fused, body, 'Fusion', 2);
var fusedHost = CanPreview.host(fused);
previewAssert(fusedHost !== null, 'fusion host created');
previewAssert(ClientAppearance.members(fusedHost).size() === 1, 'fusion member is hydrated');
previewAssert(!ClientAppearance.members(fusedHost).get(0).isAddedToLevel(), 'fusion preview member never joins the level');
previewAssert(VisualViews.stored(fusedHost).layerCount() === 2, 'fusion default layout has two layers');
var custom = VisualLayout.defaults(2);
CanStorage.put(fused, VisualViews.SAVE_KEY, custom.save());
previewAssert(CanPreviewSource.layout(fused).layerCount() === 2, 'root layout is read');
var relaid = CanPreview.host(fused);
previewAssert(relaid !== fusedHost, 'layout change rebuilds the host');
previewAssert(VisualViews.stored(relaid).layerCount() === 2, 'root layout is installed on the host');

var spatial = new ItemStack(Items.STONE);
CanContents.storeSpace(spatial, UUID.randomUUID(), 0);
previewAssert(!CanPreviewSource.has(spatial), 'spatial can without residents falls back to text');
previewAssert(CanPreview.host(spatial) === null, 'spatial can without residents has no host');
previewAssert(CanPreviewSelection.ROTATE_MILLIS === 15000, 'preview rotation uses a 15s monotonic period');
previewAssert(CanPreviewSelection.index(2, 0) === 0, 'preview carousel starts at the newest slot');
previewAssert(CanPreviewSelection.index(2, 15000) === 1, 'preview carousel advances after 15s');

JSON.stringify({passed: previewChecks.length, host: String(cow.getType())});
