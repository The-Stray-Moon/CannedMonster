var edges=[];
if(CC.filled(p.getItemInHand(Hand.MAIN_HAND))){p.getInventory().setItem(8,p.getItemInHand(Hand.MAIN_HAND));p.setItemInHand(Hand.MAIN_HAND,Java.loadClass('net.minecraft.world.item.ItemStack').EMPTY);}
function edge(name,value){edges.push({name:name,pass:!!value});if(!value)throw new Error(name);}
var tooLarge=spawn(ET.COW,-8,4),tooLargeCan=can('monster_can');
tooLarge.getPersistentData().putString('can_capacity_test',new Array(270001).join('x'));
edge('oversized entity rejected without removal',!CA.onMob(tooLargeCan,tooLarge,p)&&!tooLarge.isRemoved()&&!CC.filled(tooLargeCan));
tooLarge.discard();
var linked=spawn(ET.COW,-8,6),rider=spawn(ET.PIG,-8,6);rider.startRiding(linked,true);
edge('passenger group not silently lost',!CA.onMob(can('monster_can'),linked,p)&&!linked.isRemoved());rider.stopRiding();rider.discard();linked.discard();
var quick=can('throwable_monster_can');p.setItemInHand(Hand.MAIN_HAND,quick);quick.getItem().use(world,p,Hand.MAIN_HAND);p.releaseUsingItem();
edge('too short charge does not consume',!p.getItemInHand(Hand.MAIN_HAND).isEmpty());
var bad=can('monster_can');var DC=Java.loadClass('net.minecraft.core.component.DataComponents');var CD=Java.loadClass('net.minecraft.world.item.component.CustomData');var invalid=new Tag();invalid.putInt('Version',99);var root=new Tag();root.put(CC.KEY,invalid);bad.set(DC.CUSTOM_DATA,CD.of(root));
edge('invalid payload retained',!CA.release(bad,world,new Vec(px+4,floor,pz+5))&&CC.filled(bad));
edge('persistent large can still stored',CC.filled(p.getInventory().getItem(8))&&CC.entity(p.getInventory().getItem(8)).getCompound('cannedmonster:fusion').getList('Members',10).size()===3);
AutoJS.command('save-all flush');JSON.stringify(edges);
