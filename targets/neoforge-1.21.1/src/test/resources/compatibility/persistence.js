var persistentRows = [];
var persistentModes = ['villager->cow', 'cow->villager', 'allay->cow', 'cow->allay', 'guardian->cow', 'cow->guardian', 'frog->cow', 'cow->frog'];
var Component = Java.loadClass('net.minecraft.network.chat.Component');
var persistenceIndex = 0;
for (var mode of persistentModes) {
    var original = matrix.filter(function(row) { return row.mode === mode; })[0];
    if (!original || !original.saved || original.roundtripMembers !== 1) throw new Error('Missing valid saved row: ' + mode);
    var host = createType(mode.indexOf('->cow') >= 0 ? 'cow' : original.species);
    host.load(original.saved);
    host.setCustomName(Component.literal('compat-persist-' + persistenceIndex));
    host.addTag('compat-persist');
    if (!world.addFreshEntity(host)) throw new Error('Restore rejected: ' + mode);
    var member = F.facets(host).get(0);
    persistentRows.push({mode: mode, host: host, name: 'compat-persist-' + persistenceIndex,
        members: F.facets(host).size(), memberType: String(member.getType()),
        memberBrain: String(member.getBrain().getActiveActivities()), hostBrain: String(host.getBrain().getActiveActivities()),
        startTick: host.tickCount, facetRegistered: member.isAddedToLevel()});
    persistenceIndex++;
}
AutoJS.command('save-all flush');
JSON.stringify(persistentRows.map(function(row) { return {mode: row.mode, name: row.name, members: row.members,
    memberType: row.memberType, memberBrain: row.memberBrain, hostBrain: row.hostBrain, facetRegistered: row.facetRegistered}; }));
