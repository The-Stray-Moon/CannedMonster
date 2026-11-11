var F=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var CC=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var CA=Java.loadClass('cc.sighs.cannedmonster.can.CanActions');
var ET=Java.loadClass('net.minecraft.world.entity.EntityType');
var R=Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var RL=Java.loadClass('net.minecraft.resources.ResourceLocation');
var Hand=Java.loadClass('net.minecraft.world.InteractionHand');
var Comp=Java.loadClass('net.minecraft.network.chat.Component');
var Tag=Java.loadClass('net.minecraft.nbt.CompoundTag');
var Vec=Java.loadClass('net.minecraft.world.phys.Vec3');
var Hit=Java.loadClass('net.minecraft.world.phys.BlockHitResult');
var Dir=Java.loadClass('net.minecraft.core.Direction');
var List=Java.loadClass('java.util.List');
var world=AutoJS.level(),p=AutoJS.player();
var px=Math.floor(p.getX()),pz=Math.floor(p.getZ()),floor=180;
AutoJS.command('gamerule doMobSpawning false');
AutoJS.command('gamerule mobGriefing false');
AutoJS.command('time set midnight');
AutoJS.command('fill '+(px-12)+' '+(floor-1)+' '+(pz-12)+' '+(px+12)+' '+(floor-1)+' '+(pz+12)+' minecraft:stone');
AutoJS.tp(p,px,floor,pz);
var checks=[];
function check(name,value){checks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
function can(name){return AutoJS.stack('cannedmonster:'+name,1);}
function spawn(type,x,z){var m=type.create(world);m.setPos(px+x,floor,pz+z);m.setNoAi(true);world.addFreshEntity(m);return m;}
function interact(stack,m){p.setItemInHand(Hand.MAIN_HAND,stack);return p.interactOn(m,Hand.MAIN_HAND);}
var names=['iron_can','monster_can','throwable_monster_can','large_iron_can','large_monster_can','throwable_large_monster_can','spatial_monster_can','random_monster_can'];
for(var name of names){check('registered '+name,R.ITEM.containsKey(RL.parse('cannedmonster:'+name)));var recipe=world.getRecipeManager().byKey(RL.parse('cannedmonster:'+name));check('recipe '+name,name==='spatial_monster_can'?!recipe.isPresent():recipe.isPresent());}
var small=can('monster_can');
var cow=spawn(ET.COW,3,0);cow.setCustomName(Comp.literal('Can Capture Cow'));cow.setHealth(7);var cowId=cow.getId();
check('capture consumes action',interact(small,cow).consumesAction());
small=p.getItemInHand(Hand.MAIN_HAND);
check('capture removed original',cow.isRemoved()&&world.getEntity(cowId)===null);check('stored content',CC.filled(small)&&CC.name(small)==='Can Capture Cow');
var savedStack=small.save(world.registryAccess());
var parsed=Java.loadClass('net.minecraft.world.item.ItemStack').parse(world.registryAccess(),savedStack).get();
check('item serialization retains entity',CC.entity(parsed).equals(CC.entity(small)));
var pos=AutoJS.pos(px+6,floor-1,pz);
var hit=new Hit(new Vec(px+6.5,floor,pz+0.5),Dir.UP,pos,false);
p.setItemInHand(Hand.MAIN_HAND,small);
check('block release succeeds',p.gameMode.useItemOn(p,world,small,Hand.MAIN_HAND,hit).consumesAction());
check('release clears can',!CC.filled(small));
var released=null;for(var e of world.getAllEntities()){if(e.getType()===ET.COW&&e.getName().getString()==='Can Capture Cow')released=e;}
check('release preserves name and health',released!==null&&released.getHealth()===7);
check('recapture works',interact(small,released).consumesAction());small=p.getItemInHand(Hand.MAIN_HAND);
var rabbit=spawn(ET.RABBIT,-3,0);
check('filled can fuses',interact(small,rabbit).consumesAction());
check('fusion empties small can',!CC.filled(p.getItemInHand(Hand.MAIN_HAND)));check('fusion member cow',F.facets(rabbit).size()===1&&F.facets(rabbit).get(0).getType()===ET.COW);
check('small cannot capture fusion',!interact(p.getItemInHand(Hand.MAIN_HAND),rabbit).consumesAction()&&!rabbit.isRemoved());
var big=can('large_monster_can');check('large captures fusion',interact(big,rabbit).consumesAction());big=p.getItemInHand(Hand.MAIN_HAND);check('large persists fused NBT',CC.entity(big).contains('cannedmonster:fusion'));
check('large release fusion',CA.release(big,world,new Vec(px-6,floor,pz)));
var fused=null;for(var e of world.getAllEntities()){if(e.getType()===ET.RABBIT&&F.facets(e).size()===1)fused=e;}
check('released fusion restores members',fused!==null);
check('large recapture',interact(big,fused).consumesAction());big=p.getItemInHand(Hand.MAIN_HAND);
var existing=spawn(ET.PIG,-3,4),extra=ET.SHEEP.create(world);F.attach(existing,List.of(extra));
check('large fuses into existing fusion',interact(big,existing).consumesAction());
check('flattened ordered members',F.facets(existing).size()===3&&F.facets(existing).get(0).getType()===ET.COW&&F.facets(existing).get(1).getType()===ET.RABBIT&&F.facets(existing).get(2).getType()===ET.SHEEP);
var a=spawn(ET.ALLAY,0,4),empty=can('monster_can');check('can captures allay instead of allay taking can',interact(empty,a).consumesAction()&&a.isRemoved()&&CC.filled(p.getItemInHand(Hand.MAIN_HAND)));
var blocking=spawn(ET.COW,0,7);var filled=p.getItemInHand(Hand.MAIN_HAND);
AutoJS.command('fill '+(px+8)+' '+floor+' '+(pz+8)+' '+(px+10)+' '+(floor+3)+' '+(pz+10)+' minecraft:stone');
check('blocked release retains contents',!CA.release(filled,world,new Vec(px+9,floor,pz+9))&&CC.filled(filled));
var throwStack=can('throwable_monster_can');check('throwable does not interact directly',!interact(throwStack,blocking).consumesAction()&&!blocking.isRemoved()&&!CC.filled(throwStack));
var randomStack=can('random_monster_can');check('random no direct storage',!interact(randomStack,blocking).consumesAction()&&!CC.filled(randomStack));
var space=can('spatial_monster_can');check('spatial no behavior',!interact(space,blocking).consumesAction()&&!blocking.isRemoved());
var finalStored=can('large_monster_can');check('prepare persistent can',interact(finalStored,existing).consumesAction());p.setItemInHand(Hand.MAIN_HAND,p.getItemInHand(Hand.MAIN_HAND));
AutoJS.command('save-all flush');
JSON.stringify({passed:checks.length,checks:checks,storedMembers:CC.entity(p.getItemInHand(Hand.MAIN_HAND)).getCompound('cannedmonster:fusion').getList('Members',10).size()});
