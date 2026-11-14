var inputMinecraft=Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var inputHand=Java.loadClass('net.minecraft.world.InteractionHand');
var inputContents=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var inputAppearance=Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientAppearance');
var inputPhase='shortHold',inputElapsed=0,inputError=null,inputChecks=[];
function inputCheck(name,value){inputChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
var inputKeyMapping=Java.loadClass('net.minecraft.client.KeyMapping');
function inputUse(){inputMinecraft.options.keyUse.setDown(true);inputKeyMapping.click(inputMinecraft.options.keyUse.getKey());}
inputCheck('start with a filled can',inputContents.filled(inputMinecraft.player.getMainHandItem()));
inputCheck('filled can uses normal 32 tick eating duration',inputMinecraft.player.getMainHandItem().getUseDuration(inputMinecraft.player)===32);
var inputPreviousPause=inputMinecraft.options.pauseOnLostFocus;
inputMinecraft.options.pauseOnLostFocus=false;
AutoJS.background(true);
inputUse();
ClientEvents.tick(function(event){
 if(inputPhase==='done'||inputPhase==='failed')return;
 try{
  if(inputMinecraft.screen!==null)throw new Error('Input test requires an unpaused game with no screen');
  inputElapsed++;
  if(inputPhase==='shortHold'&&inputElapsed>=8){
   inputCheck('short hold does not fuse',inputContents.filled(inputMinecraft.player.getMainHandItem()));
   inputMinecraft.options.keyUse.setDown(false);inputPhase='afterCancel';inputElapsed=0;
  }else if(inputPhase==='afterCancel'&&inputElapsed>=12){
   inputCheck('early release retains can contents',inputContents.filled(inputMinecraft.player.getMainHandItem()));
   inputCheck('early release does not add a member',inputAppearance.members(inputMinecraft.player).isEmpty());
   inputUse();inputPhase='eating';inputElapsed=0;
  }else if(inputPhase==='eating'){
   if(inputElapsed===20)inputCheck('incomplete meal retains contents',inputContents.filled(inputMinecraft.player.getMainHandItem()));
   if(inputElapsed>=48){
    inputCheck('meal completion empties the can without releasing',!inputContents.filled(inputMinecraft.player.getMainHandItem()));
    inputCheck('meal completion fuses exactly one member',inputAppearance.members(inputMinecraft.player).size()===1);
    inputPhase='holdAfterMeal';inputElapsed=0;
   }
  }else if(inputPhase==='holdAfterMeal'&&inputElapsed>=45){
   inputCheck('continued hold does not start empty-can use',!inputMinecraft.player.isUsingItem());
   inputCheck('continued hold does not extract or refusion',!inputContents.filled(inputMinecraft.player.getMainHandItem())&&inputAppearance.members(inputMinecraft.player).size()===1);
   inputMinecraft.options.keyUse.setDown(false);inputPhase='afterMealRelease';inputElapsed=0;
  }else if(inputPhase==='afterMealRelease'&&inputElapsed>=12){
   inputCheck('releasing the meal press does not extract',!inputContents.filled(inputMinecraft.player.getMainHandItem())&&inputAppearance.members(inputMinecraft.player).size()===1);
   inputUse();inputPhase='holdEmpty';inputElapsed=0;
  }else if(inputPhase==='holdEmpty'&&inputElapsed>=12){
   inputCheck('new empty press does not extract before release',inputAppearance.members(inputMinecraft.player).size()===1);
   inputMinecraft.options.keyUse.setDown(false);inputPhase='afterEmpty';inputElapsed=0;
  }else if(inputPhase==='afterEmpty'&&inputElapsed>=15){
   inputCheck('independent empty release fills can',inputContents.filled(inputMinecraft.player.getMainHandItem()));
   inputCheck('independent empty release removes member and appearance',inputAppearance.members(inputMinecraft.player).isEmpty());
   inputPhase='done';inputMinecraft.options.pauseOnLostFocus=inputPreviousPause;
  }
 }catch(error){inputMinecraft.options.keyUse.setDown(false);inputMinecraft.options.pauseOnLostFocus=inputPreviousPause;inputError=String(error);inputPhase='failed';console.error('Player input regression: '+inputError);}
});
JSON.stringify({phase:inputPhase,checks:inputChecks});
