package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.ClientAppearance;
import cc.sighs.cannedmonster.fusion.visual.FusionRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class FusionEntityRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$singleBase(Entity entity, float yaw, float partialTick, PoseStack poses,
                                          MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (FusionRenderer.auxiliary(entity)) ci.cancel();
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource cannedmonster$baseBuffers(MultiBufferSource buffers) {
        return FusionRenderer.baseBuffers(buffers);
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$bounds(Entity entity, Frustum frustum, double x, double y, double z,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && !ClientAppearance.members(living).isEmpty()) {
            cir.setReturnValue(entity.shouldRender(x, y, z) && (entity.noCulling || frustum.isVisible(
                    entity.getBoundingBoxForCulling().inflate(Math.max(entity.getBbWidth(), entity.getBbHeight()) * 2))));
        }
    }
}
