var F=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var CC=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var CA=Java.loadClass('cc.sighs.cannedmonster.can.CanActions');
var ET=Java.loadClass('net.minecraft.world.entity.EntityType');
var Hand=Java.loadClass('net.minecraft.world.InteractionHand');
var Comp=Java.loadClass('net.minecraft.network.chat.Component');
var Tag=Java.loadClass('net.minecraft.nbt.CompoundTag');
var Vec=Java.loadClass('net.minecraft.world.phys.Vec3');
var List=Java.loadClass('java.util.List');
var world=AutoJS.level(),p=AutoJS.player();
var px=Math.floor(p.getX()),pz=Math.floor(p.getZ()),floor=180;
AutoJS.command('gamerule doMobSpawning false');
AutoJS.command('gamerule mobGriefing false');
AutoJS.command('time set midnight');
AutoJS.command('fill '+(px-14)+' '+(floor-1)+' '+(pz-14)+' '+(px+14)+' '+(floor-1)+' '+(pz+14)+' minecraft:stone');
AutoJS.tp(p,px,floor,pz);
var checks=[];
function check(name,value){checks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
function can(name){return AutoJS.stack('cannedmonster:'+name,1);}
function spawn(type,x,z){var m=type.create(world);m.setPos(px+x,floor,pz+z);m.setNoAi(true);world.addFreshEntity(m);return m;}
function facet(type){return type.create(world);}
function interact(stack,m){p.setItemInHand(Hand.MAIN_HAND,stack);return p.interactOn(m,Hand.MAIN_HAND);}
function near(value,expected){return Math.abs(Number(value)-expected)<0.001;}
function storedType(stack){return CC.entity(stack).getString('id');}

// Two-member fusion: cow is the top member, rabbit is the body.
var rabbit=spawn(ET.RABBIT,3,0);
var cowName='Peel Cow '+Date.now();
var cow=facet(ET.COW);cow.setCustomName(Comp.literal(cowName));F.attach(rabbit,List.of(cow));
var rabbitMax=rabbit.getMaxHealth();
check('two-member fusion starts at averaged health',near(rabbitMax,(Number(ET.RABBIT.create(world).getMaxHealth())+10)/2));
var small=can('monster_can');
var sword=AutoJS.stack('minecraft:diamond_sword',1);
rabbit.setItemInHand(Hand.MAIN_HAND,sword);
check('peel consumes the action',interact(small,rabbit).consumesAction());
small=p.getItemInHand(Hand.MAIN_HAND);
check('peel fills the can',CC.filled(small));
check('peel stores the top member',storedType(small)==='minecraft:cow');
check('stored member has its own UUID',String(CC.entity(small).getUUID('UUID'))!==String(rabbit.saveWithoutId(new Tag()).getUUID('UUID')));
check('body survives the peel',!rabbit.isRemoved()&&rabbit.isAddedToLevel());
check('fusion dissolves after the last member',F.facets(rabbit).size()===0);
check('body attributes return to its own values',near(rabbit.getMaxHealth(),Number(ET.RABBIT.create(world).getMaxHealth())));
check('peeled NBT does not copy body equipment',CC.entity(small).getList('HandItems',10).getCompound(0).isEmpty());
check('body keeps its own equipment',rabbit.getMainHandItem().getItem()===sword.getItem());
check('peeled can releases',CA.release(small,world,new Vec(px+6,floor,pz))&&!CC.filled(small));
var released=null;for(var e of world.getAllEntities()){if(e.getType()===ET.COW&&e.getName().getString()===cowName)released=e;}
check('released mob is the peeled cow',released!==null);

// Three-member fusion: order is cow, sheep, pig body.
var pig=spawn(ET.PIG,-3,0);
var top=facet(ET.COW),middle=facet(ET.SHEEP);F.attach(pig,List.of(top,middle));
var pigMax=Number(ET.PIG.create(world).getMaxHealth());
check('three-member average applies',near(pig.getMaxHealth(),(10+8+pigMax)/3));
var first=can('monster_can');
check('first peel succeeds',interact(first,pig).consumesAction());
first=p.getItemInHand(Hand.MAIN_HAND);
check('first peel takes the top member',storedType(first)==='minecraft:cow');
check('remaining order keeps sheep above the pig body',F.facets(pig).size()===1&&F.facets(pig).get(0).getType()===ET.SHEEP);
check('remaining average uses only remaining members',near(pig.getMaxHealth(),(8+pigMax)/2));
check('body type is unchanged',pig.getType()===ET.PIG);
var saved=pig.saveWithoutId(new Tag());
check('save after peel stores remaining members',saved.getCompound('cannedmonster:fusion').getList('Members',10).size()===1);
check('save after peel stores matching origins',saved.getCompound('cannedmonster:fusion').getList('AttributeOrigins',10).size()===2);
var second=can('monster_can');
check('second peel succeeds',interact(second,pig).consumesAction());
second=p.getItemInHand(Hand.MAIN_HAND);
check('second peel takes the next member',storedType(second)==='minecraft:sheep');
check('body alone after peeling all members',F.facets(pig).size()===0);
check('body own attributes restored again',near(pig.getMaxHealth(),pigMax));
check('save on a plain body has no fusion data',!pig.saveWithoutId(new Tag()).contains('cannedmonster:fusion'));

// Large cans still capture the whole fusion, and throwable cans still do not peel by hand.
var body=spawn(ET.RABBIT,0,4),member=facet(ET.COW);F.attach(body,List.of(member));
var large=can('large_monster_can');
check('large can captures the whole fusion',interact(large,body).consumesAction()&&body.isRemoved());
large=p.getItemInHand(Hand.MAIN_HAND);
check('large can stores the fusion tag',CC.filled(large)&&CC.entity(large).contains('cannedmonster:fusion'));
var fusedBody=spawn(ET.RABBIT,0,7),fusedMember=facet(ET.COW);F.attach(fusedBody,List.of(fusedMember));
var throwable=can('throwable_monster_can');
check('throwable can does not peel by hand',!interact(throwable,fusedBody).consumesAction()&&!CC.filled(throwable)&&F.facets(fusedBody).size()===1);

// Oversized member data must leave the fusion intact when the can cannot store it.
var rollbackBody=spawn(ET.RABBIT,-6,7),huge=facet(ET.COW);
huge.setCustomName(Comp.literal(Array(23001).join('一')));
F.attach(rollbackBody,List.of(huge));
var rollbackCan=can('monster_can');
check('oversized peel reports failure',!interact(rollbackCan,rollbackBody).consumesAction());
rollbackCan=p.getItemInHand(Hand.MAIN_HAND);
check('oversized peel leaves the can empty',!CC.filled(rollbackCan));
check('oversized peel restores the fusion',F.facets(rollbackBody).size()===1&&F.facets(rollbackBody).get(0).getType()===ET.COW);
check('oversized peel keeps the body registered',!rollbackBody.isRemoved()&&rollbackBody.isAddedToLevel());

JSON.stringify({passed:checks.length,checks:checks});
