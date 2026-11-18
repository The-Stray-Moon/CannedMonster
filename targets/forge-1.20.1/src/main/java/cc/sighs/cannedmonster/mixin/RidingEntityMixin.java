package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionRiding;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class RidingEntityMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$move(MoverType type, Vec3 delta, CallbackInfo callback) {
        if (FusionRiding.move((Entity) (Object) this, type, delta)) callback.cancel();
    }

    @Inject(method = "isControlledByLocalInstance", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$authority(CallbackInfoReturnable<Boolean> callback) {
        Entity self = (Entity) (Object) this;
        if (FusionRiding.authoritative(self) && (self.level().isClientSide
                || self.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player)) {
            callback.setReturnValue(!self.level().isClientSide);
        }
    }
}
