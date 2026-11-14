var TF=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var TT=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionTraits');
var TE=Java.loadClass('net.minecraft.world.entity.EntityType');
var TL=Java.loadClass('java.util.List');
var TM=Java.loadClass('net.minecraft.world.entity.ai.memory.MemoryModuleType');
var tp=AutoJS.player(),tw=AutoJS.level(),targetChecks=[];
function targetCheck(name,value){targetChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
if(!tp.isAlive()||tp.isRemoved())throw new Error('Respawn the test player before running this suite');
while(!TF.facets(tp).isEmpty())TF.peel(tp);
var targetMember=TE.PIGLIN_BRUTE.create(tw);TF.attach(tp,TL.of(targetMember));
var allowedVictim=TE.COW.create(tw),foreignVictim=TE.COW.create(tw);
allowedVictim.setPos(tp.getX()+10,tp.getY(),tp.getZ()+4);foreignVictim.setPos(tp.getX()+10,tp.getY(),tp.getZ()-4);
allowedVictim.setNoAi(true);foreignVictim.setNoAi(true);tw.addFreshEntity(allowedVictim);tw.addFreshEntity(foreignVictim);
var targetBrain=targetMember.getBrain();
tp.setLastHurtMob(allowedVictim);
targetMember.setTarget(foreignVictim);
targetCheck('unapproved Mob target is refused',targetMember.getTarget()===null);
targetBrain.setMemory(TM.ATTACK_TARGET,foreignVictim);
targetCheck('unapproved Brain target is refused',!targetBrain.getMemory(TM.ATTACK_TARGET).isPresent());
targetMember.setTarget(allowedVictim);
targetBrain.setMemory(TM.ATTACK_TARGET,allowedVictim);
targetCheck('approved Mob target is retained',targetMember.getTarget()===allowedVictim);
targetCheck('approved Brain target is retained',targetBrain.getMemory(TM.ATTACK_TARGET).get()===allowedVictim);
targetCheck('foreign melee action is refused',!targetMember.doHurtTarget(foreignVictim));
var targetStart=Number(tp.tickCount),targetPhase='expiry',targetError=null;
ServerEvents.tick(function(event){
 if(targetPhase!=='expiry')return;
 if(Number(tp.tickCount)-targetStart<105)return;
 try{
  targetCheck('expired intent cannot authorize an attack',!TT.allowedTarget(tp,allowedVictim));
  targetCheck('expired Brain target is not exposed',!targetBrain.getMemory(TM.ATTACK_TARGET).isPresent());
  TF.peel(tp);allowedVictim.discard();foreignVictim.discard();targetPhase='done';
 }catch(error){targetPhase='failed';targetError=String(error);console.error('Player target regression: '+targetError);}
});
JSON.stringify({phase:targetPhase,checks:targetChecks});
