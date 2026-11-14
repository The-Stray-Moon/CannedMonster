if(!verify.done) throw new Error('Wait for both 160 tick phases');
var checks=[];
for(var phase of verify.phases){
 if(phase.end>phase.start-2.5) throw new Error('Pursuit did not approach: '+JSON.stringify(phase));
 checks.push({body:phase.body,start:phase.start,end:phase.end,direct:phase.direct});
}
if(!verify.phases[0].direct) throw new Error('Squid body did not use the direct fallback');
if(verify.control.direct) throw new Error('Non-fusion squid must not use fusion steering');
JSON.stringify({checks:checks,control:verify.control});
