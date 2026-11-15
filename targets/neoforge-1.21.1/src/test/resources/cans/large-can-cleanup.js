var CF=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var CC=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var CA=Java.loadClass('cc.sighs.cannedmonster.can.CanActions');
var CT=Java.loadClass('net.minecraft.world.entity.EntityType');
var CH=Java.loadClass('net.minecraft.world.InteractionHand');
var CV=Java.loadClass('net.minecraft.world.phys.Vec3');
var cp=AutoJS.player(),cw=AutoJS.level(),cleanupChecks=[];
function cleanupCheck(name,value){cleanupChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
if(typeof largeSaved==='undefined'||typeof largeHeld==='undefined')throw new Error('Run large-can-regression.js first');
var cleanupCan=cp.getMainHandItem();
cleanupCheck('filled large can is in hand',CC.filled(cleanupCan));
cleanupCheck('packed can has three mobs',CC.count(cleanupCan)===3);
var cleanupReleased=CA.release(cleanupCan,cw,new CV(cp.getX()+3,cp.getY()+1,cp.getZ()));
cleanupCheck('large can releases',cleanupReleased);
cleanupCheck('released can is empty',!CC.filled(cleanupCan));
var cleanupBody=null,cleanupNear=cw.getEntities(cp,cp.getBoundingBox().inflate(8));
for(var i=0;i<cleanupNear.size();i++){
 var entity=cleanupNear.get(i);
 if(entity.getType()===CT.SHEEP&&entity.isAddedToLevel()){cleanupBody=entity;break;}
}
cleanupCheck('released body exists',cleanupBody!==null);
var cleanupTypes=[];var cleanupMembers=CF.facets(cleanupBody);
for(var i=0;i<cleanupMembers.size();i++)cleanupTypes.push(String(cleanupMembers.get(i).getType()));
cleanupCheck('released body is the last member',String(cleanupBody.getType())==='minecraft:sheep');
cleanupCheck('remaining members keep order',cleanupTypes.join(',')==='minecraft:cow,minecraft:pig');
CF.detach(cleanupBody);cleanupBody.discard();
cp.setItemInHand(CH.MAIN_HAND,largeHeld);
CF.load(cp,largeSaved);
JSON.stringify({checks:cleanupChecks,saved:AutoJS.server().saveEverything(false,true,true),players:CF.facets(cp).size()});
