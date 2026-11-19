var CoreRuntime = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var CoreAttributes = Java.loadClass('cc.sighs.cannedmonster.fusion.AttributeAverages');
var CoreStorage = Java.loadClass('cc.sighs.cannedmonster.platform.EntityStorage');
var CoreRegistries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
var CoreResourceId = Java.loadClass('net.minecraft.resources.ResourceLocation');
var CoreCan = Java.loadClass('cc.sighs.cannedmonster.can.CanContents');
var CoreActions = Java.loadClass('cc.sighs.cannedmonster.can.CanActions');
var CorePlayer = Java.loadClass('cc.sighs.cannedmonster.fusion.PlayerFusion');
var CoreThrown = Java.loadClass('cc.sighs.cannedmonster.can.ThrownCan');
var CoreLists = Java.loadClass('java.util.List');
var CoreTag = Java.loadClass('net.minecraft.nbt.CompoundTag');
var CoreVec = Java.loadClass('net.minecraft.world.phys.Vec3');
var CoreBox = Java.loadClass('net.minecraft.world.phys.AABB');
var CoreItemEntity = Java.loadClass('net.minecraft.world.entity.item.ItemEntity');

function createCoreMob(level, id) {
    return CoreRegistries.ENTITY_TYPE.get(CoreResourceId.tryParse(id)).create(level);
}

function coreCheck(name, condition) {
    global.sharedCoreResult.checks.push({name: name, pass: !!condition});
    if (!condition) throw new Error(name);
}

function startSharedCoreRegression() {
    if (global.sharedCoreProbe) throw new Error('Previous shared-core probe must be finished first');
    var player = AutoJS.player();
    if (!player || !CoreRuntime.facets(player).isEmpty()) throw new Error('An unfused player in the verification world is required');
    var level = player.level;
    var state = CoreAttributes.snapshot(player);
    var gravity = player.isNoGravity();
    global.sharedCoreResult = {checks: [], phase: 'setup'};
    var body = createCoreMob(level, 'minecraft:cow');
    var member = createCoreMob(level, 'minecraft:sheep');
    var restored = null;
    var playerMember = null;
    try {
        CoreRuntime.attach(body, CoreLists.of(member));
        coreCheck('shared attribute mean', Math.abs(body.getMaxHealth() - 9) < 0.00001);
        coreCheck('member remains outside world', !CoreStorage.added(member));
        var saved = new CoreTag();
        CoreRuntime.save(body, saved);
        CoreRuntime.detach(body);
        CoreRuntime.load(body, saved);
        coreCheck('fusion save/load retains member', CoreRuntime.facets(body).toArray().length === 1);
        coreCheck('save/load retains species', String(AutoJS.id(CoreRuntime.facets(body).toArray()[0].getType())) === 'minecraft:sheep');
        var large = AutoJS.stack('cannedmonster:large_monster_can', 1);
        coreCheck('large can captures complete fusion', CoreActions.onMob(large, body, player));
        coreCheck('large can retains count', CoreCan.count(large) === 2);
        restored = CoreActions.restore(large, level);
        coreCheck('can restore retains ordered fusion', restored !== null && CoreRuntime.facets(restored).toArray().length === 1);
        CoreRuntime.detach(restored);
        restored = null;
        playerMember = createCoreMob(level, 'minecraft:cow');
        var base = player.getAttributeBaseValue(Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes').MOVEMENT_SPEED);
        CoreRuntime.attach(player, CoreLists.of(playerMember));
        coreCheck('player shares fusion session', CoreRuntime.facets(player).toArray().length === 1);
        coreCheck('player base speed unchanged', Math.abs(player.getAttributeBaseValue(Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes').MOVEMENT_SPEED) - base) < 0.000000001);
        CoreRuntime.detach(player);
        coreCheck('player session detach clears membership', CoreRuntime.facets(player).isEmpty());
        coreCheck('oversized NBT rejected before capture', oversizedCoreCan(level, player));
        var target = createCoreMob(level, 'minecraft:cow');
        target.setNoAi(true);
        target.setNoGravity(true);
        CoreStorage.position(target, new CoreVec(player.getX() + 5, player.getY() + 4, player.getZ()), 0);
        if (!level.addFreshEntity(target)) throw new Error('Probe target spawn rejected');
        var thrown = new CoreThrown(AutoJS.entityType('cannedmonster:thrown_can'), level);
        thrown.setOwner(player);
        Java.loadClass('cc.sighs.cannedmonster.platform.ProjectileAccess').position(thrown, new CoreVec(target.getX(), target.getY() + 4, target.getZ()));
        thrown.setItem(AutoJS.stack('cannedmonster:throwable_monster_can', 1));
        thrown.setDeltaMovement(new CoreVec(0, -0.3, 0));
        if (!level.addFreshEntity(thrown)) { target.discard(); throw new Error('Probe projectile spawn rejected'); }
        global.sharedCoreProbe = {level: level, target: target, thrown: thrown, tick: thrown.tickCount,
            x: target.getX(), y: target.getY(), z: target.getZ()};
        global.sharedCoreResult.phase = 'waiting for real projectile ticks';
        return JSON.stringify(global.sharedCoreResult);
    } finally {
        if (restored !== null) CoreRuntime.detach(restored);
        CoreRuntime.detach(body);
        CoreRuntime.detach(player);
        state.restore(player);
        player.setNoGravity(gravity);
    }
}

function oversizedCoreCan(level, player) {
    var mob = createCoreMob(level, 'minecraft:cow');
    var tag = CoreStorage.save(mob);
    tag.putString('OversizedProbe', 'x'.repeat(70000));
    var stack = AutoJS.stack('cannedmonster:monster_can', 1);
    try { CoreCan.store(stack, tag, 'Cow', 1); return false; }
    catch (expected) { return !CoreCan.filled(stack) && !mob.isRemoved(); }
}

function finishSharedCoreRegression() {
    var probe = global.sharedCoreProbe;
    if (!probe) throw new Error('No shared-core probe');
    var drops = probe.level.getEntitiesOfClass(CoreItemEntity, new CoreBox(probe.x - 4, probe.y - 12, probe.z - 4, probe.x + 4, probe.y + 8, probe.z + 4));
    var matched = [];
    try {
        coreCheck('projectile advanced real ticks', probe.thrown.tickCount > probe.tick);
        coreCheck('real collision captured target', probe.target.isRemoved());
        for (var i = 0; i < drops.size(); i++) {
            var drop = drops.get(i);
            if (CoreCan.filled(drop.getItem()) && String(CoreStorage.identity(CoreCan.entity(drop.getItem()))) === String(probe.target.getUUID())) matched.push(drop);
        }
        coreCheck('one filled can returned', matched.length === 1);
        coreCheck('projectile removed after return', probe.thrown.isRemoved());
        global.sharedCoreResult.phase = 'passed';
        return JSON.stringify(global.sharedCoreResult);
    } finally {
        matched.forEach(function(drop) { drop.discard(); });
        if (!probe.target.isRemoved()) probe.target.discard();
        if (!probe.thrown.isRemoved()) probe.thrown.discard();
        global.sharedCoreProbe = null;
    }
}

'Loaded shared-core regression; call startSharedCoreRegression(), allow real world ticks, then finishSharedCoreRegression()';
