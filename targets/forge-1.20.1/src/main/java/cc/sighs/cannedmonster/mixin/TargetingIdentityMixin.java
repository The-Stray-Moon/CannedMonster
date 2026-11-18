package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetingConditions.class)
public abstract class TargetingIdentityMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$excludeSelf(LivingEntity source, LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
        if (BodyLinks.same(source, target)) {
            callback.setReturnValue(false);
        }
    }
}
