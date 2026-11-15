var TF=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var TT=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionTraits');
var TG=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionGaze');
var TE=Java.loadClass('net.minecraft.world.entity.EntityType');
var TL=Java.loadClass('java.util.List');
var TM=Java.loadClass('net.minecraft.world.entity.ai.memory.MemoryModuleType');
var TS=Java.loadClass('net.minecraft.world.entity.ai.sensing.Sensor');
var tp=AutoJS.player(),tw=AutoJS.level(),targetChecks=[],targetPhase='setup',targetTick=0,targetError=null;
function targetCheck(name,value){targetChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
if(!tp.isAlive()||tp.isRemoved())throw new Error('Respawn the test player before running this suite');
while(!TF.facets(tp).isEmpty())TF.peel(tp);
var targetMember=TE.PIGLIN_BRUTE.create(tw),targetBreeze=TE.BREEZE.create(tw);
TF.attach(tp,TL.of(targetMember,targetBreeze));
var allowedVictim=TE.COW.create(tw),foreignVictim=TE.COW.create(tw);
var targetBrain=targetMember.getBrain();
function targetPlace(victim,sign){
 var yaw=Number(tp.getYRot())*Math.PI/180;
 var fx=-Math.sin(yaw),fz=Math.cos(yaw);
 victim.setPos(tp.getX()+sign*fx*8,tp.getY(),tp.getZ()+sign*fz*8);
 victim.setNoAi(true);victim.setNoGravity(true);tw.addFreshEntity(victim);
}
function targetAim(victim){
 var eye=tp.getEyePosition(1),look=victim.getEyePosition(1);
 var dx=look.x-eye.x,dy=look.y-eye.y,dz=look.z-eye.z;
 tp.setXRot(-Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))*180/Math.PI);
 tp.setYRot(Math.atan2(dz,dx)*180/Math.PI-90);
 tp.setYHeadRot(tp.getYRot());tp.setYBodyRot(tp.getYRot());
 TG.clear();
}
targetPlace(allowedVictim,1);targetPlace(foreignVictim,-1);
targetAim(allowedVictim);
targetCheck('gaze target is authorized',TT.allowedTarget(tp,allowedVictim));
targetCheck('non-gaze target is refused',!TT.allowedTarget(tp,foreignVictim));
targetMember.setTarget(foreignVictim);
targetCheck('non-gaze Mob target is refused',targetMember.getTarget()===null);
targetBrain.setMemory(TM.ATTACK_TARGET,foreignVictim);
targetCheck('non-gaze Brain target is refused',!targetBrain.getMemory(TM.ATTACK_TARGET).isPresent());
targetMember.setTarget(allowedVictim);
targetBrain.setMemory(TM.ATTACK_TARGET,allowedVictim);
targetCheck('gaze Mob target is retained',targetMember.getTarget()===allowedVictim);
targetCheck('gaze Brain target is retained',targetBrain.getMemory(TM.ATTACK_TARGET).get()===allowedVictim);
targetCheck('non-gaze melee action is refused',!targetMember.doHurtTarget(foreignVictim));
targetCheck('native breeze still excludes cows',!targetBreeze.canAttackType(TE.COW));
targetBreeze.getBrain().setMemory(TM.ATTACK_TARGET,allowedVictim);
targetCheck('gaze type passes breeze target validation',TS.isEntityAttackable(targetBreeze,allowedVictim));
targetAim(foreignVictim);
targetPhase='switch';targetTick=0;
ServerEvents.tick(function(event){
 if(targetPhase==='done'||targetPhase==='failed')return;
 try{
  targetTick++;
  if(targetPhase==='switch'&&targetTick>=2){
   targetCheck('new gaze target is authorized',TT.allowedTarget(tp,foreignVictim));
   targetCheck('member target follows a new gaze',targetMember.getTarget()===foreignVictim);
   targetCheck('Brain target follows a new gaze',
    targetBrain.getMemory(TM.ATTACK_TARGET).isPresent()&&targetBrain.getMemory(TM.ATTACK_TARGET).get()===foreignVictim);
   targetCheck('breeze target follows a new gaze',targetBreeze.getTarget()===foreignVictim);
   tp.setXRot(-90);tp.setYRot(0);tp.setYHeadRot(0);tp.setYBodyRot(0);TG.clear();
   targetPhase='clear';targetTick=0;return;
  }
  if(targetPhase==='clear'&&targetTick>=2){
   targetCheck('looking at nothing clears the member target',targetMember.getTarget()===null);
   targetCheck('looking at nothing clears the Brain target',!targetBrain.getMemory(TM.ATTACK_TARGET).isPresent());
   targetCheck('looking at nothing clears the breeze target',targetBreeze.getTarget()===null);
   TF.peel(tp);allowedVictim.discard();foreignVictim.discard();TG.clear();targetPhase='done';
  }
 }catch(error){targetError=String(error);targetPhase='failed';console.error('Player target regression: '+targetError);}
});
JSON.stringify({phase:targetPhase,checks:targetChecks});
