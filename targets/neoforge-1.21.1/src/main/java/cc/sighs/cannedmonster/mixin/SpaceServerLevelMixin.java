package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.platform.SpacePlatform;
import cc.sighs.cannedmonster.space.SpaceDomain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerLevel.class)
public abstract class SpaceServerLevelMixin {
    @Shadow @Final
    List<ServerPlayer> players;

    @Unique
    private LevelEntityGetter<Entity> cannedmonster$rawEntities;
    @Unique
    private LevelEntityGetter<Entity> cannedmonster$filteredEntities;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cannedmonster$resetDomain(java.util.function.BooleanSupplier supplier, CallbackInfo ci) {
        SpaceDomain.reset();
    }

    @Inject(method = "players", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$spacePlayers(CallbackInfoReturnable<List<ServerPlayer>> cir) {
        List<ServerPlayer> filtered = new ArrayList<>();
        boolean active = SpaceDomain.active((ServerLevel) (Object) this);
        for (ServerPlayer player : players) {
            if (active ? SpaceDomain.sameSpace(player) : !cc.sighs.cannedmonster.space.SpaceManager.isInside(player)) {
                filtered.add(player);
            }
        }
        cir.setReturnValue(filtered);
    }

    @Inject(method = "getEntities()Lnet/minecraft/world/level/entity/LevelEntityGetter;", at = @At("RETURN"), cancellable = true)
    private void cannedmonster$filterEntities(CallbackInfoReturnable<LevelEntityGetter<Entity>> cir) {
        LevelEntityGetter<Entity> raw = cir.getReturnValue();
        if (raw == null) return;
        if (cannedmonster$rawEntities != raw) {
            cannedmonster$rawEntities = raw;
            cannedmonster$filteredEntities = SpacePlatform.filtered(raw);
        }
        cir.setReturnValue(cannedmonster$filteredEntities);
    }

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$spaceAdd(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceDomain.frame().active() && !SpaceDomain.allowed(entity)) cir.setReturnValue(false);
    }

    /** Anything spawned inside the room (drops, xp, arrows, babies) becomes space-owned. */
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void cannedmonster$deriveAdded(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) cc.sighs.cannedmonster.space.SpaceManager.derive(entity);
    }
}
