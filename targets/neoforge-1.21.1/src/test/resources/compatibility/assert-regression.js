var checks = [];
function check(name, condition) {
    checks.push({name: name, pass: !!condition});
    if (!condition) throw new Error('Regression failed: ' + name);
}
check('matrix completed', matrixElapsed === 200 && matrix.length === 54);
for (var row of matrix) {
    check('matrix errors ' + row.species + '/' + row.mode, row.errors.length === 0);
    if (row.species === 'warden') continue;
    check('matrix alive ' + row.species + '/' + row.mode, row.observed.alive && row.observed.bodyTicks === 200);
    check('matrix identity ' + row.species + '/' + row.mode, !row.observed.facetRegistered);
    check('matrix save ' + row.species + '/' + row.mode, row.roundtripMembers === (row.mode === 'native-control' ? 0 : 1));
}
check('specials completed', specialClock === 100);
for (var row of specialRows) {
    check('special errors ' + row.mode, row.errors.length === 0);
    if (row.species === 'sheep') {
        check('dye ' + row.mode, row.colorAfterDye === 'red' && row.dyeResult === 'CONSUME');
        check('shear ' + row.mode, row.sheared && row.shearResult === 'CONSUME');
    }
    if (row.species === 'pufferfish') {
        check('no self poison ' + row.mode, row.finalState.alive && !row.finalState.poison && !row.early.attackerIsMember);
        check('no self damage ' + row.mode, row.finalState.health === (row.direction === 1 ? 10 : 3));
    }
    if (row.species === 'allay') check('give item ' + row.mode, row.held === 'minecraft:diamond');
    if (row.species === 'creeper') check('explosion removes body ' + row.mode, row.finalState.removed && !row.finalState.worldLookupPresent);
}
check('boundaries completed', boundaryClock === 60);
for (var row of boundaryRows) {
    check('boundary errors ' + row.mode, row.errors.length === 0);
    if (row.species === 'enderman') check('teleport ' + row.mode, row.teleportReturned && row.hostMoved && row.memberBodyPositionMatches);
    if (row.species === 'snow_golem') check('projectile identity ' + row.mode,
        row.projectileCreated && row.ownerIsHost && row.ownerRegistered && row.ownerLookupIsOwner && row.ownerLookupIsHost);
    if (row.species === 'slime') {
        check('split count ' + row.mode, row.children.length >= 2 && row.children.length <= 4);
        for (var child of row.children) check('split inheritance ' + row.mode, child.size === 2 && child.members === (row.direction === 0 ? 0 : 1));
    }
}
JSON.stringify({passed: checks.length, checks: checks});
