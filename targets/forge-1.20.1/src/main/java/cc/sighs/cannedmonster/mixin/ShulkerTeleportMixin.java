package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.TeleportCalls;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shulker.class)
public abstract class ShulkerTeleportMixin {
    @Inject(method = "teleportSomewhere()Z", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$begin(CallbackInfoReturnable<Boolean> cir) {
        if (TeleportCalls.begin((Entity) (Object) this) == TeleportCalls.Status.DENIED) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "teleportSomewhere()Z", at = @At("RETURN"))
    private void cannedmonster$end(CallbackInfoReturnable<Boolean> cir) {
        TeleportCalls.end((Entity) (Object) this);
    }
}
