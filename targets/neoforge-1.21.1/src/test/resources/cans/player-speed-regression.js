var speedF=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var speedPF=Java.loadClass('cc.sighs.cannedmonster.fusion.PlayerFusion');
var speedCC=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var speedET=Java.loadClass('net.minecraft.world.entity.EntityType');
var speedR=Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var speedA=Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes');
var speedRL=Java.loadClass('net.minecraft.resources.ResourceLocation');
var speedT=Java.loadClass('net.minecraft.nbt.CompoundTag');
var speedPlayer=AutoJS.player(),speedWorld=AutoJS.level(),speedChecks=[];
var speedId=speedRL.parse('cannedmonster:fusion_speed');
function speedCheck(name,value){speedChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
function speedCan(type,name){var stack=AutoJS.stack('cannedmonster:monster_can',1);var data=type.create(speedWorld).saveWithoutId(new speedT());data.putString('id',speedR.ENTITY_TYPE.getKey(type).toString());speedCC.store(stack,data,name);return stack;}
function speedInfo(){var instance=speedPlayer.getAttribute(speedA.MOVEMENT_SPEED);var modifier=instance.getModifier(speedId);return {base:instance.getBaseValue(),value:speedPlayer.getAttributeValue(speedA.MOVEMENT_SPEED),modifier:modifier===null?null:modifier.amount()};}
function speedNear(a,b){return Math.abs(Number(a)-b)<0.000001;}
function speedReset(){while(!speedF.facets(speedPlayer).isEmpty())speedPF.extract(speedPlayer,AutoJS.stack('cannedmonster:monster_can',1));speedPlayer.getAttribute(speedA.MOVEMENT_SPEED).setBaseValue(0.1);}
function speedFuse(type,name){var can=speedCan(type,name);speedCheck('prepare '+type,speedPF.fuse(speedPlayer,can));return speedInfo();}
function speedExtract(){var can=AutoJS.stack('cannedmonster:monster_can',1);speedCheck('extract member',speedPF.extract(speedPlayer,can));return speedInfo();}
if(!speedPlayer.isAlive()||speedPlayer.isRemoved())throw new Error('Respawn the test player before running this suite');
speedReset();
var nativeSpeed=speedInfo();
speedCheck('native player base is 0.1',speedNear(nativeSpeed.base,0.1)&&speedNear(nativeSpeed.value,0.1)&&nativeSpeed.modifier===null);
var cow=speedFuse(speedET.COW,'speed cow');
speedCheck('cow base attribute is not overwritten',speedNear(cow.base,0.1));
speedCheck('cow contributes squared native drive',cow.modifier!==null&&speedNear(cow.modifier,-0.03));
speedCheck('cow effective player speed is averaged',speedNear(cow.value,0.07));
speedCheck('cow is counted as a ground member',speedF.facets(speedPlayer).size()===1);
var afterCow=speedExtract();
speedCheck('extracting cow removes the fusion modifier',afterCow.modifier===null&&speedNear(afterCow.base,0.1)&&speedNear(afterCow.value,0.1));
var zombie=speedFuse(speedET.ZOMBIE,'speed zombie');
speedCheck('zombie native drive is slower than player walk',zombie.modifier!==null&&speedNear(zombie.modifier,-0.02355)&&speedNear(zombie.value,0.07645));
speedPlayer.getAttribute(speedA.MOVEMENT_SPEED).setBaseValue(0.2);
var changed=speedInfo();
speedCheck('external base change is preserved',speedNear(changed.base,0.2));
speedCheck('external base change recomputes the modifier',changed.modifier!==null&&speedNear(changed.modifier,-0.07355)&&speedNear(changed.value,0.12645));
speedPlayer.getAttribute(speedA.MOVEMENT_SPEED).setBaseValue(0.1);
var restored=speedInfo();
speedCheck('restoring the base recomputes the modifier',speedNear(restored.modifier,-0.02355)&&speedNear(restored.value,0.07645));
speedExtract();
var squid=speedFuse(speedET.SQUID,'speed squid');
speedCheck('water navigation is neutral instead of guessed',squid.modifier===null&&speedNear(squid.value,0.1));
speedExtract();
var turtle=speedFuse(speedET.TURTLE,'speed turtle');
speedCheck('custom move control is neutral instead of guessed',turtle.modifier===null&&speedNear(turtle.value,0.1));
speedExtract();
var fastCow=speedET.COW.create(speedWorld);fastCow.getAttribute(speedA.MOVEMENT_SPEED).setBaseValue(0.4);
speedF.prepend(speedPlayer,fastCow);
var fast=speedInfo();
speedCheck('a faster standard ground member can raise speed',fast.modifier!==null&&Number(fast.modifier)>0&&Number(fast.value)>0.1);
speedExtract();
speedPlayer.getAttribute(speedA.MOVEMENT_SPEED).setBaseValue(0.1);
speedCheck('final state has no member or modifier',speedF.facets(speedPlayer).isEmpty()&&speedInfo().modifier===null&&speedNear(speedInfo().value,0.1));
JSON.stringify({passed:speedChecks.length,checks:speedChecks});
