var RF=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var RT=Java.loadClass('net.minecraft.world.entity.EntityType');
var RL=Java.loadClass('java.util.List');
var RA=Java.loadClass('net.minecraft.commands.arguments.EntityAnchorArgument$Anchor');
var RV=Java.loadClass('net.minecraft.world.phys.Vec3');
var RAttr=Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes');
var RShoot=Java.loadClass('net.minecraft.world.entity.monster.breeze.Shoot');
var rp=AutoJS.player(),rw=AutoJS.level();
if(!RF.facets(rp).isEmpty())throw new Error('Use an unfused player in an isolated flat test area');
var rotationChecks=[],rotationPhase=0,rotationTicks=0,rotationDone=false,rotationError=null;
var rotationDirections=[[0,0,6],[6,2,0],[0,0,-6],[-6,1,0]];
var rotationOrigin=[rp.getX(),rp.getY(),rp.getZ()],rotationShots=[0,0,0,0];
rp.lookAt(RA.valueOf('EYES'),new RV(rp.getX(),rp.getY()+50,rp.getZ()));
var rotationMember=RT.BREEZE.create(rw);RF.attach(rp,RL.of(rotationMember));
var rotationPig=RT.PIG.create(rw);rotationPig.setNoAi(true);rotationPig.setNoGravity(true);
rotationPig.getAttribute(RAttr.MAX_HEALTH).setBaseValue(1000);
rotationPig.getAttribute(RAttr.KNOCKBACK_RESISTANCE).setBaseValue(1);rotationPig.setHealth(1000);
if(!rw.addFreshEntity(rotationPig))throw new Error('Test target spawn rejected');
function rotationCheck(name,ok){rotationChecks.push({name:name,pass:!!ok});if(!ok)throw new Error(name);}
function rotationAim(){var d=rotationDirections[rotationPhase];rotationPig.setPos(rotationOrigin[0]+d[0],rotationOrigin[1]+d[1],rotationOrigin[2]+d[2]);rp.lookAt(RA.valueOf('EYES'),rotationPig.getEyePosition(1));}
rotationAim();
EntityEvents.spawned(function(event){if(!rotationDone&&event.entity.getType()===RT.BREEZE_WIND_CHARGE)rotationShots[rotationPhase]++;});
ServerEvents.tick(function(event){
 if(rotationDone)return;
 try{
  rotationTicks++;
  if(rotationTicks===5){
   rotationCheck('view matches player '+rotationPhase,rotationMember.getViewVector(1).distanceToSqr(rp.getViewVector(1))<1e-10);
   rotationCheck('interpolated view matches '+rotationPhase,rotationMember.getViewVector(0.5).distanceToSqr(rp.getViewVector(0.5))<1e-10);
   var view=rp.getViewVector(1),head=rp.getYHeadRot(),body=rp.yBodyRot;
   rotationMember.lookAt(RA.valueOf('EYES'),new RV(rp.getX()+20,rp.getY()+30,rp.getZ()-20));
   rotationMember.setYHeadRot(123);
   rotationCheck('member cannot rotate player '+rotationPhase,view.distanceToSqr(rp.getViewVector(1))<1e-10&&head===rp.getYHeadRot()&&body===rp.yBodyRot);
   rotationCheck('native facing check '+rotationPhase,RShoot.isFacingTarget(rotationMember,rotationPig));
  }
  if(rotationShots[rotationPhase]>0&&rotationTicks>=30){
   rotationCheck('native projectile spawned '+rotationPhase,true);
   rotationPhase++;rotationTicks=0;
   if(rotationPhase===rotationDirections.length){rotationDone=true;return;}
   rotationAim();
  }else if(rotationTicks>=600)throw new Error('No native wind charge in phase '+rotationPhase);
 }catch(error){rotationError=String(error);rotationDone=true;console.error(rotationError);}
});
JSON.stringify({started:true});
