package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionRuntime;
import cc.sighs.cannedmonster.fusion.RemovalCalls;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class SpawnInheritanceMixin {
    @ModifyVariable(method = "addEntity", at = @At("HEAD"), argsOnly = true)
    private Entity cannedmonster$inherit(Entity entity) {
        return RemovalCalls.inherit(entity);
    }

    @Inject(method = "addEntity", at = @At("RETURN"))
    private void cannedmonster$rejected(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() && entity instanceof Mob mob && !mob.isAddedToLevel()) {
            FusionRuntime.detach(mob);
        }
    }
}
