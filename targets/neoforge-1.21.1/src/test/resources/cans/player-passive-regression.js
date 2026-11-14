var PassiveF=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var PassivePF=Java.loadClass('cc.sighs.cannedmonster.fusion.PlayerFusion');
var PassiveET=Java.loadClass('net.minecraft.world.entity.EntityType');
var PassiveL=Java.loadClass('java.util.List');
var PassiveH=Java.loadClass('net.minecraft.world.InteractionHand');
var PassiveEffects=Java.loadClass('net.minecraft.world.effect.MobEffects');
var passivePlayer=AutoJS.player(),passiveWorld=AutoJS.level(),passiveChecks=[],passivePhase='observe',passiveError=null;
function passiveCheck(name,value){passiveChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
if(!passivePlayer.isAlive()||passivePlayer.isRemoved())throw new Error('Respawn the test player before running this suite');
while(!PassiveF.facets(passivePlayer).isEmpty())PassiveF.peel(passivePlayer);
passivePlayer.removeEffect(PassiveEffects.FIRE_RESISTANCE);passivePlayer.clearFire();passivePlayer.setAirSupply(300);
var passiveWitch=PassiveET.WITCH.create(passiveWorld);PassiveF.attach(passivePlayer,PassiveL.of(passiveWitch));
passivePlayer.setItemInHand(PassiveH.MAIN_HAND,AutoJS.stack('minecraft:diamond',12));passivePlayer.setHealth(passivePlayer.getMaxHealth());
passiveCheck('no fire resistance was manually added',!passivePlayer.hasEffect(PassiveEffects.FIRE_RESISTANCE));
passivePlayer.igniteForSeconds(10);var passiveStart=Number(passivePlayer.tickCount);
ServerEvents.tick(function(event){
 if(passivePhase!=='observe')return;
 try{
  if(passivePlayer.hasEffect(PassiveEffects.FIRE_RESISTANCE)){
   passiveCheck('native member potion use affected the player',true);
   passiveCheck('player survived native passive behavior',passivePlayer.isAlive());
   passiveCheck('native temporary equipment did not replace player diamonds',passivePlayer.getMainHandItem().getItem()===AutoJS.stack('minecraft:diamond',1).getItem()&&passivePlayer.getMainHandItem().getCount()===12);
   var privateData=PassiveF.independentSave(passiveWitch);
   passiveCheck('consumed private potion is not recreated in save',privateData.getList('HandItems',10).getCompound(0).isEmpty());
   var output=AutoJS.stack('cannedmonster:monster_can',1);
   passiveCheck('active member can still be extracted',PassivePF.extract(passivePlayer,output));
   passiveCheck('extraction preserves player equipment',passivePlayer.getMainHandItem().getCount()===12);
   passivePlayer.clearFire();passivePlayer.removeEffect(PassiveEffects.FIRE_RESISTANCE);passivePlayer.setItemInHand(PassiveH.MAIN_HAND,AutoJS.stack('minecraft:air',0));passivePlayer.setHealth(passivePlayer.getMaxHealth());
   passivePhase='done';
  }else if(Number(passivePlayer.tickCount)-passiveStart>240){passiveCheck('native member potion use affected the player',false);}
 }catch(error){passiveError=String(error);passivePhase='failed';console.error('Player passive regression: '+passiveError);}
});
JSON.stringify({phase:passivePhase,checks:passiveChecks});
