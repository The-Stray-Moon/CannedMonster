package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;

public interface MemberEffectRenderer {
    void cannedmonster$effects(LivingEntity member, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light);
}
