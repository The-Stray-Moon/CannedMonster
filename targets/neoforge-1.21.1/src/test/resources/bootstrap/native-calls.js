if (String(AutoJS.server().getWorldData().getLevelName()) !== 'Fusion motion verification') throw new Error('Wrong verification world');
var callsF = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var callsET = Java.loadClass('net.minecraft.world.entity.EntityType');
var callsList = Java.loadClass('java.util.List');
var callsMode = Java.loadClass('net.minecraft.world.level.GameType');
var callsPlayer = AutoJS.player();
if (!callsPlayer.isAlive()) throw new Error('Requires living test player');
var callsModeBefore = callsPlayer.gameMode.getGameModeForPlayer();
var callsHealthBefore = callsPlayer.getHealth();
var callsNaturalRegen = AutoJS.server().getGameRules().getBoolean(Java.loadClass('net.minecraft.world.level.GameRules').RULE_NATURAL_REGENERATION);
AutoJS.command('gamerule naturalRegeneration false');
var callsMember = callsET.SQUID.create(AutoJS.level());
callsF.prepend(callsPlayer, callsMember);
callsPlayer.setGameMode(callsMode.SURVIVAL);
callsPlayer.setHealth(callsPlayer.getMaxHealth());
callsPlayer.invulnerableTime = 0;
var callsHealth = Number(callsPlayer.getHealth());
AutoJS.command('damage ' + String(callsPlayer.uuid) + ' 2 minecraft:generic');
var callsDamage = callsHealth - Number(callsPlayer.getHealth());
if (Math.abs(callsDamage - 2) > 0.00001) throw new Error('Public hurt must deduct exactly two health, got ' + callsDamage);
callsPlayer.setGameMode(callsMode.CREATIVE);
var callsStart = Number(callsMember.tentacleMovement);
var callsTicks = 0;
var callsResult = {done:false};
ServerEvents.tick(function(event) {
    if (callsResult.done || ++callsTicks < 100) return;
    callsResult = {done:true, damage:callsDamage, nativeAiAdvanced:Math.abs(Number(callsMember.tentacleMovement)-callsStart)>0.00001,
        bound:Java.loadClass('cc.sighs.cannedmonster.fusion.BodyLinks').same(callsMember,callsPlayer),
        memberUnregistered:!callsMember.isAddedToLevel(), playerAlive:callsPlayer.isAlive()};
    callsF.peel(callsPlayer);
    callsPlayer.setHealth(callsHealthBefore);
    callsPlayer.setGameMode(callsModeBefore);
    AutoJS.command('gamerule naturalRegeneration ' + callsNaturalRegen);
});
JSON.stringify({started:true,damage:callsDamage});
