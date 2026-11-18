package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.FusionRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class FusionRenderDispatcherMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> void cannedmonster$record(E entity, float partialTick,
                                                         CallbackInfoReturnable<EntityRenderState> cir) {
        FusionRenderer.record(cir.getReturnValue(), entity);
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$slices(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                      PoseStack poses, SubmitNodeCollector collector, CallbackInfo ci) {
        if (FusionRenderer.submit(state, camera, x, y, z, poses, collector)) ci.cancel();
    }
}
