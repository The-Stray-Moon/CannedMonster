var F=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var CC=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var CA=Java.loadClass('cc.sighs.cannedmonster.can.CanActions');
var ET=Java.loadClass('net.minecraft.world.entity.EntityType');
var Hand=Java.loadClass('net.minecraft.world.InteractionHand');
var Vec=Java.loadClass('net.minecraft.world.phys.Vec3');
var Comp=Java.loadClass('net.minecraft.network.chat.Component');
var world=AutoJS.level(),p=AutoJS.player(),floor=180,px=Math.floor(p.getX()),pz=Math.floor(p.getZ());
var savedCan=p.getInventory().getItem(8);
if(!CC.filled(savedCan)||CC.entity(savedCan).getCompound('cannedmonster:fusion').getList('Members',10).size()!==3)throw new Error('Inventory can lost stored fusion after restart');
var releaseAt=new Vec(px-1,floor,pz+3);
AutoJS.command('fill '+(px-4)+' '+(floor-1)+' '+pz+' '+(px+4)+' '+(floor-1)+' '+(pz+6)+' minecraft:stone');
if(!CA.release(savedCan,world,releaseAt)||CC.filled(savedCan))throw new Error('Persisted can could not release');
var body=null;
for(var e of world.getAllEntities())if(e.getType()===ET.PIG&&F.facets(e).size()===3)body=e;
if(!body)throw new Error('Missing restored fused body');
var types=[];for(var m of F.facets(body)){if(m.isAddedToLevel())throw new Error('Member became a world entity');types.push(String(m.getType()));}
if(types.join(',')!=='minecraft:cow,minecraft:rabbit,minecraft:sheep')throw new Error('Member order lost '+types);
p.setItemInHand(Hand.MAIN_HAND,savedCan);p.getInventory().setItem(8,Java.loadClass('net.minecraft.world.item.ItemStack').EMPTY);
if(!p.interactOn(body,Hand.MAIN_HAND).consumesAction()||!CC.filled(p.getItemInHand(Hand.MAIN_HAND)))throw new Error('Persisted fusion failed recapture');
var Tag=Java.loadClass('net.minecraft.nbt.CompoundTag');
function can(name){return AutoJS.stack('cannedmonster:'+name,1);}
function spawn(type,x,z){var m=type.create(world);m.setPos(px+x,floor,pz+z);m.setNoAi(true);world.addFreshEntity(m);return m;}
JSON.stringify({restartRestored:true,body:'minecraft:pig',members:types,recaptured:true});
