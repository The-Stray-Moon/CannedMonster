package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySaveMixin {
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void cannedmonster$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> callback) {
        FusionRuntime.save((Entity) (Object) this, callback.getReturnValue());
    }

    @Inject(method = "setRemoved", at = @At("RETURN"))
    private void cannedmonster$removed(Entity.RemovalReason reason, CallbackInfo callback) {
        if ((Object) this instanceof net.minecraft.world.entity.Mob mob
                && !cc.sighs.cannedmonster.fusion.RemovalCalls.active(mob)) {
            FusionRuntime.detach(mob);
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void cannedmonster$load(CompoundTag tag, CallbackInfo callback) {
        FusionRuntime.load((Entity) (Object) this, tag);
    }
}
