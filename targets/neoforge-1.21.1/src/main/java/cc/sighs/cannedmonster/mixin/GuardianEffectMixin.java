package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.FusionRenderer;
import cc.sighs.cannedmonster.fusion.visual.MemberEffectRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.GuardianRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuardianRenderer.class)
public abstract class GuardianEffectMixin implements MemberEffectRenderer {
    @Redirect(method = "render(Lnet/minecraft/world/entity/monster/Guardian;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Guardian;getActiveAttackTarget()Lnet/minecraft/world/entity/LivingEntity;"))
    private LivingEntity cannedmonster$beamPass(Guardian guardian) {
        return FusionRenderer.active() && !FusionRenderer.effects() ? null : guardian.getActiveAttackTarget();
    }

    @Redirect(method = "render(Lnet/minecraft/world/entity/monster/Guardian;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Guardian;getEyeHeight()F"))
    private float cannedmonster$beamEye(Guardian guardian) {
        LivingEntity body = cc.sighs.cannedmonster.fusion.visual.ClientAppearance.bodyOf(guardian);
        return body == null ? guardian.getEyeHeight() : guardian.getEyeHeight() * body.getBbHeight() / Math.max(0.01F, guardian.getBbHeight());
    }

    @Override
    public void cannedmonster$effects(LivingEntity member, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        ((GuardianRenderer) (Object) this).render((Guardian) member, yaw, partialTick, poses, buffers, light);
    }
}
