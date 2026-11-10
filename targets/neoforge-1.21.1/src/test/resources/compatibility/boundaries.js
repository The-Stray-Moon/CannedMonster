var boundaryRows = [];
var boundaryClock = 0;
for (var direction = 0; direction < 3; direction++) {
    var enderRow = makeSpecial('enderman', direction, 66 + direction, false);
    var oldPos = enderRow.host.position();
    try {
        enderRow.teleportReturned = enderRow.member.randomTeleport(enderRow.point.x + 2.5, testY, enderRow.point.z + 0.5, false);
        enderRow.hostMoved = enderRow.host.position().distanceToSqr(oldPos) > 0.1;
        enderRow.memberBodyPositionMatches = enderRow.member.position().distanceToSqr(enderRow.host.position()) < 0.00001;
        enderRow.lookupPresent = world.getEntity(enderRow.host.getId()) !== null;
    } catch (error) { enderRow.errors.push(String(error)); }
    boundaryRows.push(enderRow);
    var snowRow = makeSpecial('snow_golem', direction, 69 + direction, false);
    try {
        var target = createType('cow');
        target.setPos(snowRow.point.x + 2, testY, snowRow.point.z);
        target.setNoAi(true);
        world.addFreshEntity(target);
        var prior = [];
        for (var entity of world.getAllEntities()) if (entity.getType() === ET.SNOWBALL) prior.push(entity.getId());
        snowRow.member.performRangedAttack(target, 1.0);
        var projectile = null;
        for (var entity of world.getAllEntities()) {
            if (entity.getType() === ET.SNOWBALL && prior.indexOf(entity.getId()) < 0) projectile = entity;
        }
        if (!projectile) throw new Error('No snowball created');
        var owner = projectile.getOwner();
        snowRow.projectileCreated = true;
        snowRow.ownerIsHost = owner === snowRow.host;
        snowRow.ownerIsMember = owner === snowRow.member;
        snowRow.ownerRegistered = owner.isAddedToLevel();
        snowRow.ownerLookupIsOwner = world.getEntity(owner.getId()) === owner;
        snowRow.ownerLookupIsHost = world.getEntity(owner.getId()) === snowRow.host;
        projectile.discard();
        target.discard();
    } catch (error) { snowRow.errors.push(String(error)); }
    boundaryRows.push(snowRow);
    var slimeRow = makeSpecial('slime', direction, 72 + direction, false);
    try {
        slimeRow.member.setSize(4, true);
        slimeRow.host.addTag('compat_slime_' + direction);
        AutoJS.command('kill @e[tag=compat_slime_' + direction + ']');
    } catch (error) { slimeRow.errors.push(String(error)); }
    boundaryRows.push(slimeRow);
}
var boundaryGeneration = typeof boundaryGeneration === 'undefined' ? 1 : boundaryGeneration + 1;
(function(generation) {
ServerEvents.tick(function(event) {
    if (generation !== boundaryGeneration || boundaryClock >= 60) return;
    boundaryClock++;
    if (boundaryClock !== 60) return;
    for (var row of boundaryRows) {
        try {
            if (row.species === 'slime') {
                var children = [];
                for (var entity of world.getAllEntities()) {
                    if (entity.getType() === (row.direction === 1 ? ET.COW : ET.SLIME)
                            && Math.abs(entity.getX() - row.point.x) < 4 && Math.abs(entity.getZ() - row.point.z) < 4 && entity.isAlive()) {
                        var slime = entity;
                        if (row.direction === 1) {
                            for (var member of F.facets(entity)) if (member.getType() === ET.SLIME) slime = member;
                        }
                        children.push({size: slime.getSize(), members: F.facets(entity).size(), hostType: String(entity.getType())});
                    }
                }
                row.children = children;
                row.removed = row.host.isRemoved();
            }
            if (!row.host.isRemoved()) row.host.discard();
        } catch (error) { row.errors.push(String(error)); }
    }
});
})(boundaryGeneration);
JSON.stringify(boundaryRows.map(function(row) { return {species: row.species, mode: row.mode,
    teleport: row.teleportReturned, moved: row.hostMoved, positionMatches: row.memberBodyPositionMatches,
    projectile: row.projectileCreated, ownerIsHost: row.ownerIsHost, ownerIsMember: row.ownerIsMember,
    ownerRegistered: row.ownerRegistered, ownerLookupIsOwner: row.ownerLookupIsOwner,
    ownerLookupIsHost: row.ownerLookupIsHost, errors: row.errors}; }));
