function forceAt(t){return forceSamples.filter(function(r){return r.forceTick===t;})[0];}
if(forceTicks!==100 || forceAt(45).x-forceAt(15).x<0.3 || forceAt(70).x-forceAt(45).x<0.3 || forceAt(80).controller || forceAt(100).vehicle) throw new Error('External velocity or release failed');
JSON.stringify({bodyImpulse:forceAt(45).x-forceAt(15).x,memberImpulse:forceAt(70).x-forceAt(45).x,rodReleased:!forceAt(80).controller,dismounted:!forceAt(100).vehicle});
