var identityRow = makeSpecial('pig', 1, 75, false);
var queried = world.getEntities(identityRow.member, identityRow.host.getBoundingBox().inflate(1), function(entity) { return true; });
for (var entity of queried) if (entity === identityRow.host) throw new Error('Excluded member still returned its body');
var damage = identityRow.host.damageSources().mobAttack(identityRow.member);
if (damage.getActual() !== identityRow.host || damage.getImmediate() !== identityRow.host) throw new Error('Damage source retained member identity');
var nearbyBefore = [];
for (var entity of world.getAllEntities()) {
    if (Math.abs(entity.getX() - identityRow.point.x) < 4 && Math.abs(entity.getZ() - identityRow.point.z) < 4 && entity.isAlive()) nearbyBefore.push(entity);
}
identityRow.host.discard();
var nearbyAfter = [];
for (var entity of world.getAllEntities()) {
    if (Math.abs(entity.getX() - identityRow.point.x) < 4 && Math.abs(entity.getZ() - identityRow.point.z) < 4 && entity.isAlive()) nearbyAfter.push(entity);
}
if (!identityRow.host.isRemoved() || F.facets(identityRow.host).size() !== 0 || nearbyAfter.length !== nearbyBefore.length - 1) throw new Error('Discard leaked or created descendants');
JSON.stringify({queryExclusion: true, damageIdentity: true, discardCleanup: true});
