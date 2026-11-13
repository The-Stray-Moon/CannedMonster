var ridingClientTicks=0;
var ridingUseResult=null;
ClientEvents.tick(function(event){
 if(ridingClientTicks>=65) return;
 ridingClientTicks++;
 if(ridingClientTicks===60){
  var b=Client.player.getVehicle();
  if(b===null || b.isControlledByLocalInstance()) throw new Error('Client still controls fusion physics');
  ridingUseResult=String(Client.gameMode.useItem(Client.player,Java.loadClass('net.minecraft.world.InteractionHand').MAIN_HAND));
 }
});
'riding boost input scheduled';
