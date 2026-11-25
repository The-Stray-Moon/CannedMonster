package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.FusionRenderer;
import cc.sighs.cannedmonster.fusion.visual.VisualGeometry;
import cc.sighs.cannedmonster.fusion.visual.VisualViews;
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

import java.util.List;

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
        if (!(entity instanceof LivingEntity living)) return;
        List<LivingEntity> ordered = VisualViews.ordered(living);
        if (ordered.size() <= 1) return;
        var bounds = VisualGeometry.bodyBounds(living, VisualViews.layout(living), ordered,
                entity.getBoundingBoxForCulling());
        cir.setReturnValue(entity.shouldRender(x, y, z) && (entity.noCulling || frustum.isVisible(bounds)));
    }
}
