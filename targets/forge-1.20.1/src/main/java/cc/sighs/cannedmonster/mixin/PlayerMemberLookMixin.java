package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerMemberLookMixin {
    @Unique
    private Player cannedmonster$lookPlayer() {
        Object body = BodyLinks.body(this);
        return body != this && body instanceof Player player ? player : null;
    }

    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$viewYaw(float partialTick, CallbackInfoReturnable<Float> cir) {
        Player player = cannedmonster$lookPlayer();
        if (player != null) cir.setReturnValue(player.getViewYRot(partialTick));
    }

    @Inject(method = "lookAt", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$noLivingAutoAim(EntityAnchorArgument.Anchor anchor, Vec3 target, CallbackInfo ci) {
        if (cannedmonster$lookPlayer() != null) ci.cancel();
    }

    @Inject(method = {"setYHeadRot", "setYBodyRot"}, at = @At("HEAD"), cancellable = true)
    private void cannedmonster$noHeadWrites(float angle, CallbackInfo ci) {
        if (cannedmonster$lookPlayer() != null) ci.cancel();
    }
}
