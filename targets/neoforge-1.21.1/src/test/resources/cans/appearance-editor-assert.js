// Server-side assertions for the fusion appearance editor / rotating can tooltip E2E.
//
// Original scope contract:
//   0 embedded current creature NBT, 1 can item default, 2 player UUID default without fusion,
//   3 server default. Resolution: embedded -> item -> player -> server -> unit fallback.
//
// Run after appearance-editor-client.js in the disposable "Can Editor E2E" world.

var SETUP_WORLD = 'Can Editor E2E';
var RF = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var RC = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var RS = Java.loadClass('cc.sighs.cannedmonster.platform.CanStorage');
var RV = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualViews');
var VL = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualLayout');
var VD = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualDefaults');
var VLR = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualLayoutResolver');
var RE = Java.loadClass('cc.sighs.cannedmonster.platform.EntityStorage');
var RHand = Java.loadClass('net.minecraft.world.InteractionHand');

var server = AutoJS.server();
var player = AutoJS.player();
var level = AutoJS.level();
var checks = [];

if (!server || !player || !level) throw new Error('No integrated server player in this world');
if (level.isClientSide()) throw new Error('Use side=server');
var worldName = String(server.getWorldData().getLevelName());
if (worldName !== SETUP_WORLD) throw new Error('Refusing to assert in world "' + worldName + '"; expected "' + SETUP_WORLD + '"');

function check(name, ok, detail) {
    checks.push({ name: name, pass: !!ok, detail: detail == null ? '' : String(detail) });
    if (!ok) console.error('E2E server check failed: ' + name + (detail == null ? '' : ': ' + detail));
}

var can = player.getItemInHand(RHand.MAIN_HAND);
check('player holds a large can', can != null && !can.isEmpty());
check('can is filled', RC.filled(can));
check('can keeps both members', RC.count(can) === 2, RC.count(can));
check('player is unfused (player default does not require fusion)', RF.facets(player).isEmpty(), RF.facets(player).size());

var client = null;
try { client = AutoJS.readJson('appearance-editor-client.json'); } catch (ignored) {}
check('client result exists', client != null);
var scopes = client == null ? null : client.scopes;
check('client scopes recorded', scopes != null && scopes.server != null && scopes.player != null && scopes.item != null && scopes.individual != null);

var item = RS.compound(can, RV.SAVE_KEY);
check('can has item default', !item.isEmpty());
var itemLayout = item.isEmpty() ? null : VL.load(item);
check('embedded layout exists', RC.valid(can) && RC.entity(can).contains(RV.SAVE_KEY));
var embeddedLayout = RC.valid(can) ? VLR.embedded(can) : null;
check('embedded layout exists 2', embeddedLayout != null);

if (scopes != null && itemLayout != null && embeddedLayout != null) {
    check('item default matches client', itemLayout.toString() === scopes.item, itemLayout);
    check('embedded layout matches client', embeddedLayout.toString() === scopes.individual, embeddedLayout);
    check('item default differs from embedded', !itemLayout.equals(embeddedLayout));
}

var serverDefault = VD.server(server, 2);
check('server default exists for 2 layers', serverDefault != null);
if (scopes != null && serverDefault != null) check('server default matches client', serverDefault.toString() === scopes.server, serverDefault);

check('player UUID default was recorded by the client save', scopes != null && scopes.player != null);
check('player is unfused for the player UUID default', RF.facets(player).isEmpty());

var resolved = VLR.resolve(player, can, 2);
check('resolver picks embedded individual', resolved.source() === VLR.Source.INDIVIDUAL, resolved.source());
if (embeddedLayout != null) check('resolver returns embedded layout', resolved.layout().equals(embeddedLayout), resolved.layout());

var setup = null;
try { setup = AutoJS.readJson('appearance-editor-setup.json'); } catch (ignored) {}
if (setup != null) {
    var nearby = level.getEntities(player, player.getBoundingBox().inflate(16.0));
    var duplicate = false;
    for (var ni = 0; ni < nearby.size(); ni++) {
        if (RE.identity(RE.save(nearby.get(ni))).toString() === setup.fusionAUuid) duplicate = true;
    }
    check('released F body was recaptured (no duplicate UUID)', !duplicate, setup.fusionAUuid);
}

if (client != null) {
    check('client finished', client.done === true, client.phase);
    check('client reported no error', client.error == null, client.error);
    check('client tooltip preview clock advanced', client.tooltipClock != null && client.tooltipClock.first != null && client.tooltipClock.second != null && client.tooltipClock.first !== client.tooltipClock.second, client.tooltipClock);
    var failed = [];
    for (var i = 0; i < client.checks.length; i++) if (!client.checks[i].pass) failed.push(client.checks[i].name);
    check('client checks all passed', failed.length === 0, failed.join(', '));
}

var ok = checks.every(function (c) { return c.pass; });
var result = { ok: ok, world: worldName, checks: checks };
AutoJS.writeJson('appearance-editor-assert.json', result);
JSON.stringify(result);
