if(deepPhase!==3) throw new Error('Wait for three 40 tick phases');
if(deepResults[0].mounted || deepResults[1].mounted || !deepResults[2].mounted || deepResults[2].z+20<3) throw new Error('Unexpected body-native deep water riding behavior: '+JSON.stringify(deepResults));
JSON.stringify(deepResults);
