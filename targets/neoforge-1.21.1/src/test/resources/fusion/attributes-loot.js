var F = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var Attr = Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes');
var Tag = Java.loadClass('net.minecraft.nbt.CompoundTag');
var Lists = Java.loadClass('java.util.List');
var Slots = Java.loadClass('net.minecraft.world.entity.EquipmentSlot');
var Color = Java.loadClass('net.minecraft.world.item.DyeColor');
var world = AutoJS.level();
if (String(AutoJS.server().getWorldData().getLevelName()) !== 'Fusion attributes loot verification') throw new Error('Use the dedicated test world');
for (var old of world.getAllEntities()) {
    if (!old.isRemoved() && (old.getTags().contains('attribute-persistence') || old.getTags().contains('attribute-persistence-verified')
            || String(AutoJS.id(old.getType())) === 'minecraft:item' && Math.abs(old.getX()) < 15 && Math.abs(old.getZ()) < 15)) old.discard();
}
var checks = [];
function check(ok, text) { if (!ok) throw new Error(text); checks.push(text); }
function near(actual, expected, text) { check(Math.abs(actual - expected) < 0.00001, text + ': ' + actual); }
function mob(type, health) {
    var m = type.create(world);
    m.getAttribute(Attr.MAX_HEALTH).setBaseValue(health);
    m.setHealth(health);
    m.setPos(0, 100, 0);
    m.setNoAi(true);
    return m;
}
AutoJS.command('gamerule doMobSpawning false');
AutoJS.command('gamerule doMobLoot true');
AutoJS.command('fill -10 99 -10 10 99 10 minecraft:stone');
AutoJS.player().teleportTo(0,100,5);
var body = mob(ET.COW, 80);
var sheep = mob(ET.SHEEP, 10);
F.attach(body, Lists.of(sheep));
near(body.getMaxHealth(), 45, 'two-member average');
near(body.getHealth(), 45, 'health clamped');
var pig = mob(ET.PIG, 30);
F.prepend(body, pig);
near(body.getMaxHealth(), 40, 'three-member unweighted average');
near(sheep.getMaxHealth(), 40, 'members read shared average');
var saved = body.saveWithoutId(new Tag());
check(saved.getCompound('cannedmonster:fusion').getInt('Version') === 2, 'version 2 origins saved');
var restored = ET.COW.create(world);
restored.load(saved);
near(restored.getMaxHealth(), 40, 'save/load average stable');
var extra = mob(ET.PIG, 100);
F.prepend(restored, extra);
near(restored.getMaxHealth(), 55, 'prepend after load uses original four members');
var group = mob(ET.PIG, 50);
F.attach(group, Lists.of(mob(ET.SHEEP, 70)));
F.prepend(restored, group);
near(restored.getMaxHealth(), 340 / 6, 'flattening two fused groups preserves weights');
restored.addTag('attribute-persistence');
world.addFreshEntity(restored);
F.detach(body);
var attackHost = mob(ET.COW, 20);
var attacker = mob(ET.ZOMBIE, 20);
attacker.getAttribute(Attr.ATTACK_DAMAGE).setBaseValue(9);
F.attach(attackHost, Lists.of(attacker));
near(attackHost.getAttributeValue(Attr.ATTACK_DAMAGE), 9, 'missing attribute not averaged as zero');
F.detach(attackHost);
var lootRows = [];
var lootGeneration = (typeof lootGeneration === 'undefined' ? 0 : lootGeneration) + 1;
(function(generation) {
    NativeEvents.onEvent('net.neoforged.neoforge.event.entity.living.LivingDropsEvent', function(event) {
        if (generation !== lootGeneration) return;
        for (var row of lootRows) {
            if (event.getEntity() !== row.body) continue;
            row.events++;
            for (var drop of event.getDrops()) {
                var stack = drop.getItem();
                var id = String(AutoJS.id(stack.getItem()));
                row.items[id] = (row.items[id] || 0) + stack.getCount();
            }
            if (row.cancel) event.setCanceled(true);
        }
    });
})(lootGeneration);
function lootCase(cancel, lootRule, duplicate) {
    var b = mob(ET.COW, 20);
    var a = mob(ET.SHEEP, 20);
    var c = mob(ET.SHEEP, 20);
    a.setColor(Color.RED);
    c.setColor(duplicate ? Color.RED : Color.BLUE);
    F.attach(b, Lists.of(a, c));
    b.setItemSlot(Slots.MAINHAND, AutoJS.stack('minecraft:diamond', 12));
    b.setDropChance(Slots.MAINHAND, 2);
    b.setPos(lootRows.length * 3, 100, 0);
    var row = {body:b, events:0, items:{}, cancel:cancel, lootRule:lootRule, duplicate:duplicate};
    lootRows.push(row);
    world.addFreshEntity(b);
    AutoJS.command('gamerule doMobLoot ' + lootRule);
    AutoJS.command('kill ' + String(b.uuid));
    check(row.events === 1, 'one public drop event');
    if (lootRule) {
        check(row.items['minecraft:red_wool'] === (duplicate ? 2 : 1), 'all red sheep tables');
        if (!duplicate) check(row.items['minecraft:blue_wool'] === 1, 'blue sheep table');
        check(row.items['minecraft:beef'] >= 1, 'body cow table');
        check(row.items['minecraft:diamond'] === 12, 'shared equipment dropped once');
    } else check(Object.keys(row.items).length === 0, 'doMobLoot respected');
    return row;
}
lootCase(false, true, false);
lootCase(true, true, false);
lootCase(false, true, true);
lootCase(false, false, false);
AutoJS.command('gamerule doMobLoot true');
JSON.stringify({passed:checks.length, attributes:restored.getMaxHealth(), loot:lootRows.map(function(r){return {events:r.events, items:r.items, canceled:r.cancel};})});
