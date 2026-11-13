if(reverseElapsed!==80) throw new Error('Wait for 80 real ticks');
var last=reverseSamples[reverseSamples.length-1];
var maxSideways=0;
for(var sample of reverseSamples) maxSideways=Math.max(maxSideways,Math.abs(sample.x));
var report={elapsed:reverseElapsed,forward:last.z+10,sideways:maxSideways,vehicleBody:last.vehicleBody,controller:last.controllerPlayer,water:last.water};
if(!last.vehicleBody || !last.controllerPlayer || !last.water || report.forward<3 || maxSideways>0.5) throw new Error('Reverse riding is NOT fixed: '+JSON.stringify(report));
JSON.stringify(report);
