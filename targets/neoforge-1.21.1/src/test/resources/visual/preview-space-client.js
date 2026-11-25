var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var CanContents = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var CanPreview = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.CanPreview');
var CanPreviewSelection = Java.loadClass('cc.sighs.cannedmonster.can.CanPreviewSelection');
var CanPreviewSource = Java.loadClass('cc.sighs.cannedmonster.can.CanPreviewSource');
var ClientAppearance = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientAppearance');
var EntityStorage = Java.loadClass('cc.sighs.cannedmonster.platform.EntityStorage');

var clientChecks = [];
function clientAssert(condition, message) {
    if (!condition) throw new Error(message);
    clientChecks.push(message);
}

var stack = mc.player.getMainHandItem();
var preview = CanContents.spacePreview(stack);
clientAssert(preview.size() === 3, 'spatial client snapshot has three resident slots');
clientAssert(CanPreviewSelection.ROTATE_MILLIS === 15000, 'client carousel uses a 15s period');
clientAssert(CanPreviewSource.spaceSelection(stack) !== null, 'spatial tooltip has a selected/total label');
clientAssert(CanPreviewSource.spaceSelection(stack).total() === 3, 'label total is the resident count');
for (var i = 0; i < preview.size(); i++) {
    var entry = preview.get(i);
    clientAssert(!entry.contains('Health') && !entry.contains('Pos') && !entry.contains('UUID'),
            'resident slot ' + i + ' has no volatile/private state');
    clientAssert(!entry.contains('Inventory') && !entry.contains('ArmorItems') && !entry.contains('HandItems'),
            'resident slot ' + i + ' has no inventory or equipment');
}
var index = CanPreviewSource.spatialIndex(stack);
var selected = preview.get(index);
var fusion = EntityStorage.compound(selected, 'cannedmonster:fusion');
var expectedMembers = fusion.isEmpty() ? 0 : EntityStorage.list(fusion, 'Members').size();
var host = CanPreview.host(stack);
clientAssert(host !== null, 'selected spatial resident has a preview host');
clientAssert(ClientAppearance.members(host).size() === expectedMembers,
        'selected resident is shown alone unless it is itself a fusion');
clientAssert(!host.isAddedToLevel(), 'spatial preview host never joins the level');

JSON.stringify({passed: clientChecks.length, index: index, members: expectedMembers});
