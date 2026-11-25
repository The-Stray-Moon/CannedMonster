package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntitySaveMixin {
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void cannedmonster$save(ValueOutput output, CallbackInfo callback) {
        cc.sighs.cannedmonster.platform.EntityStorage.saveFusion((Entity) (Object) this, output);
    }

    @Inject(method = "setRemoved", at = @At("RETURN"))
    private void cannedmonster$removed(Entity.RemovalReason reason, CallbackInfo callback) {
        if ((Object) this instanceof net.minecraft.world.entity.Mob mob
                && !cc.sighs.cannedmonster.fusion.RemovalCalls.active(mob)) {
            FusionRuntime.detach(mob);
            cc.sighs.cannedmonster.fusion.visual.VisualViews.discard(mob);
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void cannedmonster$load(ValueInput input, CallbackInfo callback) {
        cc.sighs.cannedmonster.platform.EntityStorage.loadFusion((Entity) (Object) this, input);
    }
}
