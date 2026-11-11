var RejectEvent='net.neoforged.neoforge.event.entity.EntityJoinLevelEvent';
var rejectRelease=false,rejectDrop=false,failureTicks=0,failurePhase='start',failureError=null,failureFlight=null,failureResults=[];
var rejectCan=can('monster_can'),rejectMob=spawn(ET.COW,-4,-4);
rejectMob.addTag('can-reject-release');
if(!CA.onMob(rejectCan,rejectMob,p))throw new Error('Failed to prepare release rejection test');
NativeEvents.onEvent(RejectEvent,function(event){var entity=event.getEntity();if(rejectRelease&&entity.getTags().contains('can-reject-release'))event.setCanceled(true);if(rejectDrop&&entity.getType()===ET.ITEM&&entity.getItem().getItem() instanceof Java.loadClass('cc.sighs.cannedmonster.can.MonsterCanItem'))event.setCanceled(true);});
rejectRelease=true;
if(CA.release(rejectCan,world,new Vec(px-4,floor,pz-4))||!CC.filled(rejectCan))throw new Error('Canceled release lost contents');
failureResults.push('canceled release retained contents');rejectRelease=false;
if(!CA.release(rejectCan,world,new Vec(px-4,floor,pz-4))||CC.filled(rejectCan))throw new Error('Release did not recover after cancellation removed');
for(var e of world.getAllEntities())if(e.getTags().contains('can-reject-release'))e.discard();
failureResults.push('release retries successfully');
var failureTarget=target(ET.COW);rejectDrop=true;
cleanDrops();AutoJS.command('tp @a '+(zoneX+0.5)+' '+floor+' '+(zoneZ+0.5)+' -90 10');p.setItemInHand(Hand.MAIN_HAND,can('throwable_monster_can'));p.getItemInHand(Hand.MAIN_HAND).getItem().use(world,p,Hand.MAIN_HAND);
ServerEvents.tick(function(event){if(failurePhase==='done'||failurePhase==='failed')return;failureTicks++;try{
 if(failurePhase==='start'&&failureTicks>=25){p.releaseUsingItem();for(var e of world.getAllEntities())if(e.getType()===throwType&&Math.abs(e.getX()-zoneX)<4)failureFlight=e;if(!failureFlight)throw new Error('Failure test projectile missing');failurePhase='blocked';failureTicks=0;}
 else if(failurePhase==='blocked'&&failureTicks>=35){if(!failureTarget.isRemoved()||failureFlight.isRemoved()||!CC.filled(failureFlight.getItem()))throw new Error('Rejected drop lost captured mob');var snapshot=failureFlight.saveWithoutId(new Tag());if(!snapshot.getBoolean('ReturningCan'))throw new Error('Return retry state missing');var copy=throwType.create(world);copy.load(snapshot);if(!CC.filled(copy.getItem())||!copy.saveWithoutId(new Tag()).getBoolean('ReturningCan'))throw new Error('Retry snapshot lost contents');failureResults.push('rejected item drop retained filled projectile');failureResults.push('retry snapshot retains captured entity');rejectDrop=false;failurePhase='retry';failureTicks=0;}
 else if(failurePhase==='retry'&&failureTicks>=25){if(!failureFlight.isRemoved())throw new Error('Return retry did not finish');var count=0;for(var e of world.getAllEntities())if(e.getType()===ET.ITEM&&Math.abs(e.getX()-zoneX)<14&&CC.filled(e.getItem()))count++;if(count!==1)throw new Error('Return retry duplicated or lost can: '+count);failureResults.push('retry dropped exactly one filled can');failurePhase='done';}
}catch(error){rejectDrop=false;rejectRelease=false;failureError=String(error);failurePhase='failed';console.error(failureError);}});
JSON.stringify({releaseFailuresHandled:true,observingRejectedDrop:true});
