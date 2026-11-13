if(authorityPhase!==2) throw new Error('Finish authority.js first');
motionPlayer.stopRiding();
AutoJS.command('tp @a 0 201 -20 0 0');
AutoJS.command('fill -12 200 -30 12 200 30 minecraft:air');
authoritySpawn(true);
var edgesTicks=0;
var edgesSamples=[];
var edgesGeneration=authorityGeneration;
ServerEvents.tick(function(event){
 if(edgesGeneration!==authorityGeneration || edgesTicks>=180) return;
 edgesTicks++;
 if(edgesTicks===130) authorityBody.setDeltaMovement(authorityBody.getDeltaMovement().add(0.3,0,0));
 if(edgesTicks===155) authorityMember.setDeltaMovement(authorityMember.getDeltaMovement().add(0.3,0,0));
 if(edgesTicks%5===0){var r=authoritySnapshot();r.edgeTick=edgesTicks;r.damage=motionPlayer.getMainHandItem().getDamageValue();edgesSamples.push(r);}
 if(edgesTicks===180) motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:air',1));
});
JSON.stringify({edgesStarted:true,initial:authoritySnapshot()});
