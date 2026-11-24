package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpacePositions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class SpaceEntityPositionMixin {
    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void cannedmonster$externalBox(CallbackInfoReturnable<net.minecraft.world.phys.AABB> cir) {
        Vec3 external = SpacePositions.external((Entity) (Object) this);
        if (external == null) return;
        Entity self = (Entity) (Object) this;
        Vec3 real = cc.sighs.cannedmonster.space.SpaceDomain.raw(self::position);
        cir.setReturnValue(cir.getReturnValue().move(external.x - real.x, external.y - real.y, external.z - real.z));
    }

    @Inject(method = "blockPosition", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$externalBlockPosition(CallbackInfoReturnable<net.minecraft.core.BlockPos> cir) {
        Vec3 external = SpacePositions.external((Entity) (Object) this);
        if (external != null) cir.setReturnValue(net.minecraft.core.BlockPos.containing(external));
    }

    @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$distanceToRoom(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity) (Object) this;
        Vec3 external = SpacePositions.external(self);
        if (external == null) return;
        cc.sighs.cannedmonster.space.SpaceManager.Space space =
                cc.sighs.cannedmonster.space.SpaceManager.trackedSpace(self);
        if (space == null || !space.internal().contains(x, y, z)) return;
        Vec3 real = cc.sighs.cannedmonster.space.SpaceDomain.raw(self::position);
        double dx = real.x - x, dy = real.y - y, dz = real.z - z;
        cir.setReturnValue(dx * dx + dy * dy + dz * dz);
    }

    @Inject(method = "position", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$externalPosition(CallbackInfoReturnable<Vec3> cir) {
        Vec3 external = SpacePositions.external((Entity) (Object) this);
        if (external != null) cir.setReturnValue(external);
    }

    @Inject(method = "getX", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$externalX(CallbackInfoReturnable<Double> cir) {
        Vec3 external = SpacePositions.external((Entity) (Object) this);
        if (external != null) cir.setReturnValue(external.x);
    }

    @Inject(method = "getY", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$externalY(CallbackInfoReturnable<Double> cir) {
        Vec3 external = SpacePositions.external((Entity) (Object) this);
        if (external != null) cir.setReturnValue(external.y);
    }

    @Inject(method = "getZ", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$externalZ(CallbackInfoReturnable<Double> cir) {
        Vec3 external = SpacePositions.external((Entity) (Object) this);
        if (external != null) cir.setReturnValue(external.z);
    }

    /** A resident's enclosed room has no sky light, so daylight burning cannot trigger. */
    @Inject(method = "getLightLevelDependentMagicValue", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$roomLight(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        if (cc.sighs.cannedmonster.space.SpaceDomain.isolated(self)) cir.setReturnValue(0.0F);
    }
}
