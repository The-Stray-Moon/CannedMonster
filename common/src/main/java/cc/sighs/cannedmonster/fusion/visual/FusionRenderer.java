package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class FusionRenderer {
    private static Mob body;
    private static MultiBufferSource originalBuffers;
    private static long renderedFrames;
    private static List<Integer> lastVertexCounts = List.of();

    private FusionRenderer() {}

    public static boolean active() { return body != null; }
    public static boolean auxiliary(Entity entity) { return active() && entity != body; }
    public static MultiBufferSource baseBuffers(MultiBufferSource fallback) { return active() ? originalBuffers : fallback; }
    public static long renderedFrames() { return renderedFrames; }
    public static List<Integer> lastVertexCounts() { return lastVertexCounts; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(EntityRenderer renderer, Entity entity, float yaw, float partialTick,
                              PoseStack poses, MultiBufferSource buffers, int light) {
        if (!(entity instanceof Mob host) || active()) {
            renderer.render(entity, yaw, partialTick, poses, buffers, light);
            return;
        }
        List<Mob> facets = ClientAppearance.members(host);
        if (facets.isEmpty()) {
            renderer.render(entity, yaw, partialTick, poses, buffers, light);
            return;
        }
        List<Mob> members = new ArrayList<>(facets);
        members.add(host);
        Matrix4f inverse = new Matrix4f(poses.last().pose()).invert();
        List<Integer> counts = new ArrayList<>();
        body = host;
        originalBuffers = buffers;
        try {
            for (int i = 0; i < members.size(); i++) {
                Mob member = members.get(i);
                if (member != host) ClientAppearance.pose(member, host);
                float height = Math.max(0.01F, host.getBbHeight());
                float lower = i == members.size() - 1 ? Float.NEGATIVE_INFINITY : height * (members.size() - i - 1) / members.size();
                float upper = i == 0 ? Float.POSITIVE_INFINITY : height * (members.size() - i) / members.size();
                SliceBuffers sliced = new SliceBuffers(inverse, lower, upper);
                poses.pushPose();
                try {
                    if (member != host) {
                        float scaleY = height / Math.max(0.01F, member.getBbHeight());
                        float scaleXZ = Math.max(0.01F, host.getBbWidth()) / Math.max(0.01F, member.getBbWidth());
                        poses.scale(scaleXZ, scaleY, scaleXZ);
                    }
                    EntityRenderer memberRenderer = member == host ? renderer : Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(member);
                    memberRenderer.render(member, yaw, partialTick, poses, sliced, light);
                } finally {
                    poses.popPose();
                }
                counts.add(sliced.submit(buffers));
            }
            renderedFrames++;
            lastVertexCounts = List.copyOf(counts);
        } finally {
            body = null;
            originalBuffers = null;
        }
    }
}
