package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionTraits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingInvulnerableMixin {
    @Inject(method = "isInvulnerableTo", at = @At("RETURN"), cancellable = true)
    private void cannedmonster$immune(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            cir.setReturnValue(FusionTraits.invulnerable((Entity) (Object) this, source, false));
        }
    }
}
