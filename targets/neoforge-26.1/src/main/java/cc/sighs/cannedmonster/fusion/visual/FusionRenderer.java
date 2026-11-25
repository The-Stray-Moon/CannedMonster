package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class FusionRenderer {
    private static final Map<EntityRenderState, Entity> ENTITIES =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private static long renderedFrames;
    private static List<Integer> lastVertexCounts = List.of();

    private FusionRenderer() {}

    public static void record(EntityRenderState state, Entity entity) {
        ENTITIES.put(state, entity);
    }

    public static boolean auxiliary(Entity entity) {
        return ACTIVE.get() && entity instanceof Mob member && ClientAppearance.bodyOf(member) != null;
    }

    public static long renderedFrames() { return renderedFrames; }
    public static List<Integer> lastVertexCounts() { return lastVertexCounts; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean submit(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                 PoseStack poses, SubmitNodeCollector collector) {
        if (ACTIVE.get()) return false;
        Entity body = ENTITIES.get(state);
        if (!(body instanceof LivingEntity host)) return false;
        List<Mob> facets = ClientAppearance.members(host);
        if (facets.isEmpty()) return false;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer renderer = dispatcher.getRenderer(host);
        var offset = renderer.getRenderOffset(state);
        List<LivingEntity> members = new ArrayList<>(facets);
        members.add(host);
        VisualLayout layout = VisualGeometry.aligned(VisualViews.layout(host), members.size(),
                () -> VisualLayout.legacy(members));
        int total = members.size();
        float width = Math.max(0.01F, host.getBbWidth());
        float height = Math.max(0.01F, host.getBbHeight());
        poses.pushPose();
        poses.translate(x + offset.x, y + offset.y, z + offset.z);
        Matrix4f inverse = new Matrix4f(poses.last().pose()).invert();
        ACTIVE.set(true);
        try {
            for (int i = 0; i < total; i++) {
                LivingEntity member = members.get(i);
                VisualLayout.Layer layer = layout.layers().get(i);
                if (!layer.visible()) continue;
                EntityRenderer memberRenderer = member == host ? renderer : dispatcher.getRenderer(member);
                EntityRenderState memberState = memberRenderer.createRenderState(member, state.partialTick);
                VisualGeometry.Cut lower = VisualGeometry.lower(layout, i, width, height);
                VisualGeometry.Cut upper = VisualGeometry.upper(layout, i, width, height);
                poses.pushPose();
                try {
                    VisualGeometry.applyPose(poses, layer, width, height,
                            VisualGeometry.bandFloor(layout, i));
                    SliceContext.run(new SliceContext.Band(inverse, lower, upper),
                            () -> memberRenderer.submit(memberState, poses, collector, camera));
                } finally {
                    poses.popPose();
                }
            }
            if (state.displayFireAnimation) {
                collector.submitFlame(poses, state,
                        Mth.rotationAroundAxis(Mth.Y_AXIS, camera.orientation, new Quaternionf()));
            }
            if (!state.shadowPieces.isEmpty()) {
                collector.submitShadow(poses, state.shadowRadius, state.shadowPieces);
            }
            renderedFrames++;
            lastVertexCounts = List.of();
        } finally {
            ACTIVE.set(false);
            poses.popPose();
        }
        return true;
    }
}
