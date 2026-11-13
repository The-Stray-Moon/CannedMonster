if(authorityPhase!==2) throw new Error('Wait for both 80 tick riding phases');
var authorityChecks=[];
for(var phase=0;phase<2;phase++){
 var rows=authorityResults.filter(function(r){return r.phase===phase;});
 var last=rows[rows.length-1];
 if(last.z+20<3 || Math.abs(last.x)>0.5 || !last.vehicle || !last.controller || last.registered) throw new Error('Straight riding failed: '+JSON.stringify(last));
 authorityChecks.push({phase:phase,forward:last.z+20,sideways:last.x});
}
authoritySpawn(true);
var turnStart=null;
var turnEnd=null;
var turnTicks=0;
var turnGeneration=authorityGeneration;
ServerEvents.tick(function(event){
 if(turnGeneration!==authorityGeneration || turnTicks>=60) return;
 turnTicks++;
 if(turnTicks===1) turnStart=authoritySnapshot();
 if(turnTicks===60){turnEnd=authoritySnapshot(); motionPlayer.setItemInHand(Hand.MAIN_HAND,AutoJS.stack('minecraft:air',1));}
});
JSON.stringify({straight:authorityChecks,turnReady:true});
