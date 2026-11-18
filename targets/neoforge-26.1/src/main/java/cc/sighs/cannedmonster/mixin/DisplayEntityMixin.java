package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.DisplayExecution;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class DisplayEntityMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$noMovement(MoverType type, Vec3 delta, CallbackInfo ci) {
        if (DisplayExecution.member((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$noRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        if (DisplayExecution.current() != null && DisplayExecution.member((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = {"setPos(DDD)V", "setPosRaw(DDD)V"}, at = @At("HEAD"), cancellable = true)
    private void cannedmonster$noPosition(CallbackInfo ci) {
        if (DisplayExecution.current() != null && DisplayExecution.member((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$noRide(Entity vehicle, boolean force, boolean sendEventAndTriggers,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (DisplayExecution.member((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
