package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionTraits;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FusionPhysiologyMixin {
    @Inject(method = "canBreatheUnderwater", at = @At("RETURN"), cancellable = true)
    private void cannedmonster$underwater(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(FusionTraits.underwater((LivingEntity) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "canBeAffected", at = @At("RETURN"), cancellable = true)
    private void cannedmonster$effects(net.minecraft.world.effect.MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(FusionTraits.affected((LivingEntity) (Object) this, effect, cir.getReturnValue()));
    }

    public boolean canDrownInFluidType(FluidType type) {
        LivingEntity entity = (LivingEntity) (Object) this;
        boolean original = type == ForgeMod.WATER_TYPE.get() ? !entity.canBreatheUnderwater() : type.canDrownIn(entity);
        return FusionTraits.canDrown(entity, type, original);
    }
}
