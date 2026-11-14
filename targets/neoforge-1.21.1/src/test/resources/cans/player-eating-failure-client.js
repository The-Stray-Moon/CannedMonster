var failedMealMC=Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var failedMealContents=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var failedMealAppearance=Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientAppearance');
var failedMealKey=Java.loadClass('net.minecraft.client.KeyMapping');
var failedMealOriginal=failedMealContents.entity(failedMealMC.player.getMainHandItem());
var failedMealPhase='holding',failedMealTicks=0,failedMealError=null,failedMealChecks=[];
var failedMealPreviousPause=failedMealMC.options.pauseOnLostFocus;
function failedMealCheck(name,value){failedMealChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
failedMealCheck('test starts with a filled invalid payload',failedMealContents.filled(failedMealMC.player.getMainHandItem())&&!failedMealOriginal.hasUUID('UUID'));
failedMealMC.options.pauseOnLostFocus=false;AutoJS.background(true);
failedMealMC.options.keyUse.setDown(true);failedMealKey.click(failedMealMC.options.keyUse.getKey());
ClientEvents.tick(function(event){
 if(failedMealPhase==='done'||failedMealPhase==='failed')return;
 try{
  if(failedMealMC.screen!==null)throw new Error('Input test requires an unpaused game with no screen');
  failedMealTicks++;
  if(failedMealPhase==='holding'&&failedMealTicks>=65){
   var stack=failedMealMC.player.getMainHandItem();
   failedMealCheck('failed meal retains exact entity contents',failedMealContents.filled(stack)&&failedMealContents.entity(stack).equals(failedMealOriginal));
   failedMealCheck('failed meal adds no member',failedMealAppearance.members(failedMealMC.player).isEmpty());
   failedMealCheck('failed completion is not automatically retried while held',!failedMealMC.player.isUsingItem());
   failedMealMC.options.keyUse.setDown(false);failedMealPhase='released';failedMealTicks=0;
  }else if(failedMealPhase==='released'&&failedMealTicks>=12){
   failedMealCheck('release after failed completion still retains contents',failedMealContents.entity(failedMealMC.player.getMainHandItem()).equals(failedMealOriginal));
   failedMealPhase='done';failedMealMC.options.pauseOnLostFocus=failedMealPreviousPause;
  }
 }catch(error){failedMealMC.options.keyUse.setDown(false);failedMealMC.options.pauseOnLostFocus=failedMealPreviousPause;failedMealPhase='failed';failedMealError=String(error);console.error('Failed meal regression: '+failedMealError);}
});
JSON.stringify({phase:failedMealPhase,checks:failedMealChecks});
