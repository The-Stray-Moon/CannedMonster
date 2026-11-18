package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PlayerMemberRotationMixin {
    @Unique
    private Player cannedmonster$rotationPlayer() {
        Object body = BodyLinks.body(this);
        return body != this && body instanceof Player player ? player : null;
    }

    @Inject(method = "getXRot", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$pitch(CallbackInfoReturnable<Float> cir) {
        Player player = cannedmonster$rotationPlayer();
        if (player != null) cir.setReturnValue(player.getXRot());
    }

    @Inject(method = "getYRot", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$yaw(CallbackInfoReturnable<Float> cir) {
        Player player = cannedmonster$rotationPlayer();
        if (player != null) cir.setReturnValue(player.getYRot());
    }

    @Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$viewPitch(float partialTick, CallbackInfoReturnable<Float> cir) {
        Player player = cannedmonster$rotationPlayer();
        if (player != null) cir.setReturnValue(player.getViewXRot(partialTick));
    }

    @Inject(method = "lookAt", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$noAutoAim(EntityAnchorArgument.Anchor anchor, Vec3 target, CallbackInfo ci) {
        if (cannedmonster$rotationPlayer() != null) ci.cancel();
    }
}
