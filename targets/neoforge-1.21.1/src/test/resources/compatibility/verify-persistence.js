var F = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var world = AutoJS.level();
var expectedNames = ['compat-persist-0', 'compat-persist-1', 'compat-persist-2', 'compat-persist-3', 'compat-persist-4', 'compat-persist-5', 'compat-persist-6', 'compat-persist-7'];
var recovered = [];
for (var entity of world.getAllEntities()) {
    if (!entity.getTags().contains('compat-persist')) continue;
    var members = F.facets(entity);
    if (members.size() !== 1) throw new Error('Lost fusion member: ' + entity);
    if (!entity.isAlive() || members.get(0).isAddedToLevel()) throw new Error('Invalid recovered identity: ' + entity);
    recovered.push({name: entity.getCustomName().getString(), hostType: String(entity.getType()),
        memberType: String(members.get(0).getType()), health: entity.getHealth(), members: members.size(),
        hostBrain: String(entity.getBrain().getActiveActivities()), memberBrain: String(members.get(0).getBrain().getActiveActivities())});
}
if (recovered.length !== expectedNames.length) throw new Error('Expected 8 restored bodies, found ' + recovered.length);
for (var name of expectedNames) {
    if (!recovered.some(function(row) { return String(row.name) === name; })) throw new Error('Missing body ' + name);
}
JSON.stringify(recovered);
