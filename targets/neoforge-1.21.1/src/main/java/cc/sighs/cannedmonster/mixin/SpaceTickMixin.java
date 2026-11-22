package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceDomain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class SpaceTickMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void cannedmonster$pushEntity(Entity entity, CallbackInfo ci) {
        SpaceDomain.push(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void cannedmonster$popEntity(Entity entity, CallbackInfo ci) {
        SpaceDomain.pop();
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"))
    private void cannedmonster$pushPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        SpaceDomain.push(passenger);
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"))
    private void cannedmonster$popPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        SpaceDomain.pop();
    }
}
