if(String(AutoJS.server().getWorldData().getLevelName())!=='Fusion motion verification') throw new Error('Wrong world');
motionPlayer.stopRiding();
if(authorityBody!==null && !authorityBody.isRemoved()) authorityBody.discard();
AutoJS.command('fill -12 200 -30 12 203 30 minecraft:water');
var deepPhase=0;
var deepTicks=0;
var deepBody=null;
var deepMember=null;
var deepResults=[];
function deepSpawn(){
 motionPlayer.stopRiding();
 if(deepBody!==null && !deepBody.isRemoved()) deepBody.discard();
 AutoJS.command('tp @a 0 201 -20 0 0');
 deepBody=(deepPhase===2?ET.SQUID:ET.PIG).create(world);
 deepMember=deepPhase===0?null:(deepPhase===1?ET.SQUID:ET.PIG).create(world);
 deepBody.setPos(0,200,-20);
 if(deepMember!==null) F.attach(deepBody,Java.loadClass('java.util.List').of(deepMember));
 deepBody.addTag('motion-verification');world.addFreshEntity(deepBody);
 motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:saddle',1));motionPlayer.interactOn(deepBody,Hand.MAIN_HAND);
 motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:carrot_on_a_stick',1));motionPlayer.interactOn(deepBody,Hand.MAIN_HAND);
}
deepSpawn();
ServerEvents.tick(function(event){
 if(deepPhase>=3)return;
 deepTicks++;
 if(deepTicks===40){
  deepResults.push({phase:deepPhase,ticks:deepBody.tickCount,water:deepBody.isInWater(),mounted:motionPlayer.getVehicle()===deepBody,z:deepBody.getZ()});
  deepPhase++;deepTicks=0;
  if(deepPhase<3)deepSpawn();
 }
});
'deep water native and two fusion orders started';
