if(!flightDone) throw new Error('Wait for the pursuit observation window');
var finalFlight=flightSamples[flightSamples.length-1];
if(finalFlight.moved<5 || finalFlight.y<203 || finalFlight.target==='null' || finalFlight.memberRegistered
        || !(finalFlight.swell===1 || finalFlight.distance<=4)) {
    throw new Error('Flight pursuit regressed: '+JSON.stringify(finalFlight));
}
JSON.stringify(finalFlight);
