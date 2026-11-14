package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.AttributeAverages;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AttributeInstance.class)
public abstract class FusionAttributeWriteMixin {
    @Inject(method = "setBaseValue", at = @At("RETURN"))
    private void cannedmonster$base(double value, CallbackInfo ci) {
        AttributeAverages.baseChanged((AttributeInstance) (Object) this);
    }
}
