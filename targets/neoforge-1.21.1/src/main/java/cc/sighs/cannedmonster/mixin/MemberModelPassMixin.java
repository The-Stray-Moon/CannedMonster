package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.FusionRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MemberModelPassMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$effectsOnly(LivingEntity entity, float yaw, float partialTick, PoseStack poses,
                                           MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (FusionRenderer.effects()) ci.cancel();
    }
}
