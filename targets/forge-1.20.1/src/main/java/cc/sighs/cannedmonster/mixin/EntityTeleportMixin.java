package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionTraits;
import cc.sighs.cannedmonster.fusion.TeleportCalls;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityTeleportMixin {
    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"))
    private void cannedmonster$begin(double x, double y, double z, CallbackInfo ci) {
        TeleportCalls.begin((Entity) (Object) this);
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("RETURN"))
    private void cannedmonster$end(double x, double y, double z, CallbackInfo ci) {
        TeleportCalls.end((Entity) (Object) this);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
            at = @At("HEAD"), cancellable = true)
    private void cannedmonster$dimensionBegin(ServerLevel level, double x, double y, double z,
            Set<RelativeMovement> relative, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (!FusionTraits.playerMember(entity)) return;
        if (level != entity.level()) {
            cir.setReturnValue(false);
            return;
        }
        TeleportCalls.begin(entity);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
            at = @At("RETURN"))
    private void cannedmonster$dimensionEnd(ServerLevel level, double x, double y, double z,
            Set<RelativeMovement> relative, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        TeleportCalls.end((Entity) (Object) this);
    }
}
