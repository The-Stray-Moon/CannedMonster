package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceClientView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The client space room has no weather; real rain/thunder never bleeds into it. */
@Mixin(Level.class)
public abstract class SpaceClientLevelMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$roomRain(float partial, CallbackInfoReturnable<Float> cir) {
        if (SpaceClientView.active() && (Object) this instanceof ClientLevel) cir.setReturnValue(0.0F);
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$roomThunder(float partial, CallbackInfoReturnable<Float> cir) {
        if (SpaceClientView.active() && (Object) this instanceof ClientLevel) cir.setReturnValue(0.0F);
    }
}
