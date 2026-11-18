package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class PlayerMemberMobMixin {
    private Player cannedmonster$player() {
        Object body = BodyLinks.body(this);
        return body instanceof Player player ? player : null;
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$target(LivingEntity target, CallbackInfo ci) {
        Player player = cannedmonster$player();
        if (player != null && target != null && !cc.sighs.cannedmonster.fusion.FusionTraits.allowedTarget(player, target)) ci.cancel();
    }

    @Inject(method = "setTarget", at = @At("RETURN"))
    private void cannedmonster$targetReset(LivingEntity target, CallbackInfo ci) {
        if (target == null) cc.sighs.cannedmonster.fusion.TargetResets.cleared((Mob) (Object) this);
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$attack(net.minecraft.server.level.ServerLevel level, Entity target,
                                       CallbackInfoReturnable<Boolean> cir) {
        Player player = cannedmonster$player();
        if (player != null && !cc.sighs.cannedmonster.fusion.FusionTraits.allowedTarget(player, target)) cir.setReturnValue(false);
    }
}
