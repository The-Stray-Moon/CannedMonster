package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionRiding;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class RidingPhysicsMixin {
    @Inject(method = "travelRidden", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$ride(Player rider, Vec3 input, CallbackInfo callback) {
        if (FusionRiding.travel((LivingEntity) (Object) this, rider, input)) callback.cancel();
    }
}
