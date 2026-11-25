package cc.sighs.cannedmonster.fusion.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Supplier;

/** Converts the degree/normalized interchange form of {@link VisualLayout} into render values. */
public final class VisualGeometry {
    private VisualGeometry() {}

    /**
     * A half space that keeps points with a non-negative signed distance. Both the normal and the
     * offset are already expressed in the host body space used by the slice capture.
     *
     * <p>An unbounded cut must answer with a finite positive distance: the slice workspace
     * interpolates the per-vertex distances of both cuts at every crossing, and an infinite
     * distance would make a crossing against the other cut interpolate {@code inf - inf = NaN}.
     * The NaN then reads as "outside" for the unbounded cut, so the freshly created vertex lost its
     * position and the whole primitive that crossed the cut vanished instead of being trimmed.
     */
    public record Cut(float x, float y, float z, float offset) {
        private static final float UNBOUNDED_OFFSET = -Float.MAX_VALUE;
        private static final Cut UNBOUNDED = new Cut(0.0F, 0.0F, 0.0F, UNBOUNDED_OFFSET);

        public static Cut unbounded() { return UNBOUNDED; }

        public boolean isUnbounded() { return offset == UNBOUNDED_OFFSET; }

        public float distance(float px, float py, float pz) {
            return x * px + y * py + z * pz - offset;
        }
    }

    /** Degrees XYZ as stored by the layout; JOML applies X, then Y, then Z around the member origin. */
    public static Quaternionf rotation(VisualLayout.V degrees) {
        return new Quaternionf()
                .rotateX((float) Math.toRadians(degrees.x()))
                .rotateY((float) Math.toRadians(degrees.y()))
                .rotateZ((float) Math.toRadians(degrees.z()));
    }

    /**
     * Normalised lower edge of a layer's height slot inside the host body. Rendering, culling and
     * the editor guides all anchor a layer's own origin on this floor, so a native unit scale keeps
     * the member inside its slot instead of losing it under the neighbouring cut. Legacy layouts
     * store the negated floor in their offset to keep the stretched members at the body origin.
     */
    public static float bandFloor(VisualLayout layout, int index) {
        if (layout == null || index < 0 || index >= layout.layerCount()) return 0.0F;
        List<VisualLayout.Layer> layers = layout.layers();
        float total = 0.0F;
        float above = 0.0F;
        for (int i = 0; i < layers.size(); i++) {
            float weight = layers.get(i).height();
            total += weight;
            if (i > index) above += weight;
        }
        return total <= 0.0F ? 0.0F : above / total;
    }

    /**
     * @param below when true the kept side is the one under the plane (used for the upper cut of a layer)
     */
    public static Cut cut(VisualLayout.Plane plane, float width, float height, boolean below) {
        Vector3f normal = rotation(plane.rotation()).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        float px = plane.origin().x() * width;
        float py = plane.origin().y() * height;
        float pz = plane.origin().z() * width;
        float offset = normal.x * px + normal.y * py + normal.z * pz;
        return below
                ? new Cut(-normal.x, -normal.y, -normal.z, -offset)
                : new Cut(normal.x, normal.y, normal.z, offset);
    }

    public static Cut lower(VisualLayout layout, int layer, float width, float height) {
        return layer < 0 || layer >= layout.planes().size() ? Cut.unbounded()
                : cut(layout.planes().get(layer), width, height, false);
    }

    public static Cut upper(VisualLayout layout, int layer, float width, float height) {
        return layer <= 0 || layer - 1 >= layout.planes().size() ? Cut.unbounded()
                : cut(layout.planes().get(layer - 1), width, height, true);
    }

    /**
     * Keeps a stored layout usable after the composition changed: an absent layout or a mismatched
     * layer count falls back to the supplied legacy layout of the actual ordered members, whose
     * native layer scales carry the old host/member fit ratios.
     */
    public static VisualLayout aligned(VisualLayout layout, int layerCount, Supplier<VisualLayout> legacy) {
        if (layout == null || layout.layers().size() != Math.max(0, layerCount)) return legacy.get();
        return layout;
    }

    /** Places a layer origin on its slot floor, then applies the editable offset, rotation and scale. */
    public static void applyPose(PoseStack poses, VisualLayout.Layer layer, float width, float height,
                                 float bandFloor) {
        poses.translate(layer.offset().x() * width, (bandFloor + layer.offset().y()) * height,
                layer.offset().z() * width);
        poses.mulPose(rotation(layer.rotation()));
        poses.scale(layer.scale().x(), layer.scale().y(), layer.scale().z());
    }

    /** Bounding box a layer can occupy, in world space, for culling. Layer scale is native. */
    public static AABB layerBox(double x, double y, double z, float hostWidth, float hostHeight,
                                float memberWidth, float memberHeight, VisualLayout.Layer layer,
                                float bandFloor) {
        Quaternionf rotation = rotation(layer.rotation());
        float scaleX = layer.scale().x();
        float scaleY = layer.scale().y();
        float scaleZ = layer.scale().z();
        Vector3f center = new Vector3f(0.0F, memberHeight * 0.5F * scaleY, 0.0F);
        rotation.transform(center);
        Vector3f extentX = rotation.transform(new Vector3f(memberWidth * 0.5F * scaleX, 0.0F, 0.0F));
        Vector3f extentY = rotation.transform(new Vector3f(0.0F, memberHeight * 0.5F * scaleY, 0.0F));
        Vector3f extentZ = rotation.transform(new Vector3f(0.0F, 0.0F, memberWidth * 0.5F * scaleZ));
        double halfX = Math.abs(extentX.x) + Math.abs(extentY.x) + Math.abs(extentZ.x);
        double halfY = Math.abs(extentX.y) + Math.abs(extentY.y) + Math.abs(extentZ.y);
        double halfZ = Math.abs(extentX.z) + Math.abs(extentY.z) + Math.abs(extentZ.z);
        double centerX = x + center.x + layer.offset().x() * hostWidth;
        double centerY = y + center.y + (bandFloor + layer.offset().y()) * hostHeight;
        double centerZ = z + center.z + layer.offset().z() * hostWidth;
        return new AABB(centerX - halfX, centerY - halfY, centerZ - halfZ,
                centerX + halfX, centerY + halfY, centerZ + halfZ);
    }

    /**
     * Union of the body box and every layout layer, inflated like the legacy single-body culling
     * box. {@code orderedIncludingHost} must match the render order ending with the host.
     */
    public static AABB bodyBounds(LivingEntity host, VisualLayout layout, List<? extends LivingEntity> orderedIncludingHost,
                                  AABB base) {
        AABB bounds = base;
        float hostWidth = Math.max(0.01F, host.getBbWidth());
        float hostHeight = Math.max(0.01F, host.getBbHeight());
        VisualLayout resolved = aligned(layout, orderedIncludingHost.size(),
                () -> VisualLayout.legacy(orderedIncludingHost));
        int count = Math.min(resolved.layers().size(), orderedIncludingHost.size());
        for (int i = 0; i < count; i++) {
            LivingEntity member = orderedIncludingHost.get(i);
            if (member == null) continue;
            VisualLayout.Layer layer = resolved.layers().get(i);
            if (!layer.visible()) continue;
            AABB box = layerBox(host.getX(), host.getY(), host.getZ(), hostWidth, hostHeight,
                    Math.max(0.01F, member.getBbWidth()), Math.max(0.01F, member.getBbHeight()), layer,
                    bandFloor(resolved, i));
            bounds = new AABB(
                    Math.min(bounds.minX, box.minX), Math.min(bounds.minY, box.minY), Math.min(bounds.minZ, box.minZ),
                    Math.max(bounds.maxX, box.maxX), Math.max(bounds.maxY, box.maxY), Math.max(bounds.maxZ, box.maxZ));
        }
        return bounds.inflate(Math.max(hostWidth, hostHeight) * 2.0);
    }

    /**
     * Body-space box of the host body and every visible layout layer, using the member bounding
     * boxes. Preview cameras use this as the deterministic first-frame frame before the client
     * measures the real rendered geometry, which can overhang the bounding box.
     */
    public static VisualEditorProjection.Box compositionBounds(LivingEntity host, VisualLayout layout) {
        float hostWidth = Math.max(0.01F, host == null ? 0.0F : host.getBbWidth());
        float hostHeight = Math.max(0.01F, host == null ? 0.0F : host.getBbHeight());
        VisualEditorProjection.Box box = VisualEditorProjection.Box.of(
                -hostWidth * 0.5F, 0.0F, -hostWidth * 0.5F, hostWidth * 0.5F, hostHeight, hostWidth * 0.5F);
        if (host == null) return box;
        List<LivingEntity> ordered = VisualViews.ordered(host);
        VisualLayout resolved = aligned(layout, ordered.size(), () -> VisualLayout.legacy(ordered));
        int count = Math.min(resolved.layers().size(), ordered.size());
        for (int i = 0; i < count; i++) {
            LivingEntity member = ordered.get(i);
            if (member == null) continue;
            VisualLayout.Layer layer = resolved.layers().get(i);
            if (!layer.visible()) continue;
            AABB layerBounds = layerBox(0.0, 0.0, 0.0, hostWidth, hostHeight,
                    Math.max(0.01F, member.getBbWidth()), Math.max(0.01F, member.getBbHeight()), layer,
                    bandFloor(resolved, i));
            box = box.union(VisualEditorProjection.Box.of(layerBounds.minX, layerBounds.minY, layerBounds.minZ,
                    layerBounds.maxX, layerBounds.maxY, layerBounds.maxZ));
        }
        return box;
    }
}
