var NativeF=Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var NativeET=Java.loadClass('net.minecraft.world.entity.EntityType');
var NativeList=Java.loadClass('java.util.List');
var NativeHand=Java.loadClass('net.minecraft.world.InteractionHand');
var NativeCC=Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var NativePF=Java.loadClass('cc.sighs.cannedmonster.fusion.PlayerFusion');
var nativePlayer=AutoJS.player(),nativeWorld=AutoJS.level();
if(!nativePlayer.isAlive()||nativePlayer.isRemoved())throw new Error('Respawn the test player before running this suite');
var nativeChecks=[],nativePhase='setup',nativeElapsed=0,nativeError=null,nativeMember=null,nativeBefore=0,nativeTentacle=0,nativeClearFireStart=0;
function nativeCheck(name,value){nativeChecks.push({name:name,pass:!!value});if(!value)throw new Error(name);}
function nativeClear(){while(!NativeF.facets(nativePlayer).isEmpty())NativeF.peel(nativePlayer);nativePlayer.setAirSupply(300);nativePlayer.clearFire();nativePlayer.setHealth(nativePlayer.getMaxHealth());nativePlayer.getFoodData().setFoodLevel(20);}
AutoJS.command('gamerule doMobSpawning false');AutoJS.command('gamerule mobGriefing false');AutoJS.command('gamerule naturalRegeneration false');AutoJS.command('gamemode survival @a');
ServerEvents.tick(function(event){
 if(nativePhase==='done'||nativePhase==='failed')return;
 try{
  nativeElapsed++;
  if(nativePhase==='setup'){
   nativeClear();
   AutoJS.command('fill 56 179 -18 66 179 -8 minecraft:stone');
   AutoJS.command('fill 57 180 -17 65 183 -9 minecraft:water');
   AutoJS.tp(nativePlayer,61,180,-13);
   nativeMember=NativeET.SQUID.create(nativeWorld);NativeF.attach(nativePlayer,NativeList.of(nativeMember));
   nativePlayer.setAirSupply(10);nativeBefore=Number(nativePlayer.getHealth());nativeTentacle=Number(nativeMember.tentacleMovement);
   nativeCheck('squid grants native underwater breathing',nativePlayer.canBreatheUnderwater());
   nativePhase='water';nativeElapsed=0;return;
  }
  if(nativePhase==='water'&&nativeElapsed>=400){
   nativeCheck('test actually submerged',nativePlayer.isInWater());
   nativeCheck('400 ticks without drowning',Number(nativePlayer.getHealth())===nativeBefore&&nativePlayer.getAirSupply()>=10);
   nativeCheck('native species aiStep advanced',Math.abs(Number(nativeMember.tentacleMovement)-nativeTentacle)>0.00001);
   nativeCheck('member is bound and unregistered',!nativeMember.isAddedToLevel()&&Java.loadClass('cc.sighs.cannedmonster.fusion.BodyLinks').same(nativeMember,nativePlayer));
   NativeF.peel(nativePlayer);nativePlayer.setAirSupply(10);nativeBefore=Number(nativePlayer.getHealth());
   nativeCheck('extraction revokes water breathing',!nativePlayer.canBreatheUnderwater());
   nativePhase='control';nativeElapsed=0;return;
  }
  if(nativePhase==='control'&&nativeElapsed>=55){
   nativeCheck('unfused control loses air and health',nativePlayer.getAirSupply()<10&&Number(nativePlayer.getHealth())<nativeBefore);
   AutoJS.command('fill 57 180 -17 65 184 -9 minecraft:air');AutoJS.tp(nativePlayer,61,180,-13);nativeClear();
   nativeMember=NativeET.MAGMA_CUBE.create(nativeWorld);NativeF.attach(nativePlayer,NativeList.of(nativeMember));nativeClearFireStart=Number(nativePlayer.getHealth());
   nativeCheck('magma grants native fire immunity',nativePlayer.fireImmune());
   nativePlayer.invulnerableTime=0;AutoJS.command('damage @a[limit=1] 4 minecraft:in_fire');
   nativePlayer.invulnerableTime=0;AutoJS.command('damage @a[limit=1] 4 minecraft:lava');
   nativeCheck('fire and lava commands do not damage immune body',Number(nativePlayer.getHealth())===nativeClearFireStart);
   AutoJS.command('fill 60 180 -14 62 181 -12 minecraft:lava');
   nativePhase='lava';nativeElapsed=0;return;
  }
  if(nativePhase==='lava'&&nativeElapsed>=80){
   nativeCheck('actual lava does not damage immune body',nativePlayer.isAlive()&&Number(nativePlayer.getHealth())===nativeClearFireStart);
   AutoJS.command('fill 60 180 -14 62 181 -12 minecraft:air');
   NativeF.peel(nativePlayer);nativePlayer.setAirSupply(300);nativePlayer.clearFire();nativePlayer.invulnerableTime=0;
   nativeCheck('extraction revokes fire immunity',!nativePlayer.fireImmune());
   nativeBefore=Number(nativePlayer.getHealth());AutoJS.command('damage @a[limit=1] 2 minecraft:in_fire');
   nativeCheck('unfused fire damage applies',Number(nativePlayer.getHealth())<nativeBefore);
   nativeClear();nativePhase='done';
  }
 }catch(error){nativeError=String(error);nativePhase='failed';console.error('Native player fusion regression: '+nativeError);}
});
JSON.stringify({phase:nativePhase,observing:'400 water ticks, 55 control ticks, 80 lava ticks'});
