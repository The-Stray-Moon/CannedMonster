if (String(AutoJS.server().getWorldData().getLevelName()) !== 'Fusion motion verification') throw new Error('Wrong world');
if (motionElapsed < 160) throw new Error('Wait for temptation observer');
var temptationResults = motionObserved[motionObserved.length-1].rows;
for (var r of temptationResults) if (!r.alive || r.moved < 3 || r.distance > 4 || r.memberRegistered) throw new Error('Temptation failed: ' + JSON.stringify(r));
for (var row of motionRows) if (!row.body.isRemoved()) row.body.discard();
motionRows = [];
motionPlayer.setItemInHand(Hand.MAIN_HAND, AutoJS.stack('minecraft:air', 1));
motionPlayer.setGameMode(Mode.SURVIVAL);
AutoJS.command('effect give @a minecraft:resistance 120 4 true');
AutoJS.tp(motionPlayer, 0,204,0);
AutoJS.command('fill -1 203 -1 1 203 1 minecraft:glass');
var flightRow = motionSpawn('creeper -> allay survival pursuit', ET.CREEPER, ET.ALLAY, 10,204,0);
var flightElapsed = 0;
var flightSamples = [];
var flightDone = false;
(function(flightGeneration) {
ServerEvents.tick(function(event) {
    if (flightGeneration !== motionGeneration || flightDone || flightElapsed >= 200) return;
    flightElapsed++;
    if (flightElapsed % 5 === 0) {
        var r = motionSnapshot(flightRow);
        r.swell = flightRow.member.getSwellDir();
        flightSamples.push(r);
        if (r.swell === 1 || flightElapsed === 200) {
            flightDone = true;
            if (!flightRow.body.isRemoved()) flightRow.body.discard();
        }
    }
});
})(motionGeneration);
JSON.stringify({temptation:temptationResults,flightStarted:true});
