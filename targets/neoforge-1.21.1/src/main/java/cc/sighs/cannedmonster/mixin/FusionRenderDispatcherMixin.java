package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.FusionRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
public abstract class FusionRenderDispatcherMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void cannedmonster$slices(EntityRenderer<?> renderer, Entity entity, float yaw, float partialTick,
                                      PoseStack poses, MultiBufferSource buffers, int light) {
        FusionRenderer.render(renderer, entity, yaw, partialTick, poses, buffers, light);
    }
}
