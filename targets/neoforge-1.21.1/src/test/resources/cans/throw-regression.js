var Reg=Java.loadClass('cc.sighs.cannedmonster.CanRegistration');
var throwType=Reg.THROWN_CAN.get();
var ticks=0,stage=0,phase='start',throwError=null,throwChecks=[],flight=null,returnedCan=null,throwTarget=null,largeStored=null;
var zoneX=px+32,zoneZ=pz;
AutoJS.command('fill '+(zoneX-3)+' '+(floor-1)+' '+(zoneZ-5)+' '+(zoneX+12)+' '+(floor-1)+' '+(zoneZ+5)+' minecraft:stone');
AutoJS.command('fill '+(zoneX+8)+' '+floor+' '+(zoneZ-4)+' '+(zoneX+8)+' '+(floor+5)+' '+(zoneZ+4)+' minecraft:stone');
p.getInventory().setItem(8,p.getItemInHand(Hand.MAIN_HAND));p.setItemInHand(Hand.MAIN_HAND,Java.loadClass('net.minecraft.world.item.ItemStack').EMPTY);
function throwCheck(name,value){throwChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
function cleanDrops(){var drops=[];for(var e of world.getAllEntities())if(e.getType()===ET.ITEM&&Math.abs(e.getX()-zoneX)<14&&Math.abs(e.getZ()-zoneZ)<6)drops.push(e);for(var e of drops)e.discard();}
function target(type){var m=type.create(world);m.setPos(zoneX+5.5,floor,zoneZ+0.5);m.setNoAi(true);world.addFreshEntity(m);return m;}
function launch(stack,pitch){
 cleanDrops();AutoJS.command('tp @a '+(zoneX+0.5)+' '+floor+' '+(zoneZ+0.5)+' -90 '+pitch);p.setItemInHand(Hand.MAIN_HAND,stack);
 throwCheck('begin charge stage '+stage,stack.getItem().use(world,p,Hand.MAIN_HAND).getResult().consumesAction());phase='charge';ticks=0;flight=null;
}
ServerEvents.tick(function(event){
 if(phase==='done'||phase==='failed')return;
 try{
 if(phase==='start'){
  if(stage===0){throwTarget=target(ET.COW);launch(can('throwable_monster_can'),10);}
  else if(stage===1){throwTarget=target(ET.COW);launch(returnedCan,10);}
  else if(stage===2){launch(can('throwable_monster_can'),10);}
  else if(stage===3){launch(can('throwable_large_monster_can'),10);}
  else if(stage===4){largeStored=returnedCan;launch(largeStored,0);}
  else if(stage===5){launch(can('random_monster_can'),0);}
  else if(stage===6){var generated=[];for(var e of world.getAllEntities())if(e!==p&&e.getType()!==ET.ITEM&&e.getType()!==throwType&&Math.abs(e.getX()-zoneX)<14&&Math.abs(e.getZ()-zoneZ)<6)generated.push(e);for(var e of generated)if(!e.isRemoved())e.discard();launch(can('throwable_monster_can'),0);}
  else{phase='done';return;}
  return;
 }
 ticks++;
 if(phase==='charge'&&ticks>=25){
  throwCheck('is charging '+stage,p.isUsingItem()&&p.getTicksUsingItem()>=20);p.releaseUsingItem();
  for(var e of world.getAllEntities())if(e.getType()===throwType&&Math.abs(e.getX()-zoneX)<4)flight=e;
  throwCheck('projectile spawned '+stage,flight!==null);throwCheck('one can consumed '+stage,p.getItemInHand(Hand.MAIN_HAND).isEmpty());
  phase='flight';ticks=0;return;
 }
 if(phase==='flight'&&ticks>=30){
  throwCheck('projectile settled '+stage,flight.isRemoved());var drops=[];
  for(var e of world.getAllEntities())if(e.getType()===ET.ITEM&&Math.abs(e.getX()-zoneX)<14&&Math.abs(e.getZ()-zoneZ)<6&&e.getItem().getItem() instanceof Java.loadClass('cc.sighs.cannedmonster.can.MonsterCanItem'))drops.push(e);
  if(stage===5){throwCheck('random consumed on successful block spawn',drops.length===0);var found=false;for(var e of world.getAllEntities())if(e!==p&&e.getType()!==ET.ITEM&&e.getType()!==throwType&&e.isAlive()&&Math.abs(e.getX()-(zoneX+7.5))<3&&Math.abs(e.getZ()-zoneZ)<4)found=true;throwCheck('random block generated living mob',found);}
  else{
   throwCheck('exactly one returned can '+stage,drops.length===1);returnedCan=drops[0].getItem().copy();drops[0].discard();
   if(stage===0)throwCheck('empty projectile captures',throwTarget.isRemoved()&&CC.filled(returnedCan));
   if(stage===1)throwCheck('filled projectile fuses',!throwTarget.isRemoved()&&F.facets(throwTarget).size()===1&&!CC.filled(returnedCan));
   if(stage===2)throwCheck('small projectile rejects fused storage',!throwTarget.isRemoved()&&F.facets(throwTarget).size()===1&&!CC.filled(returnedCan));
   if(stage===3)throwCheck('large projectile captures fusion',throwTarget.isRemoved()&&CC.filled(returnedCan)&&CC.entity(returnedCan).contains('cannedmonster:fusion'));
   if(stage===4){throwCheck('filled projectile releases on block',!CC.filled(returnedCan));var released=[];for(var e of world.getAllEntities())if(e.getType()===ET.COW&&e.isAlive()&&Math.abs(e.getX()-zoneX)<14&&F.facets(e).size()===1)released.push(e);throwCheck('released projectile fusion restored',released.length===1);for(var e of released)e.discard();}
   if(stage===6)throwCheck('empty miss returns intact',!CC.filled(returnedCan));
  }
  stage++;phase='start';ticks=0;
 }
 }catch(error){phase='failed';throwError=String(error);console.error('Can throwing regression: '+throwError);}
});
JSON.stringify({stages:7,observing:'charge and actual projectile collision',persistentCanSlot:8});
