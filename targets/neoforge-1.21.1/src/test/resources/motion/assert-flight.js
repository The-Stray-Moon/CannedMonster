if(!flightDone) throw new Error('Wait for pursuit completion, at most 200 real ticks');
var finalFlight=flightSamples[flightSamples.length-1];
if(finalFlight.ticks!==flightElapsed || finalFlight.ticks>200 || finalFlight.moved<5 || finalFlight.y<203 || finalFlight.target==='null' || finalFlight.swell!==1 || finalFlight.memberRegistered) throw new Error('Flight pursuit regressed: '+JSON.stringify(finalFlight));
JSON.stringify(finalFlight);
