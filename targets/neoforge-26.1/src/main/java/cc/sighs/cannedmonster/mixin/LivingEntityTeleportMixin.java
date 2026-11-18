package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.TeleportCalls;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTeleportMixin {
    @Inject(method = "randomTeleport(DDDZ)Z", at = @At("HEAD"))
    private void cannedmonster$begin(double x, double y, double z, boolean particles, CallbackInfoReturnable<Boolean> cir) {
        TeleportCalls.begin((Entity) (Object) this);
    }

    @Inject(method = "randomTeleport(DDDZ)Z", at = @At("RETURN"))
    private void cannedmonster$end(double x, double y, double z, boolean particles, CallbackInfoReturnable<Boolean> cir) {
        TeleportCalls.end((Entity) (Object) this);
    }
}
