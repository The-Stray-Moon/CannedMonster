if (!flightDone) throw new Error('Wait for bounded flight pursuit observer');
var flightResult = flightSamples[flightSamples.length-1];
if (flightResult.moved < 5 || flightResult.y < 203 || flightResult.target === 'null' || flightResult.swell !== 1 || flightResult.memberRegistered) throw new Error('Flight pursuit failed');
for (var row of motionRows) if (!row.body.isRemoved()) row.body.discard();
motionRows = [];
motionPlayer.setGameMode(Mode.CREATIVE);
motionPlayer.stopRiding();
AutoJS.tp(motionPlayer,0,200,10);
var rideRow = motionSpawn('squid -> pig land ride', ET.SQUID, ET.PIG,0,200,10);
rideRow.member.setAirSupply(6000);
motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:saddle',1));
var saddleResult = motionPlayer.interactOn(rideRow.body,Hand.MAIN_HAND);
motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:carrot_on_a_stick',1));
var mountResult = motionPlayer.interactOn(rideRow.body,Hand.MAIN_HAND);
function rideSnapshot() {
 var b=rideRow.body;
 return {body:String(b.getType()),member:String(rideRow.member.getType()),
 vehicleBody:motionPlayer.getVehicle()===b,rootBody:motionPlayer.getRootVehicle()===b,
 controllerPlayer:b.getControllingPassenger()===motionPlayer,passengers:b.getPassengers().size(),
 x:b.getX(),y:b.getY(),z:b.getZ(),water:b.isInWater(),ticks:b.tickCount-rideRow.tick,
 velocity:String(b.getDeltaMovement()),memberVelocity:String(rideRow.member.getDeltaMovement()),
 durability:motionPlayer.getMainHandItem().getDamageValue()};
}
var rideElapsed=0;
var rideSamples=[];
var rideGeneration=motionGeneration;
ServerEvents.tick(function(event){
 if (rideGeneration!==motionGeneration || rideElapsed>=100) return;
 rideElapsed++;
 if(rideElapsed%10===0) rideSamples.push(rideSnapshot());
});
JSON.stringify({flight:flightResult,saddle:String(saddleResult),mount:String(mountResult),ride:rideSnapshot()});
