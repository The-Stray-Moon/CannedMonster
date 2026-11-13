var boostDamage = motionPlayer.getMainHandItem().getDamageValue();
if (boostDamage !== 7) throw new Error('Expected one boost damage 7, got ' + boostDamage);
if (motionPlayer.getVehicle() !== boostBody || boostBody.getControllingPassenger() !== motionPlayer) throw new Error('Lost body riding identity');
JSON.stringify({damage:boostDamage,vehicleBody:true,controller:true});
