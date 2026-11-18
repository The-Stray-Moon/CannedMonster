package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.ClientAppearance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class FusionEntityRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$bounds(Entity entity, Frustum frustum, double x, double y, double z,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && !ClientAppearance.members(living).isEmpty()) {
            cir.setReturnValue(entity.shouldRender(x, y, z) && frustum.isVisible(
                    entity.getBoundingBox().inflate(Math.max(entity.getBbWidth(), entity.getBbHeight()) * 2)));
        }
    }
}
