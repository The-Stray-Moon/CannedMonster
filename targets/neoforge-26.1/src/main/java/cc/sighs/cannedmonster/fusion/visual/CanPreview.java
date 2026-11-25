package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Detached item preview rendering for the 26.1 extraction pipeline. The GUI entity picture-in-picture
 * renderer still submits through {@link EntityRenderDispatcher}, so the fusion slice path applies.
 *
 * <p>The camera frames the composition bounding box; this pipeline cannot capture the rendered
 * vertices, so members whose model overhangs its bounding box are only fitted by their boxes here.
 */
public final class CanPreview {
    private CanPreview() {}

    public static LivingEntity host(ItemStack stack) {
        return CanPreviewHosts.host(stack);
    }

    /** Camera that fits the composition into {@code content} with {@code margin} kept free. */
    public static VisualEditorProjection.Camera frame(LivingEntity entity, VisualLayout layout,
                                                      VisualEditorProjection.Frame content, float yaw, float pitch,
                                                      float zoom, float margin) {
        if (entity == null || content == null) return null;
        VisualLayout applied = layout == null ? VisualViews.layout(entity) : layout;
        return VisualEditorProjection.boxCamera(content, VisualGeometry.compositionBounds(entity, applied),
                entity.getBbWidth(), entity.getBbHeight(), yaw, pitch, zoom, margin);
    }

    public static void draw(GuiGraphicsExtractor graphics, LivingEntity entity, VisualEditorProjection.Frame preview,
                            VisualEditorProjection.Camera camera, VisualLayout override) {
        if (graphics == null || entity == null || camera == null || preview == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (override != null) VisualViews.set(entity, override);
        float bodyHeight = Math.max(0.01F, camera.bodyHeight());
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
        try {
            for (Mob member : ClientAppearance.members(entity)) ClientAppearance.pose(member, entity);
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            EntityRenderState state = dispatcher.extractEntity(entity, 1.0F);
            if (state instanceof LivingEntityRenderState living) {
                living.bodyRot = renderYaw;
                living.yRot = renderYaw;
                living.xRot = pitch;
                if (living.scale != 0.0F) {
                    living.boundingBoxWidth /= living.scale;
                    living.boundingBoxHeight /= living.scale;
                }
                living.scale = 1.0F;
            }
            Vector3f translation = new Vector3f(0.0F, bodyHeight / 2.0F, 0.0F);
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf pitchRotation = new Quaternionf().rotateX((float) Math.toRadians(pitch));
            rotation.mul(pitchRotation);
            int halfWidth = Math.max(1, preview.width() / 2);
            int halfHeight = Math.max(1, preview.height() / 2);
            int centerX = Math.round(camera.centerX());
            int centerY = Math.round(camera.centerY());
            graphics.enableScissor(preview.left(), preview.top(), preview.right(), preview.bottom());
            try {
                graphics.entity(state, scale, translation, rotation, pitchRotation,
                        centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
            } finally {
                graphics.disableScissor();
            }
        } finally {
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
}
