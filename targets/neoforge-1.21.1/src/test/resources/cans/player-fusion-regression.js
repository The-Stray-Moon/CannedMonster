var F=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var PF=Java.loadClass('cc.sighs.cannedmonster.fusion.PlayerFusion');
var CC=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var ET=Java.loadClass('net.minecraft.world.entity.EntityType');
var Hand=Java.loadClass('net.minecraft.world.InteractionHand');
var T=Java.loadClass('net.minecraft.nbt.CompoundTag');
var Stack=Java.loadClass('net.minecraft.world.item.ItemStack');
var CanItem=Java.loadClass('cc.sighs.cannedmonster.can.MonsterCanItem');
var world=AutoJS.level(),p=AutoJS.player();
if(!p.isAlive()||p.isRemoved())throw new Error('Respawn the test player before running this suite');
var px=Math.floor(p.getX()),pz=Math.floor(p.getZ()),floor=180;
AutoJS.command('gamerule doMobSpawning false');
AutoJS.command('gamerule mobGriefing false');
AutoJS.command('time set midnight');
AutoJS.command('fill '+(px-6)+' '+(floor-1)+' '+(pz-6)+' '+(px+6)+' '+(floor-1)+' '+(pz+6)+' minecraft:stone');
AutoJS.command('fill '+(px-4)+' '+floor+' '+(pz-4)+' '+(px+4)+' '+(floor+16)+' '+(pz+4)+' minecraft:air');
AutoJS.tp(p,px,floor,pz);
while(PF.facets(p).size()>0)PF.extract(p,can('monster_can'));
var checks=[],phase='start',ticks=0,failure=null,startX=0,startY=0,startZ=0;
function check(name,value){checks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
function can(name){return AutoJS.stack('cannedmonster:'+name,1);}
function cleanCans(){for(var i=0;i<p.getInventory().getContainerSize();i++){var s=p.getInventory().getItem(i);if(s.getItem() instanceof CanItem)p.getInventory().setItem(i,Stack.EMPTY);}}
function drops(){var out=[];for(var e of world.getAllEntities())if(e.getType()===ET.ITEM&&Math.abs(e.getX()-px)<12&&Math.abs(e.getZ()-pz)<12&&e.getItem().getItem() instanceof CanItem)out.push(e);return out;}
function allCans(){var out=[];for(var i=0;i<p.getInventory().getContainerSize();i++){var s=p.getInventory().getItem(i);if(s.getItem() instanceof CanItem)out.push(s);}for(var e of drops())out.push(e.getItem());return out;}
function cleanDrops(){for(var e of drops())e.discard();}
function cowCan(){var stack=can('monster_can');var cow=ET.COW.create(world);var data=cow.saveWithoutId(new T());data.putString('id','minecraft:cow');CC.store(stack,data,'Player Fusion Cow');return stack;}
ServerEvents.tick(function(event){
 if(phase==='done'||phase==='failed')return;
 ticks++;
 try{
  if(phase==='start'){
   cleanCans();cleanDrops();
   var filled=cowCan();p.setItemInHand(Hand.MAIN_HAND,filled);
   p.getMainHandItem().getItem().use(world,p,Hand.MAIN_HAND);
   p.releaseUsingItem();filled=p.getMainHandItem();
   check('early release retains filled can',CC.filled(filled)&&PF.facets(p).isEmpty());
   filled.getItem().finishUsingItem(filled,world,p);
   check('finish without an active completed meal retains contents',CC.filled(filled)&&PF.facets(p).isEmpty());
   check('prepare player fusion through transfer API',PF.fuse(p,filled));
   var members=PF.facets(p);
   check('successful fusion empties the can',!CC.filled(filled));
   check('successful fusion adds one cow',members.size()===1&&members.get(0).getType()===ET.COW);
   check('member is bound but not independently registered',!members.get(0).isAddedToLevel()&&!members.get(0).isRemoved()&&Java.loadClass('cc.sighs.cannedmonster.fusion.BodyLinks').isFacet(members.get(0)));
   check('player max health is averaged',Math.abs(Number(p.getMaxHealth())-15)<0.001);
   check('player stays registered',p.isAddedToLevel()&&!p.isRemoved());
   filled.getItem().releaseUsing(filled,world,p,72000-1);
   check('fusion release does not also extract',PF.facets(p).size()===1&&!CC.filled(filled));
   startX=Number(p.getX());startY=Number(p.getY());startZ=Number(p.getZ());
   var saved=p.saveWithoutId(new T());
   check('player save stores fusion members',saved.getCompound('cannedmonster:fusion').getList('Members',10).size()===1);
   check('player save stores origins',saved.getCompound('cannedmonster:fusion').getList('AttributeOrigins',10).size()===2);
   var empty=can('monster_can');p.setItemInHand(Hand.MAIN_HAND,empty);
   p.getMainHandItem().getItem().use(world,p,Hand.MAIN_HAND);
   p.releaseUsingItem();empty=p.getMainHandItem();
   check('empty can extracts',CC.filled(empty));
   empty.getItem().releaseUsing(empty,world,p,72000-1);
   check('extraction release does not repeat without a new press',PF.facets(p).size()===0&&CC.filled(empty));
   check('extracted can stores the cow',CC.filled(empty)&&CC.entity(empty).getString('id')==='minecraft:cow');
   check('extraction clears the player fusion',PF.facets(p).size()===0);
   check('player attributes return to their own values',Math.abs(Number(p.getMaxHealth())-20)<0.001);
   var throwCan=can('throwable_monster_can');CC.store(throwCan,CC.entity(empty),CC.name(empty));
   p.setItemInHand(Hand.MAIN_HAND,throwCan);p.getInventory().setItem(p.getInventory().selected,throwCan);
   p.absRotateTo(0,-90);
   var look=p.getLookAngle();check('filled throw aims straight up',Number(look.y)>0.99);
   throwCan.getItem().use(world,p,Hand.MAIN_HAND);
   throwCan.getItem().releaseUsing(throwCan,world,p,72000-25);p.stopUsingItem();
   phase='flightFilled';ticks=0;return;
  }
  if(phase==='flightFilled'){
   if(PF.facets(p).size()===1){
    check('self throw fuses the cow',PF.facets(p).get(0).getType()===ET.COW);
    var cans=allCans();
    check('self throw returns an emptied can',cans.length>0&&!CC.filled(cans[0]));
    check('player did not move during fusion',Math.abs(Number(p.getX())-startX)<0.01&&Math.abs(Number(p.getY())-startY)<0.01&&Math.abs(Number(p.getZ())-startZ)<0.01);
    cleanDrops();cleanCans();
    var emptyThrow=can('throwable_monster_can');p.setItemInHand(Hand.MAIN_HAND,emptyThrow);p.getInventory().setItem(p.getInventory().selected,emptyThrow);
    p.absRotateTo(0,-90);
    var look2=p.getLookAngle();check('empty throw aims straight up',Number(look2.y)>0.99);
    emptyThrow.getItem().use(world,p,Hand.MAIN_HAND);
    emptyThrow.getItem().releaseUsing(emptyThrow,world,p,72000-25);p.stopUsingItem();
    phase='flightEmpty';ticks=0;return;
   }
   if(ticks>240)check('self throw fuses the cow',false);
   return;
  }
  if(phase==='flightEmpty'){
   if(PF.facets(p).size()===0){
    var cans2=allCans();
    check('self throw extracts a filled can',cans2.length>0&&CC.filled(cans2[0])&&CC.entity(cans2[0]).getString('id')==='minecraft:cow');
    check('player remains in control after extraction',Math.abs(Number(p.getX())-startX)<0.01&&Math.abs(Number(p.getY())-startY)<0.01&&Math.abs(Number(p.getZ())-startZ)<0.01);
    phase='done';return;
   }
   if(ticks>240)check('self throw extracts a filled can',false);
   return;
  }
 }catch(error){phase='failed';failure=String(error);console.error('Player fusion regression: '+failure);}
});
JSON.stringify({phase:phase,checks:checks});
