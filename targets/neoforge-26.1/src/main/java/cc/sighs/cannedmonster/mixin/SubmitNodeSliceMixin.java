package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.SliceConsumer;
import cc.sighs.cannedmonster.fusion.visual.SliceContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeSliceMixin {
    private static final ThreadLocal<Boolean> CANNEDMONSTER$INTERNAL = ThreadLocal.withInitial(() -> false);

    @Inject(method = "submitModel", at = @At("HEAD"), cancellable = true)
    private <S> void cannedmonster$sliceModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                              int lightCoords, int overlayCoords, int tintedColor,
                                              TextureAtlasSprite sprite, int outlineColor,
                                              ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        SliceContext.Band band = SliceContext.current();
        if (band == null) return;
        SubmitNodeCollection self = (SubmitNodeCollection) (Object) this;
        cannedmonster$custom(self, poseStack, renderType, (pose, buffer) -> {
            model.setupAnim(state);
            VertexConsumer target = sprite == null ? buffer : sprite.wrap(buffer);
            SliceConsumer slice = new SliceConsumer(target, band.inverse(), band.lower(), band.upper(), renderType.mode());
            PoseStack local = new PoseStack();
            local.last().set(pose);
            model.renderToBuffer(local, slice, lightCoords, overlayCoords, tintedColor);
            slice.flush();
        });
        ci.cancel();
    }

    @Inject(method = "submitModelPart", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$sliceModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType,
                                              int lightCoords, int overlayCoords, TextureAtlasSprite sprite,
                                              boolean sheeted, boolean hasFoil, int tintedColor,
                                              ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, int outlineColor,
                                              CallbackInfo ci) {
        SliceContext.Band band = SliceContext.current();
        if (band == null) return;
        SubmitNodeCollection self = (SubmitNodeCollection) (Object) this;
        cannedmonster$custom(self, poseStack, renderType, (pose, buffer) -> {
            VertexConsumer target = sprite == null ? buffer : sprite.wrap(buffer);
            SliceConsumer slice = new SliceConsumer(target, band.inverse(), band.lower(), band.upper(), renderType.mode());
            PoseStack local = new PoseStack();
            local.last().set(pose);
            modelPart.render(local, slice, lightCoords, overlayCoords, tintedColor);
            slice.flush();
        });
        ci.cancel();
    }

    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$sliceCustom(PoseStack poseStack, RenderType renderType,
                                           SubmitNodeCollector.CustomGeometryRenderer renderer, CallbackInfo ci) {
        SliceContext.Band band = SliceContext.current();
        if (band == null || CANNEDMONSTER$INTERNAL.get()) return;
        SubmitNodeCollection self = (SubmitNodeCollection) (Object) this;
        cannedmonster$custom(self, poseStack, renderType, (pose, buffer) -> {
            SliceConsumer slice = new SliceConsumer(buffer, band.inverse(), band.lower(), band.upper(), renderType.mode());
            renderer.render(pose, slice);
            slice.flush();
        });
        ci.cancel();
    }

    private static void cannedmonster$custom(SubmitNodeCollection self, PoseStack poseStack, RenderType renderType,
                                             SubmitNodeCollector.CustomGeometryRenderer renderer) {
        boolean previous = CANNEDMONSTER$INTERNAL.get();
        CANNEDMONSTER$INTERNAL.set(true);
        try {
            self.submitCustomGeometry(poseStack, renderType, renderer);
        } finally {
            CANNEDMONSTER$INTERNAL.set(previous);
        }
    }
}
