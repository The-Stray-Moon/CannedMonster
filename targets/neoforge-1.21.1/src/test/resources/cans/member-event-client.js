var MemberViews=Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientAppearance');
var MemberSounds=Java.loadClass('cc.sighs.cannedmonster.fusion.visual.ClientMemberEvents');
var memberSamples=[],memberElapsed=0,memberObserved=null,memberClientResult=null;
AutoJS.background(true);
ClientEvents.tick(function(event){
 if(memberClientResult!==null)return;
 memberElapsed++;
 var members=MemberViews.members(AutoJS.player());
 if(!members.isEmpty()){
  memberObserved=members.get(0);
  var sounds=MemberSounds.sounds(memberObserved);
  memberSamples.push({tick:memberElapsed,clock:memberObserved.getClientSideAttackTime(),
   active:memberObserved.hasActiveAttackTarget(),sounds:sounds.size(),
   pitch:sounds.isEmpty()?0:sounds.get(0).getPitch(),volume:sounds.isEmpty()?0:sounds.get(0).getVolume()});
 }
 if(memberElapsed>=310){
  var active=memberSamples.filter(function(value){return value.active;});
  var maximumSound=0,maximumClock=0,minPitch=10,maxPitch=0;
  active.forEach(function(value){maximumSound=Math.max(maximumSound,value.sounds);maximumClock=Math.max(maximumClock,value.clock);if(value.sounds>0){minPitch=Math.min(minPitch,value.pitch);maxPitch=Math.max(maxPitch,value.pitch);}});
  memberClientResult={samples:memberSamples.length,active:active.length,maxSounds:maximumSound,
   maxClock:maximumClock,minPitch:minPitch,maxPitch:maxPitch,
   pass:active.length>60&&maximumSound===1&&maximumClock>=70&&minPitch<0.8&&maxPitch>1.1};
  if(!memberClientResult.pass)throw new Error(JSON.stringify(memberClientResult));
 }
});
JSON.stringify({started:true});
