function edgeAt(t){return edgesSamples.filter(function(r){return r.edgeTick===t;})[0];}
var edgeBaseline=(edgeAt(50).z-edgeAt(30).z)/20;
var edgeBoost=(edgeAt(120).z-edgeAt(100).z)/20;
if(edgesTicks!==180 || edgeAt(120).damage!==7 || edgeBoost<=edgeBaseline*1.1 || edgeAt(150).x<=0.3 || edgeAt(175).x-edgeAt(150).x<=0.3) throw new Error('Acceleration or external velocity regression');
JSON.stringify({baseline:edgeBaseline,boost:edgeBoost,damage:edgeAt(120).damage,bodyImpulse:edgeAt(150).x,memberImpulse:edgeAt(175).x-edgeAt(150).x});
