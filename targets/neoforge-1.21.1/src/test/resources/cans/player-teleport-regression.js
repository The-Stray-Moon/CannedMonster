var TF=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var TB=Java.loadClass('cc.sighs.cannedmonster.fusion.BodyLinks');
var TC=Java.loadClass('cc.sighs.cannedmonster.fusion.TeleportCalls');
var TE=Java.loadClass('net.minecraft.world.entity.EntityType');
var TL=Java.loadClass('java.util.List');
var TMOVER=Java.loadClass('net.minecraft.world.entity.MoverType');
var TVEC=Java.loadClass('net.minecraft.world.phys.Vec3');
var tp=AutoJS.player(),tw=AutoJS.level(),telChecks=[],telPhase='setup',telTick=0,telError=null;
var telMember=null,telStart=[0,0,0],telMoved=false,telPacket=null,telHits=0;
function telCheck(name,value){telChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
function telPos(){return [Number(tp.getX()),Number(tp.getY()),Number(tp.getZ())];}
function telDist(a,b){var dx=a[0]-b[0],dy=a[1]-b[1],dz=a[2]-b[2];return Math.sqrt(dx*dx+dy*dy+dz*dz);}
if(!tp.isAlive()||tp.isRemoved())throw new Error('Respawn the test player before running this suite');
try{
 var packetClass=Java.loadClass('net.minecraft.server.network.ServerGamePacketListenerImpl');
 var packetField=packetClass.getDeclaredField('awaitingPositionFromClient');
 packetField.setAccessible(true);
 telPacket=function(){return tp.connection==null?null:packetField.get(tp.connection);};
}catch(error){telPacket=function(){return null;};}
function telPlatform(){
 var x=Math.floor(Number(tp.getX())),y=Math.floor(Number(tp.getY())),z=Math.floor(Number(tp.getZ()));
 AutoJS.command('fill '+(x-8)+' '+(y-1)+' '+(z-8)+' '+(x+8)+' '+(y-1)+' '+(z+8)+' minecraft:stone');
 AutoJS.command('fill '+(x-8)+' '+y+' '+(z-8)+' '+(x+8)+' '+(y+4)+' '+(z+8)+' minecraft:air');
 AutoJS.tp(tp,x+0.5,y,z+0.5);
}
function telHit(){
 tp.setHealth(tp.getMaxHealth());tp.invulnerableTime=0;
 tp.hurt(tw.damageSources().magic(),1.0);
}
function telShulkerTeleport(){
 telMember.move(TMOVER.SHULKER_BOX,TVEC.ZERO);
}
while(!TF.facets(tp).isEmpty())TF.peel(tp);
AutoJS.command('gamerule doMobSpawning false');
AutoJS.command('gamerule mobGriefing false');
AutoJS.command('gamerule naturalRegeneration false');
AutoJS.command('time set midnight');
telPlatform();
tp.setHealth(tp.getMaxHealth());tp.invulnerableTime=0;
telMember=TE.ENDERMAN.create(tw);TF.attach(tp,TL.of(telMember));
telStart=telPos();
ServerEvents.tick(function(event){
 if(telPhase==='done'||telPhase==='failed')return;
 try{
  telTick++;
  if(telPhase==='idle'){
   if(telTick>=40){
    telCheck('untouched member does not move the body',telDist(telPos(),telStart)<0.05);
    telPhase='enderman';telTick=0;telStart=telPos();telHits=0;return;
   }
  }
  if(telPhase==='enderman'){
   if(telTick%2===0&&telHits<40){
    telHits++;
    telHit();
    if(telDist(telPos(),telStart)>1.0){
     telMoved=true;
     telCheck('native enderman trigger moves the player',true);
     telCheck('player keeps its own bounding box',
      Math.abs(Number(tp.getBbWidth())-0.6)<0.001&&Math.abs(Number(tp.getBbHeight())-1.8)<0.001);
     telCheck('player lands without collision',tw.noCollision(tp));
     telCheck('member stays bound and out of the world',
      !telMember.isAddedToLevel()&&TB.same(telMember,tp));
     if(telPacket()!=null)telCheck('teleport used the player position packet',true);
     var beforeCooldown=telPos();
     telHit();
     telCheck('second trigger inside the cooldown does not move the body',telDist(telPos(),beforeCooldown)<0.01);
     TF.peel(tp);telMember.discard();telMember=null;telPhase='shulkerSetup';telTick=0;return;
    }
   }
   if(telHits>=40)throw new Error('enderman never teleported during 40 hurt triggers');
  }
  if(telPhase==='shulkerSetup'){
   TC.clear();telPlatform();tp.setHealth(tp.getMaxHealth());tp.invulnerableTime=0;
   telMember=TE.SHULKER.create(tw);TF.attach(tp,TL.of(telMember));
   telStart=telPos();
   telShulkerTeleport();
   telCheck('shulker teleport moves the player',telDist(telPos(),telStart)>0.01);
   telCheck('shulker keeps the player bounding box',
    Math.abs(Number(tp.getBbWidth())-0.6)<0.001&&Math.abs(Number(tp.getBbHeight())-1.8)<0.001);
   telCheck('shulker destination has no collision',tw.noCollision(tp));
   var beforeCooldown=telPos();
   telShulkerTeleport();
   telCheck('refused shulker teleport does not move the body',telDist(telPos(),beforeCooldown)<0.01);
   telPhase='shulkerHurt';telTick=0;telStart=telPos();telHits=0;return;
  }
  if(telPhase==='shulkerHurt'){
   if(telTick%20===0&&telHits<20){
    telHits++;
    TC.clear();
    tp.setHealth(tp.getMaxHealth()*0.4);tp.invulnerableTime=0;
    tp.hurt(tw.damageSources().magic(),0.5);
    if(telDist(telPos(),telStart)>0.01){
     telCheck('shulker hurt trigger moves the player',true);
     TF.peel(tp);telMember.discard();telMember=null;TC.clear();telPhase='done';return;
    }
   }
   if(telHits>=20)throw new Error('shulker never teleported during 20 hurt triggers');
  }
 }catch(error){telError=String(error);telPhase='failed';console.error('Player teleport regression: '+telError);}
});
JSON.stringify({phase:telPhase,checks:telChecks});
