package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.AppearanceSync;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class MemberEventMixin {
    @Inject(method = "broadcastEntityEvent", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$memberEvent(Entity entity, byte event, CallbackInfo ci) {
        if (AppearanceSync.broadcast(entity, event)) ci.cancel();
    }
}
