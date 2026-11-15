var EF=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var ET=Java.loadClass('net.minecraft.world.entity.EntityType');
var EL=Java.loadClass('java.util.List');
var EA=Java.loadClass('net.minecraft.commands.arguments.EntityAnchorArgument$Anchor');
var EAttributes=Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes');
var ep=AutoJS.player(),ew=AutoJS.level();
if(!EF.facets(ep).isEmpty())throw new Error('Use a player without existing members');
var eventGuardian=ET.GUARDIAN.create(ew),eventPig=ET.PIG.create(ew);
eventPig.setNoAi(true);eventPig.setNoGravity(true);
eventPig.setPos(ep.getX(),ep.getY(),ep.getZ()+6);
eventPig.getAttribute(EAttributes.MAX_HEALTH).setBaseValue(1000);eventPig.setHealth(1000);
if(!ew.addFreshEntity(eventPig))throw new Error('Pig rejected');
EF.attach(ep,EL.of(eventGuardian));
ep.lookAt(EA.valueOf('EYES'),eventPig.getEyePosition(1));
var eventStart=Number(ep.tickCount),eventHealth=eventPig.getHealth(),eventTicks=0,eventResult=null,eventHits=[];
ServerEvents.tick(function(event){
 if(eventResult!==null)return;
 eventTicks++;
 if(eventPig.getHealth()<eventHealth){eventHits.push(eventTicks);eventHealth=eventPig.getHealth();}
 if(eventTicks>=310){
  var spaced=eventHits.length>=2;
  for(var i=1;i<eventHits.length;i++)if(eventHits[i]-eventHits[i-1]<80)spaced=false;
  eventResult={ticks:Number(ep.tickCount)-eventStart,hits:eventHits,pass:spaced};
  if(!spaced)throw new Error('Guardian must recharge between attacks: '+JSON.stringify(eventResult));
 }
});
JSON.stringify({started:true,start:eventStart});
