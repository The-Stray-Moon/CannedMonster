var switchMealMC=Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var switchMealContents=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var switchMealAppearance=Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientAppearance');
var switchMealKey=Java.loadClass('net.minecraft.client.KeyMapping');
var switchMealSlot=switchMealMC.player.getInventory().selected,switchMealOther=(switchMealSlot+1)%9;
var switchMealOriginal=switchMealContents.entity(switchMealMC.player.getMainHandItem());
var switchMealPhase='eating',switchMealTicks=0,switchMealError=null,switchMealChecks=[];
var switchMealPreviousPause=switchMealMC.options.pauseOnLostFocus;
function switchMealCheck(name,value){switchMealChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
switchMealCheck('switch test starts with filled can',switchMealContents.filled(switchMealMC.player.getMainHandItem()));
switchMealMC.options.pauseOnLostFocus=false;AutoJS.background(true);
switchMealMC.options.keyUse.setDown(true);switchMealKey.click(switchMealMC.options.keyUse.getKey());
ClientEvents.tick(function(event){
 if(switchMealPhase==='done'||switchMealPhase==='failed')return;
 try{
  if(switchMealMC.screen!==null)throw new Error('Input test requires an unpaused game with no screen');
  switchMealTicks++;
  if(switchMealPhase==='eating'&&switchMealTicks>=10){
   switchMealMC.player.getInventory().selected=switchMealOther;switchMealPhase='away';switchMealTicks=0;
  }else if(switchMealPhase==='away'&&switchMealTicks>=12){
   switchMealCheck('switching retains original payload',switchMealContents.entity(switchMealMC.player.getInventory().getItem(switchMealSlot)).equals(switchMealOriginal));
   switchMealCheck('switching before completion adds no member',switchMealAppearance.members(switchMealMC.player).isEmpty());
   switchMealMC.player.getInventory().selected=switchMealSlot;switchMealPhase='backHeld';switchMealTicks=0;
  }else if(switchMealPhase==='backHeld'&&switchMealTicks>=40){
   switchMealCheck('switching back while held does not automatically restart eating',!switchMealMC.player.isUsingItem()&&switchMealContents.entity(switchMealMC.player.getMainHandItem()).equals(switchMealOriginal)&&switchMealAppearance.members(switchMealMC.player).isEmpty());
   switchMealMC.options.keyUse.setDown(false);switchMealPhase='released';switchMealTicks=0;
  }else if(switchMealPhase==='released'&&switchMealTicks>=12){
   switchMealCheck('release after switch cancellation retains contents',switchMealContents.entity(switchMealMC.player.getMainHandItem()).equals(switchMealOriginal));
   switchMealPhase='done';switchMealMC.options.pauseOnLostFocus=switchMealPreviousPause;
  }
 }catch(error){switchMealMC.options.keyUse.setDown(false);switchMealMC.player.getInventory().selected=switchMealSlot;switchMealMC.options.pauseOnLostFocus=switchMealPreviousPause;switchMealError=String(error);switchMealPhase='failed';console.error('Switch meal regression: '+switchMealError);}
});
JSON.stringify({phase:switchMealPhase,checks:switchMealChecks});
