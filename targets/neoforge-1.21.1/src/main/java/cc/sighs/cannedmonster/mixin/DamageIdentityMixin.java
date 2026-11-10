package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DamageSource.class)
public abstract class DamageIdentityMixin {
    @ModifyVariable(method = "<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Entity cannedmonster$direct(Entity entity) {
        return (Entity) BodyLinks.body(entity);
    }

    @ModifyVariable(method = "<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static Entity cannedmonster$causing(Entity entity) {
        return (Entity) BodyLinks.body(entity);
    }
}
