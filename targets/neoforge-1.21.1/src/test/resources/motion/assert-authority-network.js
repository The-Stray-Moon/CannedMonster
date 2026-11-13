if(Math.abs(authorityBody.getX())>30) throw new Error('Client vehicle position was accepted');
var knockbackBefore=String(authorityBody.getDeltaMovement());
authorityBody.knockback(0.4,1,0);
if(Number(authorityBody.getDeltaMovement().x)>-0.2) throw new Error('Native knockback lost');
JSON.stringify({serverX:authorityBody.getX(),before:knockbackBefore,knockback:String(authorityBody.getDeltaMovement())});
