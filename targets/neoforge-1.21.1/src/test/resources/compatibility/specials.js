var Hand = Java.loadClass('net.minecraft.world.InteractionHand');
var Slot = Java.loadClass('net.minecraft.world.entity.EquipmentSlot');
var Empty = Java.loadClass('net.minecraft.world.item.ItemStack');
var Color = Java.loadClass('net.minecraft.world.item.DyeColor');
var Effects = Java.loadClass('net.minecraft.world.effect.MobEffects');
var specialRows = [];
var specialClock = 0;
function makeSpecial(species, direction, index, water) {
    var point = cage(index, water);
    var host = createType(direction === 1 ? 'cow' : species);
    host.setPos(point.x + 0.5, point.y, point.z + 0.5);
    var member = host;
    if (direction !== 0) {
        var facet = createType(direction === 1 ? species : 'cow');
        F.attach(host, JList.of(facet));
        if (direction === 1) member = facet;
    } else host.setPersistenceRequired();
    world.addFreshEntity(host);
    return {host: host, member: member, mode: direction === 0 ? 'native-control' : direction === 1 ? species + '->cow' : 'cow->' + species,
        species: species, direction: direction, startTick: host.tickCount, point: point, errors: []};
}
AutoJS.command('gamemode survival @a');
for (var direction = 0; direction < 3; direction++) {
    var sheepRow = makeSpecial('sheep', direction, 54 + direction, false);
    sheepRow.member.setColor(Color.WHITE);
    player.setItemInHand(Hand.MAIN_HAND, AutoJS.stack('minecraft:red_dye', 1));
    sheepRow.dyeResult = String(player.interactOn(sheepRow.host, Hand.MAIN_HAND));
    sheepRow.colorAfterDye = String(sheepRow.member.getColor());
    player.setItemInHand(Hand.MAIN_HAND, AutoJS.stack('minecraft:shears', 1));
    sheepRow.shearResult = String(player.interactOn(sheepRow.host, Hand.MAIN_HAND));
    sheepRow.sheared = sheepRow.member.isSheared();
    specialRows.push(sheepRow);
    var allayRow = makeSpecial('allay', direction, 57 + direction, false);
    allayRow.host.setItemSlot(Slot.MAINHAND, Empty.EMPTY);
    player.setItemInHand(Hand.MAIN_HAND, AutoJS.stack('minecraft:diamond', 1));
    allayRow.giveResult = String(player.interactOn(allayRow.host, Hand.MAIN_HAND));
    allayRow.held = String(AutoJS.id(allayRow.host.getMainHandItem()));
    specialRows.push(allayRow);
    var pufferRow = makeSpecial('pufferfish', direction, 60 + direction, true);
    pufferRow.member.setPuffState(2);
    specialRows.push(pufferRow);
    var creeperRow = makeSpecial('creeper', direction, 63 + direction, false);
    creeperRow.member.ignite();
    specialRows.push(creeperRow);
}
AutoJS.command('gamemode creative @a');
ServerEvents.tick(function(event) {
    if (specialClock >= 100) return;
    specialClock++;
    for (var row of specialRows) {
        try {
            if (specialClock === 1 && row.species === 'pufferfish') {
                row.early = {health: row.host.getHealth(), poison: row.host.hasEffect(Effects.POISON), puff: row.member.getPuffState(),
                    attacker: String(row.host.getLastHurtByMob()), attackerIsMember: row.host.getLastHurtByMob() === row.member};
            }
            if (specialClock === 100) {
                var lookup = world.getEntity(row.host.getId());
                row.finalState = {alive: row.host.isAlive(), removed: row.host.isRemoved(), health: row.host.getHealth(),
                    bodyTicks: row.host.tickCount - row.startTick, members: F.facets(row.host).size(),
                    worldLookupPresent: lookup !== null, poison: row.host.hasEffect(Effects.POISON)};
                if (row.species === 'creeper') row.finalState.ignited = row.member.isIgnited();
                row.host.discard();
            }
        } catch (error) {
            row.errors.push(String(error));
        }
    }
});
JSON.stringify(specialRows.map(function(row) { return {species: row.species, mode: row.mode,
    dye: row.dyeResult, color: row.colorAfterDye, shear: row.shearResult, sheared: row.sheared,
    give: row.giveResult, held: row.held}; }));
