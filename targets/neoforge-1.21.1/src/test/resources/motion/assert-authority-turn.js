if(turnTicks!==60 || turnEnd.x-turnStart.x> -3 || Math.abs(turnEnd.z-turnStart.z)>0.5) throw new Error('Rider-directed turn failed');
JSON.stringify({dx:turnEnd.x-turnStart.x,dz:turnEnd.z-turnStart.z,bodyTicks:turnEnd.bodyTicks});
