var derivedRows = [];
var derivedClock = 0;
for (var original of boundaryRows) {
    if (original.species !== 'slime' || original.direction === 0) continue;
    var children = [];
    for (var entity of world.getAllEntities()) {
        if (entity.getType() === (original.direction === 1 ? ET.COW : ET.SLIME)
                && Math.abs(entity.getX() - original.point.x) < 4 && Math.abs(entity.getZ() - original.point.z) < 4
                && entity.isAlive() && F.facets(entity).size() === 1) children.push(entity);
    }
    if (children.length < 2) throw new Error('Missing derived children ' + original.mode);
    var survivor = children[0];
    var Component = Java.loadClass('net.minecraft.network.chat.Component');
    survivor.setCustomName(Component.literal('derived-persist-' + original.direction));
    survivor.addTag('derived-persist');
    survivor.setNoAi(true);
    var saved = survivor.saveWithoutId(new Tag());
    var copy = createType(original.direction === 1 ? 'cow' : 'slime');
    try {
        copy.load(saved);
        if (F.facets(copy).size() !== 1) throw new Error('Derived save lost member');
    } finally { F.detach(copy); }
    var killed = children.length - 1;
    for (var i = 1; i < children.length; i++) children[i].addTag('derived-second-' + original.direction);
    AutoJS.command('kill @e[tag=derived-second-' + original.direction + ']');
    derivedRows.push({mode: original.mode, point: original.point, direction: original.direction, killed: killed, survivor: survivor});
}
ServerEvents.tick(function(event) {
    if (derivedClock >= 60) return;
    derivedClock++;
    if (derivedClock !== 60) return;
    for (var row of derivedRows) {
        row.grandchildren = [];
        for (var entity of world.getAllEntities()) {
            if (entity.getType() !== (row.direction === 1 ? ET.COW : ET.SLIME) || !entity.isAlive()
                    || Math.abs(entity.getX() - row.point.x) >= 4 || Math.abs(entity.getZ() - row.point.z) >= 4
                    || entity === row.survivor) continue;
            var members = F.facets(entity);
            var slime = entity;
            if (row.direction === 1) for (var member of members) if (member.getType() === ET.SLIME) slime = member;
            row.grandchildren.push({size: slime.getSize(), members: members.size(), hostRegistered: entity.isAddedToLevel(), facetRegistered: members.size() > 0 && members.get(0).isAddedToLevel()});
        }
    }
});
AutoJS.command('save-all flush');
JSON.stringify(derivedRows.map(function(row) { return {mode: row.mode, killed: row.killed, persisted: true}; }));
