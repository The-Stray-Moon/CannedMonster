var boostVehicle = Client.player.getVehicle();
if (boostVehicle == null || boostVehicle.getControllingPassenger() !== Client.player) throw new Error('Wait for the mounted control member to synchronize');
var boostBefore = Client.player.getMainHandItem().getDamageValue();
if (boostBefore !== 0) throw new Error('Use a fresh carrot on a stick');
var boostUse = Client.gameMode.useItem(Client.player, Java.loadClass('net.minecraft.world.InteractionHand').MAIN_HAND);
JSON.stringify({before:boostBefore, result:String(boostUse), local:boostVehicle.isControlledByLocalInstance()});
