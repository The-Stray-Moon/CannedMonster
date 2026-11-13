if(authorityPhase!==2) throw new Error('Wait for both 80 real tick phases');
var authorityReport=[];
for(var i=0;i<2;i++){
 var rows=authorityResults.filter(function(r){return r.phase===i;});
 var last=rows[rows.length-1];
 if(last.tick!==80 || last.bodyTicks!==80 || last.z+20<3 || Math.abs(last.x)>0.5 || !last.water || !last.vehicle || !last.controller || last.registered) throw new Error('Authoritative riding failed: '+JSON.stringify(last));
 authorityReport.push({phase:i,ticks:last.bodyTicks,forward:last.z+20,sideways:last.x});
}
JSON.stringify(authorityReport);
