package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class FusionRenderer {
    private static final SliceBuffers SLICES = new SliceBuffers(new Matrix4f(),
            VisualGeometry.Cut.unbounded(), VisualGeometry.Cut.unbounded());
    private static LivingEntity body;
    private static MultiBufferSource originalBuffers;
    private static boolean effects;
    public static boolean effects() { return effects; }
    private static long renderedFrames;
    private static List<Integer> lastVertexCounts = List.of();

    private FusionRenderer() {}

    public static boolean active() { return body != null; }
    public static boolean auxiliary(Entity entity) { return active() && entity != body; }
    public static MultiBufferSource baseBuffers(MultiBufferSource fallback) { return active() ? originalBuffers : fallback; }
    public static long renderedFrames() { return renderedFrames; }
    public static List<Integer> lastVertexCounts() { return lastVertexCounts; }

    public static void renderEffects(LivingEntity host, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        if (active()) return;
        List<Mob> members = ClientAppearance.members(host);
        if (members.isEmpty()) return;
        body = host;
        originalBuffers = buffers;
        effects = true;
        try {
            for (Mob member : members) {
                ClientAppearance.pose(member, host);
                var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(member);
                if (renderer instanceof MemberEffectRenderer effect) {
                    poses.pushPose();
                    try { effect.cannedmonster$effects(member, host.getYRot(), partialTick, poses, buffers, light); }
                    finally { poses.popPose(); }
                }
            }
            var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(host);
            if (renderer instanceof MemberEffectRenderer effect) effect.cannedmonster$effects(host, host.getYRot(), partialTick, poses, buffers, light);
        } finally {
            effects = false;
            body = null;
            originalBuffers = null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(EntityRenderer renderer, Entity entity, float yaw, float partialTick,
                              PoseStack poses, MultiBufferSource buffers, int light) {
        if (!(entity instanceof LivingEntity host) || active()) {
            renderer.render(entity, yaw, partialTick, poses, buffers, light);
            return;
        }
        List<Mob> facets = ClientAppearance.members(host);
        if (facets.isEmpty()) {
            renderer.render(entity, yaw, partialTick, poses, buffers, light);
            return;
        }
        List<LivingEntity> members = new ArrayList<>(facets);
        members.add(host);
        VisualLayout layout = VisualGeometry.aligned(VisualViews.layout(host), members.size(),
                () -> VisualLayout.legacy(members));
        float width = Math.max(0.01F, host.getBbWidth());
        float height = Math.max(0.01F, host.getBbHeight());
        Matrix4f inverse = new Matrix4f(poses.last().pose()).invert();
        List<Integer> counts = new ArrayList<>();
        body = host;
        originalBuffers = buffers;
        try {
            for (int i = 0; i < members.size(); i++) {
                LivingEntity member = members.get(i);
                VisualLayout.Layer layer = layout.layers().get(i);
                if (!layer.visible()) {
                    counts.add(0);
                    continue;
                }
                if (member != host) ClientAppearance.pose((Mob) member, host);
                SliceBuffers sliced = SLICES;
                sliced.reset(inverse, VisualGeometry.lower(layout, i, width, height),
                        VisualGeometry.upper(layout, i, width, height));
                poses.pushPose();
                try {
                    VisualGeometry.applyPose(poses, layer, width, height,
                            VisualGeometry.bandFloor(layout, i));
                    EntityRenderer memberRenderer = member == host ? renderer
                            : Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(member);
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
