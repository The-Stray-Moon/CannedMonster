var world=AutoJS.level();
if(String(AutoJS.server().getWorldData().getLevelName())!=='Fusion motion verification') throw new Error('Wrong world');
var F=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var P=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionPursuit');
var ET=Java.loadClass('net.minecraft.world.entity.EntityType');
var Lists=Java.loadClass('java.util.List');
var Mode=Java.loadClass('net.minecraft.world.level.GameType');
var player=AutoJS.player();
player.stopRiding();
var stale=[];for(var e of world.getAllEntities())if(e!=null&&!e.isRemoved()&&e.getTags().contains('motion-verification'))stale.push(e);for(var e of stale)e.discard();
AutoJS.command('gamerule doMobSpawning false');
AutoJS.command('gamerule mobGriefing false');
AutoJS.command('fill -14 199 -30 14 199 30 minecraft:stone');
AutoJS.command('fill -14 200 -30 14 205 30 minecraft:water');
AutoJS.command('tp @a 0 203 0 0 0');
player.setGameMode(Mode.SURVIVAL);
AutoJS.command('effect give @a minecraft:resistance 999 4 true');
AutoJS.command('effect give @a minecraft:water_breathing 999 0 true');
var verify={done:false,phases:[],control:null,error:null};
var phase=0,ticks=0,body=null,member=null,samples=[],control=null;
function spawn(first,bodyType,z){
 if(body!==null&&!body.isRemoved())body.discard();
 body=bodyType.create(world);member=first.create(world);
 body.setPos(10,203,z);F.attach(body,Lists.of(member));
 body.addTag('motion-verification');world.addFreshEntity(body);
}
function sample(){
 return {tick:ticks,distance:body.distanceTo(player),direct:P.direct(body),state:P.describe(body),water:body.isInWater(),alive:body.isAlive(),target:body.getTarget()!==null,memberRegistered:member.isAddedToLevel()};
}
control=ET.SQUID.create(world);
control.setPos(10,203,10);control.addTag('motion-verification');world.addFreshEntity(control);
spawn(ET.CREEPER,ET.SQUID,0);
var verifyGeneration=1;
(function(generation){
ServerEvents.tick(function(event){
 if(generation!==verifyGeneration||verify.done)return;
 ticks++;
 if(ticks%10===0){
  var s=sample();
  if(phase===2)s.controlDistance=control.distanceTo(player);
  samples.push(s);
 }
 if(ticks<160)return;
 var first=samples[0],last=samples[samples.length-1];
 if(phase<2){
  verify.phases.push({phase:phase,body:String(body.getType()),start:first.distance,end:last.distance,direct:last.direct,state:last.state});
  phase++;ticks=0;samples=[];
  if(phase===1)spawn(ET.SQUID,ET.CREEPER,0);
  return;
 }
 verify.control={start:samples[0].controlDistance,end:last.controlDistance,direct:last.direct};
 if(control!=null&&!control.isRemoved())control.discard();
 if(body!==null&&!body.isRemoved())body.discard();
 verify.done=true;
});
})(verifyGeneration);
'pursuit verification started';
