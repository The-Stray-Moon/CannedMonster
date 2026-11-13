motionPlayer.stopRiding();
AutoJS.command('tp @a 0 201 -20 0 0');
authoritySpawn(true);
var forceTicks=0;
var forceSamples=[];
var forceWrites=[];
ServerEvents.tick(function(event){
 if(forceTicks>=100) return;
 forceTicks++;
 if(forceTicks===20 || forceTicks===50){
  var target=forceTicks===20?authorityBody:authorityMember;
  target.setDeltaMovement(target.getDeltaMovement().add(0.3,0,0));
  forceWrites.push({tick:forceTicks,body:String(authorityBody.getDeltaMovement()),member:String(authorityMember.getDeltaMovement())});
 }
 if(forceTicks%5===0){var r=authoritySnapshot();r.forceTick=forceTicks;forceSamples.push(r);}
 if(forceTicks===75) motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:air',1));
 if(forceTicks===90) motionPlayer.stopRiding();
});
'force and release observer started';
