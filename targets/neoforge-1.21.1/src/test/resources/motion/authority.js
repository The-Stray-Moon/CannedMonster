var world=AutoJS.level();
if(String(AutoJS.server().getWorldData().getLevelName())!=='Fusion motion verification') throw new Error('Wrong world');
var F=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var R=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRiding');
var ET=Java.loadClass('net.minecraft.world.entity.EntityType');
var Hand=Java.loadClass('net.minecraft.world.InteractionHand');
var Mode=Java.loadClass('net.minecraft.world.level.GameType');
var motionPlayer=AutoJS.player();
motionPlayer.stopRiding();
for(var old of world.getAllEntities()) if(!old.isRemoved() && old.getTags().contains('motion-verification')) old.discard();
AutoJS.command('fill -12 199 -30 12 199 30 minecraft:stone');
AutoJS.command('fill -12 200 -30 12 205 30 minecraft:air');
AutoJS.command('fill -12 200 -30 12 200 30 minecraft:water');
AutoJS.command('gamerule doMobSpawning false');
motionPlayer.setGameMode(Mode.SURVIVAL);
AutoJS.command('effect give @a minecraft:resistance 999 4 true');
var authorityGeneration=typeof authorityGeneration==='undefined'?1:authorityGeneration+1;
var authorityResults=[];
var authorityElapsed=0;
var authorityPhase=0;
var authorityBody=null;
var authorityMember=null;
function authoritySpawn(reverse){
 if(authorityBody!==null && !authorityBody.isRemoved()){motionPlayer.stopRiding();authorityBody.discard();}
 AutoJS.command('tp @a 0 201 -20 0 0');
 authorityBody=(reverse?ET.SQUID:ET.PIG).create(world);
 authorityMember=(reverse?ET.PIG:ET.SQUID).create(world);
 authorityBody.setPos(0,200,-20);
 F.attach(authorityBody,Java.loadClass('java.util.List').of(authorityMember));
 world.addFreshEntity(authorityBody);authorityBody.addTag('motion-verification');
 motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:saddle',1));
 motionPlayer.interactOn(authorityBody,Hand.MAIN_HAND);
 motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:carrot_on_a_stick',1));
 motionPlayer.interactOn(authorityBody,Hand.MAIN_HAND);

}
function authoritySnapshot(){return {phase:authorityPhase,tick:authorityElapsed,bodyTicks:authorityBody.tickCount,x:authorityBody.getX(),y:authorityBody.getY(),z:authorityBody.getZ(),water:authorityBody.isInWater(),vehicle:motionPlayer.getVehicle()===authorityBody,controller:authorityBody.getControllingPassenger()===motionPlayer,source:String(R.source(authorityBody)),velocity:String(authorityBody.getDeltaMovement()),registered:authorityMember.isAddedToLevel()};}
authoritySpawn(true);
(function(observerGeneration){
ServerEvents.tick(function(event){
 if(observerGeneration!==authorityGeneration || authorityPhase>=2) return;
 authorityElapsed++;
 if(authorityElapsed%10===0) authorityResults.push(authoritySnapshot());
 if(authorityElapsed===80){
  motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:air',1));
  authorityPhase++;
  if(authorityPhase<2){authoritySpawn(false);authorityElapsed=0;}
 }
});
})(authorityGeneration);
JSON.stringify(authoritySnapshot());
