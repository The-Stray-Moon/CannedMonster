package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.ClientAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlayerFlightMoveMixin {
    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$flightRelative(float speed, Vec3 relative, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (player != Minecraft.getInstance().player || !ClientAppearance.flying(player)) return;
        if (player.isPassenger() || player.isSpectator() || player.isInWater() || player.isInLava()
                || player.isFallFlying()) return;
        Vec3 view = player.getViewVector(1.0F);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
        Vec3 input = view.scale(relative.z).add(right.scale(relative.x)).add(0.0, relative.y, 0.0);
        if (input.length() > 1.0) input = input.normalize();
        player.setDeltaMovement(player.getDeltaMovement().add(input.scale(speed)));
        ci.cancel();
    }
}
