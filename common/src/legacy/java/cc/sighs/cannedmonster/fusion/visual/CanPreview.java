package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Detached item preview rendering shared by the 1.20.1 and 1.21.1 targets. Hosts come from
 * {@link CanPreviewHosts}, so this class only handles framing, rotation, scissoring and the GUI draw
 * call.
 *
 * <p>The camera frames the whole composition, not just the host body box: the first frame after a
 * preview host or layout change measures the real rendered geometry in body space, which catches
 * heads, beaks and feet that overhang the member bounding boxes. Measurements are cached per layout
 * and reused, so the framing is stable while animations advance.
 */
public final class CanPreview {
    private record Measured(VisualLayout layout, VisualEditorProjection.Box box) {}

    private static final Map<LivingEntity, Measured> MEASURED = new WeakHashMap<>();

    private CanPreview() {}

    public static LivingEntity host(ItemStack stack) {
        return CanPreviewHosts.host(stack);
    }

    /**
     * Camera that fits the preview host composition into {@code content} with {@code margin} of the
     * frame kept free on every side. Uses the measured geometry when the layout is already known and
     * the bounding-box estimate before the first measured frame.
     */
    public static VisualEditorProjection.Camera frame(LivingEntity entity, VisualLayout layout,
                                                      VisualEditorProjection.Frame content, float yaw, float pitch,
                                                      float zoom, float margin) {
        if (entity == null || content == null) return null;
        VisualEditorProjection.Camera camera = VisualEditorProjection.boxCamera(content,
                bounds(entity, layout), entity.getBbWidth(), entity.getBbHeight(), yaw, pitch, zoom, margin);
        if (camera != null) return camera;
        return VisualEditorProjection.bodyCamera(content, entity.getBbWidth(), entity.getBbHeight(),
                yaw, pitch, zoom, margin);
    }

    /**
     * Body-space box the preview camera frames: the measured rendered geometry for {@code layout}
     * once a frame has recorded it, otherwise the bounding-box estimate of the composition.
     */
    public static VisualEditorProjection.Box bounds(LivingEntity entity, VisualLayout layout) {
        if (entity == null) return null;
        VisualLayout applied = layout == null ? VisualViews.layout(entity) : layout;
        Measured measured = MEASURED.get(entity);
        return measured != null && sameLayout(measured.layout(), applied)
                ? measured.box() : VisualGeometry.compositionBounds(entity, applied);
    }

    /** Renders the host with {@code camera} inside {@code preview}; also records the measured bounds. */
    public static void draw(GuiGraphics graphics, LivingEntity entity, VisualEditorProjection.Frame preview,
                            VisualEditorProjection.Camera camera, VisualLayout override) {
        if (graphics == null || entity == null || camera == null || preview == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (override != null) VisualViews.set(entity, override);
        VisualLayout applied = override == null ? VisualViews.layout(entity) : override;
        float bodyHeight = Math.max(0.01F, entity.getBbHeight());
        float scale = camera.scale();
        if (!(scale > 0.0F) || !Float.isFinite(scale)) return;
        float pitch = camera.pitch();
        float renderYaw = 180.0F + camera.yaw();
        float bodyRot = entity.yBodyRot;
        float bodyRotO = entity.yBodyRotO;
        float yRot = entity.getYRot();
        float yRotO = entity.yRotO;
        float xRot = entity.getXRot();
        float xRotO = entity.xRotO;
        float headRot = entity.yHeadRot;
        float headRotO = entity.yHeadRotO;
        entity.yBodyRot = renderYaw;
        entity.yBodyRotO = renderYaw;
        entity.setYRot(renderYaw);
        entity.yRotO = renderYaw;
        entity.setXRot(pitch);
        entity.xRotO = pitch;
        entity.yHeadRot = renderYaw;
        entity.yHeadRotO = renderYaw;
        for (Mob member : ClientAppearance.members(entity)) ClientAppearance.pose(member, entity);
        PoseStack poses = graphics.pose();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf pitchRotation = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        rotation.mul(pitchRotation);
        graphics.enableScissor(preview.left(), preview.top(), preview.right(), preview.bottom());
        poses.pushPose();
        try {
            poses.translate(camera.centerX(), camera.centerY(), 50.0);
            poses.scale(scale, scale, -scale);
            poses.translate(0.0, bodyHeight / 2.0, 0.0);
            poses.mulPose(rotation);
            Lighting.setupForEntityInInventory();
            dispatcher.overrideCameraOrientation(pitchRotation.conjugate(new Quaternionf()).rotateY((float) Math.PI));
            dispatcher.setRenderShadow(false);
            MultiBufferSource buffers = graphics.bufferSource();
            PreviewBounds.Recorder recorder = hasMeasurement(entity, applied) ? null
                    : PreviewBounds.create(buffers, modelInverse(poses, camera.yaw()));
            if (recorder != null) buffers = recorder;
            MultiBufferSource target = buffers;
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F,
                    poses, target, 15728880));
            graphics.flush();
            if (recorder != null) {
                VisualEditorProjection.Box box = recorder.box();
                if (box != null) MEASURED.put(entity, new Measured(applied, box));
            }
            dispatcher.setRenderShadow(true);
        } finally {
            poses.popPose();
            graphics.disableScissor();
            Lighting.setupFor3DItems();
            dispatcher.setRenderShadow(true);
            entity.yBodyRot = bodyRot;
            entity.yBodyRotO = bodyRotO;
            entity.setYRot(yRot);
            entity.yRotO = yRotO;
            entity.setXRot(xRot);
            entity.xRotO = xRotO;
            entity.yHeadRot = headRot;
            entity.yHeadRotO = headRotO;
        }
    }

    /**
     * Inverse of the preview pose plus the entity yaw and the model mirror, so recorded vertices land
     * in the same body space the layout offsets and cut planes use: feet at {@code y = 0}, positive y
     * up, x/z matching the layer transforms.
     */
    private static Matrix4f modelInverse(PoseStack poses, float yaw) {
        Matrix4f inverse = new Matrix4f(poses.last().pose()).invert();
        return new Matrix4f().scaling(-1.0F, 1.0F, 1.0F)
                .rotateY((float) Math.toRadians(yaw))
                .mul(inverse);
    }

    private static boolean hasMeasurement(LivingEntity entity, VisualLayout layout) {
        Measured measured = MEASURED.get(entity);
        return measured != null && sameLayout(measured.layout(), layout);
    }

    private static boolean sameLayout(VisualLayout first, VisualLayout second) {
        return first == second || (first != null && first.equals(second));
    }
}
