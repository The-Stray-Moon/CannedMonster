package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Projectile.class)
public abstract class ProjectileIdentityMixin {
    @ModifyVariable(method = "setOwner", at = @At("HEAD"), argsOnly = true)
    private Entity cannedmonster$owner(Entity owner) {
        return (Entity) BodyLinks.body(owner);
    }
}
