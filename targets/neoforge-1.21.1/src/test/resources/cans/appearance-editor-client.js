// Client-side real-input E2E for the fusion appearance editor and rotating can tooltips.
//
// Contract under test (original four scopes, unfused player):
//   scope 0: current creature NBT embedded inside the can contents
//   scope 1: can item default in the ItemStack root (survives emptying)
//   scope 2: player UUID persistent default, no fusion required
//   scope 3: server default
//   resolution: embedded -> item -> player -> server -> legacy/unit fallback
//
// Prerequisites:
//   * Dedicated game directory targets/neoforge-1.21.1/run-space-test
//   * Disposable world "Can Editor E2E" (appearance-editor-setup.js)
//   * Server setup already ran; player is unfused and holds the two-member large can
//   * AutoJS client side, no other screen open
//
// The script drives sneak + right-click through KeyMapping/Minecraft input, clicks the custom
// editor through ClientHooks/Screen.mouseClicked, checks legacy-vs-unit reset, the four save
// scopes and their precedence and release/recapture, then verifies the inventory tooltip preview
// component, preview host and advancing preview clock for filled/ordinary/empty/random cans.
//
// Results are stored in the global cmEditorE2E and written to appearance-editor-client.json.

var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var VisLayout = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualLayout');
var VisClient = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualEditorClient');
var VisViews = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.VisualViews');
var CanContents = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var CanStorage = Java.loadClass('cc.sighs.cannedmonster.platform.CanStorage');
var CanSource = Java.loadClass('cc.sighs.cannedmonster.can.CanPreviewSource');
var PreviewHosts = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.CanPreviewHosts');
var CanPreview = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.CanPreview');
var ClientAppearance = Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientAppearance');
var KeyMapping = Java.loadClass('net.minecraft.client.KeyMapping');
var ClientHooks = Java.loadClass('net.neoforged.neoforge.client.ClientHooks');
var RHand = Java.loadClass('net.minecraft.world.InteractionHand');
var RAnchor = Java.loadClass('net.minecraft.commands.arguments.EntityAnchorArgument$Anchor');
var RVec3 = Java.loadClass('net.minecraft.world.phys.Vec3');
var REsc = 256;

var E2E = {
    phase: 'boot', t: 0, checks: [], error: null, done: false,
    previousPause: null, legacyA: null, serverDefault: null, playerDefault: null,
    itemDefault: null, individual: null,
    tooltipDiff: -1, emptyDiff: -1, plainDiff: -1
};
var cmEditorE2E = E2E;

function check(name, ok, detail) {
    E2E.checks.push({ name: name, pass: !!ok, detail: detail == null ? '' : String(detail) });
    if (!ok) console.error('E2E check failed: ' + name + (detail == null ? '' : ': ' + detail));
    return !!ok;
}

function need(name, ok, detail) {
    check(name, ok, detail);
    if (!ok) throw new Error(name + (detail == null ? '' : ': ' + detail));
}

function approx(a, b) {
    return Math.abs(a - b) < 0.0001;
}

function enter(phase) {
    E2E.phase = phase;
    E2E.t = 0;
    E2E.openStable = 0;
}

function held() { return mc.player == null ? null : mc.player.getItemInHand(RHand.MAIN_HAND); }

function itemTag(stack) { return CanStorage.compound(stack, VisViews.SAVE_KEY); }

function itemDefaultOf(stack) {
    var tag = itemTag(stack);
    return tag.isEmpty() ? null : VisLayout.load(tag);
}

function embeddedOf(stack) { return CanSource.embedded(stack); }

function legacyOf(stack) {
    var host = CanPreview.host(stack);
    if (host == null) return null;
    return VisLayout.legacy(VisViews.ordered(host));
}

function report() {
    return {
        phase: E2E.phase, done: E2E.done, error: E2E.error,
        ok: E2E.error == null && E2E.checks.every(function (c) { return c.pass; }),
        checks: E2E.checks,
        diffs: { tooltip: E2E.tooltipDiff, empty: E2E.emptyDiff, plain: E2E.plainDiff },
        tooltipClock: { first: E2E.clockA, second: E2E.clockB },
        scopes: {
            server: E2E.serverDefault == null ? null : E2E.serverDefault.toString(),
            player: E2E.playerDefault == null ? null : E2E.playerDefault.toString(),
            item: E2E.itemDefault == null ? null : E2E.itemDefault.toString(),
            individual: E2E.individual == null ? null : E2E.individual.toString(),
            legacyA: E2E.legacyA == null ? null : E2E.legacyA.toString()
        }
    };
}

function finishRun(error) {
    if (E2E.done) return;
    E2E.error = error == null ? null : String(error);
    E2E.done = true;
    releaseKeys();
    if (E2E.previousPause != null) mc.options.pauseOnLostFocus = E2E.previousPause;
    try { AutoJS.writeJson('appearance-editor-client.json', report()); } catch (ignored) {}
    if (E2E.error == null) console.log('Appearance editor E2E: ' + JSON.stringify({ ok: report().ok, checks: E2E.checks.length }));
    else console.error('Appearance editor E2E failed: ' + E2E.error);
}

function releaseKeys() {
    if (mc.options == null) return;
    mc.options.keyUse.setDown(false);
    mc.options.keyShift.setDown(false);
    mc.options.keyInventory.setDown(false);
}

function clickGui(x, y) {
    var screen = mc.screen;
    if (screen == null) return false;
    var handled = ClientHooks.onScreenMouseClickedPre(screen, x, y, 0);
    if (!handled) {
        handled = screen.mouseClicked(x, y, 0);
        handled = ClientHooks.onScreenMouseClickedPost(screen, x, y, 0, handled);
    }
    return handled;
}

function pressEscape() {
    var screen = mc.screen;
    if (screen == null) return;
    var handled = ClientHooks.onScreenKeyPressedPre(screen, REsc, 0, 0);
    if (!handled) {
        handled = screen.keyPressed(REsc, 0, 0);
        ClientHooks.onScreenKeyPressedPost(screen, REsc, 0, 0);
    }
}

function use() {
    mc.options.keyUse.setDown(true);
    KeyMapping.click(mc.options.keyUse.getKey());
}

function hotbar(slot) {
    var mapping = mc.options.keyHotbarSlots[slot];
    mapping.setDown(true);
    KeyMapping.click(mapping.getKey());
    mc.options.keyHotbarSlots[slot].setDown(false);
}

function aimAtPlatform() {
    mc.player.lookAt(RAnchor.valueOf('EYES'), new RVec3(0.5, 99.0, 0.5));
    mc.options.keyShift.setDown(true);
}

function editorGeometry() {
    var screen = mc.screen;
    var w = screen.width;
    var h = screen.height;
    var panelWidth = Math.max(210, Math.min(240, Math.floor(w / 3)));
    var previewW = Math.max(40, w - panelWidth - 18);
    var panelX = 6 + previewW + 6;
    var panelY = 26;
    var panelH = Math.max(40, h - 32);
    var innerX = panelX + 4;
    var innerW = panelWidth - 8;
    var bottom = panelY + panelH - 4;
    var closeY = bottom - 14;
    var menuY = closeY - 17;
    var unitY = menuY - 18;
    var half = (innerW - 3) / 2;
    var actionW = (innerW - 6) / 3;
    return {
        screen: screen, w: w, h: h, innerX: innerX, innerW: innerW,
        tabY: panelY + 4 + 7,
        tab0X: innerX + half / 2,
        tab1X: innerX + half + 3 + (innerW - half - 3) / 2,
        listTop: panelY + 23,
        unitX: innerX + innerW / 2, unitY: unitY + 7,
        saveX: innerX + innerW / 2, saveY: menuY + 7,
        allX: innerX + actionW / 2, allY: closeY + 7,
        viewX: innerX + actionW + 3 + actionW / 2, viewY: closeY + 7,
        closeX: innerX + (actionW + 3) * 2 + (innerW - (actionW + 3) * 2) / 2, closeY: closeY + 7,
        confirmW: Math.min(220, w - 20), confirmH: 64
    };
}

function clickSaveButton() {
    clickGui(editorGeometry().saveX, editorGeometry().saveY);
}

function clickSaveOption(scope) {
    var g = editorGeometry();
    var w = Math.min(250, g.w - 20);
    var h = 20 + 4 * (14 + 4) + 14 + 12;
    var x = (g.w - w) / 2;
    var y = (g.h - h) / 2;
    clickGui(x + 8 + (w - 16) / 2, y + 20 + scope * 18 + 7);
}

function clickTransform(group, axis, plus) {
    var g = editorGeometry();
    var plane = String(VisClient.current().tab()) === 'PLANE';
    var rows = plane ? 2 : 3;
    var transformTop = g.unitY - 7 - 4 - (18 + rows * 15);
    var rowY = transformTop + 2 + 10 + 9 + group * 15;
    var labelWidth = 38;
    var cell = (g.innerW - labelWidth) / 3;
    var cellX = g.innerX + labelWidth + axis * cell;
    clickGui(plus ? cellX + cell - 6 : cellX + 6, rowY + 7);
}

function clickList(row) {
    var g = editorGeometry();
    clickGui(g.innerX + 30, g.listTop + row * 15 + 7);
}

function clickDiscardConfirm() {
    var g = editorGeometry();
    var x = (g.w - g.confirmW) / 2;
    var y = (g.h - g.confirmH) / 2;
    var half = (g.confirmW - 20) / 2;
    clickGui(x + 8 + half / 2, y + g.confirmH - 14 - 8 + 7);
}

function hotbarSlot(index) {
    var screen = mc.screen;
    if (screen == null || screen.menu == null) return null;
    var slots = screen.menu.slots;
    for (var i = 0; i < slots.size(); i++) {
        var slot = slots.get(i);
        if (slot.container === mc.player.getInventory() && slot.getContainerSlot() === index) return slot;
    }
    var fallback = slots.size() > 36 + index ? slots.get(36 + index) : null;
    return fallback != null && fallback.getContainerSlot() === index ? fallback : null;
}

function openEditorAtPlatform() {
    aimAtPlatform();
}

function openPhase(next) {
    if (E2E.t === 1) {
        need('main hand selected', mc.player.getInventory().selected === 0, mc.player.getInventory().selected);
        need('main hand can filled', CanContents.filled(held()) && CanContents.count(held()) === 2);
    }
    if (E2E.t === 4) openEditorAtPlatform();
    if (E2E.t === 20) use();
    if (E2E.t === 28) {
        mc.options.keyUse.setDown(false);
        mc.options.keyShift.setDown(false);
    }
    if (E2E.t > 30) {
        if (mc.screen != null && VisClient.current() != null) {
            E2E.openStable++;
            if (E2E.openStable >= 3) enter(next);
        } else E2E.openStable = 0;
    }
    if (E2E.t > 100) throw new Error('Could not open the editor for ' + next);
}

function isEditorScreen(screen) {
    return screen != null && String(screen).indexOf('VisualEditorScreen') >= 0;
}

function closeEditorClean() {
    if (mc.screen != null) pressEscape();
}

// Open and close the player inventory once to receive the server's full inventory contents.
// A player action is the supported way to refresh the held can after an editor save.
function refreshInventory(next) {
    if (E2E.t === 1) {
        mc.options.keyInventory.setDown(true);
        KeyMapping.click(mc.options.keyInventory.getKey());
    }
    if (E2E.t === 4) mc.options.keyInventory.setDown(false);
    if (E2E.t === 10) {
        need('refresh inventory opened', mc.screen != null && !isEditorScreen(mc.screen) && mc.screen.menu != null);
        pressEscape();
    }
    if (E2E.t === 14 && mc.screen != null) pressEscape();
    if (E2E.t === 18) {
        need('refresh inventory closed', mc.screen == null);
        enter(next);
    }
    if (E2E.t > 80) throw new Error('Could not refresh the inventory for ' + next);
}

// Generic scope save step: check the current resolution, add a distinguishing X offset,
// open the save menu and pick the requested scope.
function beginScopeSave(expected, storage, scope) {
    if (E2E.t === 1) {
        clickGui(editorGeometry().tab0X, editorGeometry().tabY);
        clickList(0);
    }
    if (E2E.t === 2) {
        var actual = VisClient.current().layout();
        if (expected != null) check('scope precedence before save ' + scope, actual.equals(expected), 'actual=' + actual + ' expected=' + expected);
        clickTransform(0, 0, true);
        var edited = VisClient.current().layout();
        E2E[storage] = edited;
        clickSaveButton();
    }
    if (E2E.t === 3) {
        need('save menu opened for scope ' + scope, VisClient.current().saveMenu());
        clickSaveOption(scope);
    }
    if (E2E.t > 3 && !VisClient.current().saving()) {
        if (VisClient.current().messageError()) throw new Error('Scope ' + scope + ' save failed: ' + VisClient.current().message().getString());
        need('scope ' + scope + ' keeps the editor open', mc.screen != null);
        return true;
    }
    if (E2E.t > 140) throw new Error('Scope ' + scope + ' save acknowledgement timed out');
    return false;
}

var integratedServer = mc.getSingleplayerServer();
if (integratedServer == null) throw new Error('Appearance editor E2E requires an integrated single-player world');
var clientWorldName = String(integratedServer.getWorldData().getLevelName());
if (clientWorldName !== 'Can Editor E2E') {
    throw new Error('Refusing to run in world "' + clientWorldName + '"; expected "Can Editor E2E"');
}
if (mc.player == null) throw new Error('Appearance editor E2E needs a loaded client world');

ClientEvents.tick(function () {
    if (E2E.done) return;
    E2E.t++;
    try {
        var screen = mc.screen;
        var can = held();
        switch (E2E.phase) {
            case 'boot':
                if (!mc.player || !mc.level) return;
                E2E.previousPause = mc.options.pauseOnLostFocus;
                mc.options.pauseOnLostFocus = false;
                AutoJS.background(true);
                if (screen != null) mc.setScreen(null);
                var stale = VisClient.current();
                if (stale != null) VisClient.closed(stale.session());
                VisViews.clearClient();
                PreviewHosts.clear();
                releaseKeys();
                enter('wait_can');
                break;
            case 'wait_can':
                if (can != null && CanContents.filled(can) && CanContents.count(can) === 2) {
                    need('no screen before open', mc.screen == null);
                    enter('open_a');
                } else if (E2E.t > 200) {
                    throw new Error('No filled two-member large can in the main hand; run appearance-editor-setup.js first');
                }
                break;
            case 'open_a':
                if (E2E.t === 1) openEditorAtPlatform();
                if (E2E.t === 20) use();
                if (E2E.t === 28) {
                    mc.options.keyUse.setDown(false);
                    mc.options.keyShift.setDown(false);
                }
                if (E2E.t > 30) {
                    if (mc.screen != null && VisClient.current() != null) {
                        E2E.openStable++;
                        if (E2E.openStable >= 3) enter('legacy_a');
                    } else E2E.openStable = 0;
                }
                if (E2E.t > 100) throw new Error('Sneak + right-click did not open the editor on can A');
                break;
            case 'legacy_a':
                if ((mc.screen == null || VisClient.current() == null) && E2E.t < 30) break;
                need('editor screen open', mc.screen != null);
                need('editor state installed', VisClient.current() != null);
                need('can A still filled', CanContents.filled(held()) && CanContents.count(held()) === 2);
                E2E.legacyA = legacyOf(held());
                need('actual legacy layout available', E2E.legacyA != null);
                check('initial editor layout equals legacy fit', VisClient.current().layout().equals(E2E.legacyA),
                        'actual=' + VisClient.current().layout() + ' expected=' + E2E.legacyA);
                check('initial editor is not dirty', !VisClient.current().dirty());
                need('opening writes no item default', itemTag(held()).isEmpty());
                need('opening writes no embedded layout', embeddedOf(held()) == null);
                enter('edit_reset');
                break;
            case 'edit_reset':
                if (E2E.t === 1) {
                    clickGui(editorGeometry().tab0X, editorGeometry().tabY);
                    clickList(0);
                    for (var i = 0; i < 5; i++) clickTransform(2, 1, true);
                    for (var j = 0; j < 10; j++) clickTransform(0, 1, true);
                    for (var k = 0; k < 3; k++) clickTransform(1, 0, true);
                }
                if (E2E.t === 2) {
                    need('editing marks dirty', VisClient.current().dirty());
                    clickGui(editorGeometry().unitX, editorGeometry().unitY);
                }
                if (E2E.t === 3) {
                    var layer0 = VisClient.current().layout().layer(0);
                    need('reset selected unit scale', approx(layer0.scale().x(), 1) && approx(layer0.scale().y(), 1) && approx(layer0.scale().z(), 1), layer0.scale());
                    need('reset selected zero rotation', approx(layer0.rotation().x(), 0) && approx(layer0.rotation().y(), 0) && approx(layer0.rotation().z(), 0));
                    need('reset selected zero offset', approx(layer0.offset().x(), 0) && approx(layer0.offset().y(), 0) && approx(layer0.offset().z(), 0));
                    clickGui(editorGeometry().tab1X, editorGeometry().tabY);
                    clickList(0);
                }
                if (E2E.t === 4) {
                    for (var p = 0; p < 10; p++) clickTransform(0, 0, true);
                    for (var q = 0; q < 3; q++) clickTransform(1, 0, true);
                    clickGui(editorGeometry().unitX, editorGeometry().unitY);
                }
                if (E2E.t === 5) {
                    var plane = VisClient.current().layout().plane(0);
                    need('reset plane zero origin', approx(plane.origin().x(), 0) && approx(plane.origin().z(), 0) && approx(plane.origin().y(), plane.position()));
                    need('reset plane zero rotation', approx(plane.rotation().x(), 0) && approx(plane.rotation().y(), 0) && approx(plane.rotation().z(), 0));
                    clickGui(editorGeometry().allX, editorGeometry().allY);
                }
                if (E2E.t === 6) {
                    var reset = VisClient.current().layout();
                    need('reset all equals unit legacy count', reset.equals(VisLayout.defaults(2)), reset);
                    for (var li = 0; li < reset.layerCount(); li++) {
                        var layer = reset.layer(li);
                        need('reset all layer ' + li + ' unit', approx(layer.scale().x(), 1) && approx(layer.scale().y(), 1) && approx(layer.scale().z(), 1), layer.scale());
                    }
                    clickGui(editorGeometry().tab0X, editorGeometry().tabY);
                    clickList(0);
                }
                if (E2E.t === 7) {
                    clickTransform(2, 1, true);
                    need('dirty close test is dirty', VisClient.current().dirty());
                    pressEscape();
                }
                if (E2E.t === 8) {
                    need('escape asks before discard', VisClient.current().confirming());
                    need('discard prompt open', mc.screen != null);
                }
                if (E2E.t === 9) clickDiscardConfirm();
                if (E2E.t === 10) {
                    need('discard closes editor', mc.screen == null && VisClient.current() == null);
                    need('discard writes no item default', itemTag(held()).isEmpty());
                    need('discard writes no embedded layout', embeddedOf(held()) == null);
                    enter('open_f_server');
                }
                break;
            case 'open_f_server': openPhase('save_server'); break;
            case 'save_server':
                if (E2E.t === 2) {
                    check('fresh F initial layout equals legacy fit', VisClient.current().layout().equals(legacyOf(held())),
                            'actual=' + VisClient.current().layout() + ' expected=' + legacyOf(held()));
                }
                if (beginScopeSave(null, 'serverDefault', 3)) {
                    closeEditorClean();
                    enter('refresh_player');
                }
                break;
            case 'refresh_player': refreshInventory('open_f_player'); break;
            case 'open_f_player': openPhase('save_player'); break;
            case 'save_player':
                if (beginScopeSave(E2E.serverDefault, 'playerDefault', 2)) {
                    closeEditorClean();
                    enter('refresh_item');
                }
                break;
            case 'refresh_item': refreshInventory('open_f_item'); break;
            case 'open_f_item': openPhase('save_item'); break;
            case 'save_item':
                if (beginScopeSave(E2E.playerDefault, 'itemDefault', 1)) {
                    closeEditorClean();
                    enter('refresh_individual');
                }
                break;
            case 'refresh_individual': refreshInventory('open_f_individual'); break;
            case 'open_f_individual': openPhase('save_individual'); break;
            case 'save_individual':
                if (beginScopeSave(E2E.itemDefault, 'individual', 0)) {
                    closeEditorClean();
                    enter('refresh_final');
                }
                break;
            case 'refresh_final': refreshInventory('open_f_final'); break;
            case 'open_f_final': openPhase('final_precedence'); break;
            case 'final_precedence':
                check('embedded layout wins precedence', VisClient.current().layout().equals(E2E.individual),
                        'actual=' + VisClient.current().layout() + ' embedded=' + E2E.individual);
                closeEditorClean();
                enter('refresh_release');
                break;
            case 'refresh_release': refreshInventory('release_f'); break;
            case 'release_f':
                if (E2E.t === 1) {
                    hotbar(0);
                    mc.options.keyShift.setDown(false);
                }
                if (E2E.t === 10) {
                    need('main hand selected for release', mc.player.getInventory().selected === 0);
                    need('F filled before release', CanContents.filled(held()) && CanContents.count(held()) === 2);
                    mc.player.lookAt(RAnchor.valueOf('EYES'), new RVec3(0.5, 99.0, 0.5));
                }
                if (E2E.t === 14) use();
                if (E2E.t === 18) mc.options.keyUse.setDown(false);
                if (E2E.t === 20) enter('sync_release');
                break;
            case 'sync_release':
                if (E2E.t === 1) {
                    mc.options.keyInventory.setDown(true);
                    KeyMapping.click(mc.options.keyInventory.getKey());
                }
                if (E2E.t === 4) mc.options.keyInventory.setDown(false);
                if (E2E.t === 10) pressEscape();
                if (E2E.t === 14) {
                    need('release inventory closed', mc.screen == null);
                    need('release empties the can', !CanContents.filled(held()));
                    enter('recapture_f');
                }
                if (E2E.t > 80) throw new Error('Release did not empty F');
                break;
            case 'recapture_f':
                if (E2E.t === 1) {
                    var near = mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(6.0));
                    E2E.released = null;
                    for (var e = 0; e < near.size(); e++) {
                        var entity = near.get(e);
                        if (entity !== mc.player && ClientAppearance.members(entity).size() === 1) { E2E.released = entity; break; }
                    }
                    need('released body visible', E2E.released != null);
                    mc.player.lookAt(RAnchor.valueOf('EYES'), E2E.released.getEyePosition(1.0));
                }
                if (E2E.t === 6) use();
                if (E2E.t === 10) mc.options.keyUse.setDown(false);
                if (E2E.t === 12) enter('sync_recapture');
                break;
            case 'sync_recapture':
                if (E2E.t === 1) {
                    mc.options.keyInventory.setDown(true);
                    KeyMapping.click(mc.options.keyInventory.getKey());
                }
                if (E2E.t === 4) mc.options.keyInventory.setDown(false);
                if (E2E.t === 10) pressEscape();
                if (E2E.t === 14) {
                    need('recapture inventory closed', mc.screen == null);
                    need('recapture refills F', CanContents.filled(held()) && CanContents.count(held()) === 2);
                    need('recapture keeps embedded layout', E2E.individual.equals(embeddedOf(held())), embeddedOf(held()));
                    need('recapture keeps item default', E2E.itemDefault.equals(itemDefaultOf(held())), itemDefaultOf(held()));
                    E2E.released = null;
                    enter('tooltip');
                }
                if (E2E.t > 120) throw new Error('Recapture did not fill F');
                break;
            case 'tooltip':
                if (E2E.t === 1 || (E2E.t === 30 && mc.screen == null)) {
                    mc.options.keyInventory.setDown(true);
                    KeyMapping.click(mc.options.keyInventory.getKey());
                }
                if (E2E.t === 4 || E2E.t === 33) mc.options.keyInventory.setDown(false);
                if (E2E.t === 20) need('inventory opened', mc.screen != null && mc.screen.menu != null);
                if (E2E.t === 40) {
                    var slotF = hotbarSlot(0);
                    need('F slot present', slotF != null);
                    var stackF = slotF.getItem();
                    need('F can is filled', CanContents.filled(stackF) && CanContents.count(stackF) === 2);
                    need('embedded layout survives recapture', E2E.individual.equals(embeddedOf(stackF)));
                    need('item default survives recapture', E2E.itemDefault.equals(itemDefaultOf(stackF)));
                    need('tooltip image present', stackF.getTooltipImage().isPresent());
                    E2E.hostA = PreviewHosts.host(stackF);
                    need('tooltip preview host', E2E.hostA != null);
                    E2E.clockA = E2E.hostA.tickCount;
                }
                if (E2E.t === 80) {
                    var slotF2 = hotbarSlot(0);
                    E2E.hostB = PreviewHosts.host(slotF2.getItem());
                    need('tooltip preview host still present', E2E.hostB != null);
                    E2E.clockB = E2E.hostB.tickCount;
                    need('tooltip preview clock advances', E2E.clockB !== E2E.clockA, E2E.clockA + ' -> ' + E2E.clockB);
                    E2E.hostA = null; E2E.hostB = null;
                    enter('tooltip_empty');
                } else if (E2E.t > 140) throw new Error('Tooltip preview host not available');
                break;
            case 'tooltip_empty':
                if (E2E.t === 20) {
                    var emptySlot = hotbarSlot(3);
                    need('empty slot present', emptySlot != null);
                    need('empty can has no host', PreviewHosts.host(emptySlot.getItem()) == null);
                    enter('tooltip_plain');
                }
                break;
            case 'tooltip_plain':
                if (E2E.t === 20) {
                    var plainSlot = hotbarSlot(2);
                    need('ordinary slot present', plainSlot != null);
                    need('ordinary can host', PreviewHosts.host(plainSlot.getItem()) != null);
                    enter('tooltip_random');
                }
                break;
            case 'tooltip_random':
                if (E2E.t === 20) {
                    var randomSlot = hotbarSlot(4);
                    need('random can has no contents', randomSlot != null && !CanContents.filled(randomSlot.getItem()));
                    need('random can has no host', PreviewHosts.host(randomSlot.getItem()) == null);
                    pressEscape();
                }
                if (E2E.t === 25) {
                    need('inventory closed', mc.screen == null);
                    enter('done');
                }
                break;
            case 'done':
                finishRun(null);
                break;
        }
    } catch (error) {
        finishRun(error);
    }
});

JSON.stringify({ started: true, phase: E2E.phase });
