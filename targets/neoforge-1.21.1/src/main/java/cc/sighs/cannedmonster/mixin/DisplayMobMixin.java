package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.DisplayExecution;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class DisplayMobMixin {
    @Inject(method = {"tick", "aiStep"}, at = @At("HEAD"), cancellable = true)
    private void cannedmonster$displayBoundary(CallbackInfo ci) {
        if (DisplayExecution.member((Mob) (Object) this)) ci.cancel();
    }
}
