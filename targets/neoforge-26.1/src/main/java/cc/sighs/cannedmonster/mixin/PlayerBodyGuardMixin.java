package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionTraits;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PlayerBodyGuardMixin {
    @Inject(method = "fireImmune", at = @At("RETURN"), cancellable = true)
    private void cannedmonster$fire(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(FusionTraits.fireImmune((Entity) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$playerInteraction(net.minecraft.world.entity.player.Player actor,
            net.minecraft.world.InteractionHand hand, Vec3 location,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        if (!((Object) this instanceof net.minecraft.world.entity.player.Player body)) return;
        for (net.minecraft.world.entity.Mob member : cc.sighs.cannedmonster.fusion.FusionRuntime.facets(body)) {
            var result = ((cc.sighs.cannedmonster.fusion.NativeMobAccess) member).cannedmonster$mobInteract(actor, hand);
            if (result != net.minecraft.world.InteractionResult.PASS) {
                cir.setReturnValue(result);
                return;
            }
        }
    }

    @Inject(method = {"setPos(DDD)V", "setPosRaw(DDD)V", "setYRot(F)V", "setXRot(F)V"},
            at = @At("HEAD"), cancellable = true)
    private void cannedmonster$position(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (cc.sighs.cannedmonster.fusion.TeleportCalls.allows(entity)) return;
        if (FusionTraits.playerMember(entity) || FusionTraits.autonomous(entity)) ci.cancel();
    }

    @Inject(method = {"setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", "setRemoved", "setNoGravity(Z)V"},
            at = @At("HEAD"), cancellable = true)
    private void cannedmonster$state(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (FusionTraits.playerMember(entity) || FusionTraits.autonomous(entity)) ci.cancel();
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$move(MoverType type, Vec3 delta, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (FusionTraits.playerMember(entity) || FusionTraits.autonomous(entity)) ci.cancel();
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$ride(Entity vehicle, boolean force, boolean sendEventAndTriggers,
                                    CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (FusionTraits.playerMember(entity) || FusionTraits.autonomous(entity)) {
            cir.setReturnValue(false);
            return;
        }
        if (cc.sighs.cannedmonster.fusion.BodyLinks.same(entity, vehicle)
                || cc.sighs.cannedmonster.fusion.BodyLinks.body(vehicle) instanceof net.minecraft.world.entity.player.Player) {
            cir.setReturnValue(false);
        }
    }
}
