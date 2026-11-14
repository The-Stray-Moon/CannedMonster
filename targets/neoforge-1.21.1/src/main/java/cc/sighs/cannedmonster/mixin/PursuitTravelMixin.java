package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionPursuit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class PursuitTravelMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"))
    private void cannedmonster$travel(LivingEntity entity, Vec3 input) {
        if (!FusionPursuit.travel(entity, input)) entity.travel(input);
    }
}
