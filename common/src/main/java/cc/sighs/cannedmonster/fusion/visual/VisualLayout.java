package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.platform.EntityStorage;
import cc.sighs.cannedmonster.platform.TagAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ordered visual composition of a fusion root entity.
 *
 * <p>Layers are stored top first, matching the slice order used by the renderer: the last layer is the
 * root body itself. {@code height} is a positive slice weight; planes split adjacent layers in
 * normalised body space where 0 is the feet and 1 the top of the body. Layer scale is native: a scale
 * of one renders the member at its own size, and the legacy appearance stores the host/member fit
 * ratios there explicitly. Each layer also carries the editor transform ({@code offset}/{@code
 * rotation}/{@code scale}) and each plane its editable {@code origin}/{@code rotation}. A layer
 * origin sits on the floor of its own height slot, so the renderer, the culling box and the editor
 * guides share one anchor; legacy layouts store the negated floor in the offset to keep their
 * stretched members at the body origin. The layout is immutable and value-comparable so appearance
 * sync can send it only when it changes.
 */
public final class VisualLayout {
    public static final int VERSION = 1;
    public static final int MAX_LAYERS = 512;
    public static final int MAX_PLANES = 64;

    public record V(float x, float y, float z) {
        public static final V ZERO = new V(0.0F, 0.0F, 0.0F);
        public static final V ONE = new V(1.0F, 1.0F, 1.0F);
        public static final V UNIT = ONE;

        public V {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("Visual layout vectors must be finite");
            }
        }

        public V with(int axis, float value) {
            return switch (axis) {
                case 0 -> new V(value, y, z);
                case 1 -> new V(x, value, z);
                case 2 -> new V(x, y, value);
                default -> throw new IllegalArgumentException("Unknown axis " + axis);
            };
        }

        public float get(int axis) {
            return switch (axis) {
                case 0 -> x;
                case 1 -> y;
                case 2 -> z;
                default -> throw new IllegalArgumentException("Unknown axis " + axis);
            };
        }
    }

    /**
     * One visual member. {@code height} drives slice weights, {@code visible} hides a member without
     * dropping it, offsets use body widths/heights, rotations are degrees and {@code scale} multiplies
     * the member's own native size.
     */
    public record Layer(float height, boolean visible, V offset, V rotation, V scale) {
        public static final Layer UNIT = new Layer(1.0F, true, V.ZERO, V.ZERO, V.ONE);
        public static final Layer IDENTITY = UNIT;

        public Layer {
            if (!Float.isFinite(height) || height <= 0.0F) {
                throw new IllegalArgumentException("Layer height must be positive and finite");
            }
            offset = offset == null ? V.ZERO : offset;
            rotation = rotation == null ? V.ZERO : rotation;
            scale = scale == null ? V.ONE : scale;
            if (Math.abs(scale.x) < 1.0E-4F || Math.abs(scale.y) < 1.0E-4F || Math.abs(scale.z) < 1.0E-4F) {
                throw new IllegalArgumentException("Visual layer scale must not collapse to zero");
            }
        }

        public Layer(V offset, V rotation, V scale) {
            this(1.0F, true, offset, rotation, scale);
        }

        public Layer(float height, boolean visible) {
            this(height, visible, V.ZERO, V.ZERO, V.ONE);
        }
    }

    /**
     * A cut between two layers. {@code position} mirrors {@code origin.y} for slice users, while
     * {@code origin}/{@code rotation} drive direct geometric clipping and the editor.
     */
    public record Plane(float position, V origin, V rotation) {
        public Plane {
            if (!Float.isFinite(position) || position < 0.0F || position > 1.0F) {
                throw new IllegalArgumentException("Plane position must be within [0, 1]");
            }
            origin = origin == null ? new V(0.0F, position, 0.0F) : origin;
            rotation = rotation == null ? V.ZERO : rotation;
        }

        public Plane(V origin, V rotation) {
            this(origin == null ? 0.5F : clampPosition(origin.y()), origin, rotation);
        }

        public Plane(float position) {
            this(position, new V(0.0F, position, 0.0F), V.ZERO);
        }
    }

    /** Body dimensions of one ordered member, used to derive the legacy fit ratios without entities. */
    public record Size(float width, float height) {
        public Size {
            if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F) {
                throw new IllegalArgumentException("Visual member size must be positive and finite");
            }
        }
    }

    private final List<Layer> layers;
    private final List<Plane> planes;

    /**
     * Validates the canonical composition: top-first layers and exactly one cut plane fewer than
     * layers. A layout with no layers is only meaningful as an empty placeholder.
     */
    public VisualLayout(List<Layer> layers, List<Plane> planes) {
        this(layers, planes, false);
    }

    private VisualLayout(List<Layer> layers, List<Plane> planes, boolean trusted) {
        Objects.requireNonNull(layers, "layers");
        Objects.requireNonNull(planes, "planes");
        if (!trusted) {
            if (layers.size() > MAX_LAYERS) throw new IllegalArgumentException("Too many visual layers: " + layers.size());
            if (planes.size() > MAX_PLANES) throw new IllegalArgumentException("Too many visual planes: " + planes.size());
            if (!layers.isEmpty() && planes.size() != layers.size() - 1) {
                throw new IllegalArgumentException("A visual layout needs one fewer plane than layers");
            }
            if (layers.isEmpty() && !planes.isEmpty()) {
                throw new IllegalArgumentException("An empty visual layout cannot have planes");
            }
        }
        for (Layer layer : layers) Objects.requireNonNull(layer, "layer");
        for (Plane plane : planes) Objects.requireNonNull(plane, "plane");
        this.layers = List.copyOf(layers);
        this.planes = List.copyOf(planes);
    }

    /** The unedited layout: native unit scales, equal slice weights and equal-height boundaries. */
    public static VisualLayout defaults(int layerCount) {
        if (layerCount < 0) throw new IllegalArgumentException("Negative visual layer count");
        int count = Math.min(layerCount, MAX_LAYERS);
        if (count == 0) return new VisualLayout(List.of(), List.of(), true);
        List<Layer> layers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) layers.add(Layer.UNIT);
        List<Plane> planes = new ArrayList<>(count - 1);
        for (int i = 0; i < count - 1; i++) {
            float position = (float) (count - 1 - i) / count;
            planes.add(new Plane(position));
        }
        return new VisualLayout(layers, planes, true);
    }

    public static VisualLayout reset(int layerCount) {
        return defaults(layerCount);
    }

    /** Host/member ratio that used to be implicit in the renderer, now stored as a native scale. */
    public static V legacyScale(Size host, Size member) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(member, "member");
        float width = host.width() / Math.max(0.01F, member.width());
        float height = host.height() / Math.max(0.01F, member.height());
        return new V(width, height, width);
    }

    /**
     * Legacy layout for already-resolved member dimensions; the last entry is the host. Native unit
     * scale stays reserved for {@link #reset(int)}, so the fit ratios are visible in the data. Each
     * stretched member keeps the body origin by storing the negated slot floor in its offset, which
     * is exactly what the renderer used to do implicitly.
     */
    public static VisualLayout legacySizes(List<Size> orderedIncludingHost) {
        Objects.requireNonNull(orderedIncludingHost, "orderedIncludingHost");
        if (orderedIncludingHost.size() > MAX_LAYERS) {
            throw new IllegalArgumentException("Too many visual layers: " + orderedIncludingHost.size());
        }
        int count = orderedIncludingHost.size();
        if (count == 0) return defaults(0);
        Size host = Objects.requireNonNull(orderedIncludingHost.get(count - 1), "host size");
        List<Layer> layers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (i == count - 1) {
                layers.add(Layer.UNIT);
            } else {
                Size member = Objects.requireNonNull(orderedIncludingHost.get(i), "member size");
                float floor = (float) (count - 1 - i) / count;
                layers.add(new Layer(1.0F, true, new V(0.0F, -floor, 0.0F), V.ZERO,
                        legacyScale(host, member)));
            }
        }
        return new VisualLayout(layers, defaults(count).planes(), true);
    }

    /**
     * Migration/default for saves that predate the editor. The old renderer stretched each member to
     * the body dimensions and stacked equal-height slices; {@code orderedIncludingHost} supplies the
     * actual member sizes that are encoded into the native layer scales.
     */
    public static VisualLayout legacy(List<? extends LivingEntity> orderedIncludingHost) {
        Objects.requireNonNull(orderedIncludingHost, "orderedIncludingHost");
        if (orderedIncludingHost.isEmpty()) return defaults(0);
        List<Size> sizes = new ArrayList<>(orderedIncludingHost.size());
        for (LivingEntity member : orderedIncludingHost) {
            if (member == null) throw new IllegalArgumentException("Legacy layout needs the actual ordered members");
            sizes.add(new Size(Math.max(0.01F, member.getBbWidth()), Math.max(0.01F, member.getBbHeight())));
        }
        return legacySizes(sizes);
    }

    /**
     * Rebuilds the composition after members were added or removed. Existing layers keep their custom
     * heights and visibility; the stack grows and shrinks at its top because fusion prepends and peels
     * the first member. Cut planes return to the equal-height stack for the new count.
     */
    public VisualLayout resized(int layerCount) {
        int target = Math.max(0, Math.min(layerCount, MAX_LAYERS));
        if (target == layers.size()) return this;
        List<Layer> rebuilt = new ArrayList<>(target);
        if (target < layers.size()) {
            rebuilt.addAll(layers.subList(layers.size() - target, layers.size()));
        } else {
            for (int i = layers.size(); i < target; i++) rebuilt.add(Layer.UNIT);
            rebuilt.addAll(layers);
        }
        VisualLayout shape = defaults(target);
        return new VisualLayout(rebuilt, shape.planes, true);
    }

    /**
     * Stacks {@code top} above {@code bottom}. Planes belong to the root body, so they are taken from
     * the bottom layout.
     */
    public static VisualLayout join(VisualLayout top, VisualLayout bottom) {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(bottom, "bottom");
        if (top.layers.isEmpty()) return bottom;
        if (bottom.layers.isEmpty()) return top;
        int count = top.layers.size() + bottom.layers.size();
        if (count > MAX_LAYERS) throw new IllegalArgumentException("Too many visual layers: " + count);
        List<Layer> layers = new ArrayList<>(count);
        layers.addAll(top.layers);
        layers.addAll(bottom.layers);
        if (top.equals(defaults(top.layers.size())) && bottom.equals(defaults(bottom.layers.size()))) {
            return defaults(count);
        }
        float split = (float) bottom.layers.size() / count;
        List<Plane> planes = new ArrayList<>(count - 1);
        for (Plane plane : top.planes) {
            planes.add(new Plane(split + plane.position() * (1.0F - split),
                    new V(plane.origin().x(), split + plane.origin().y() * (1.0F - split), plane.origin().z()),
                    plane.rotation()));
        }
        planes.add(new Plane(split));
        for (Plane plane : bottom.planes) {
            planes.add(new Plane(plane.position() * split,
                    new V(plane.origin().x(), plane.origin().y() * split, plane.origin().z()),
                    plane.rotation()));
        }
        return new VisualLayout(layers, planes, true);
    }

    public List<Layer> layers() {
        return layers;
    }

    public List<Plane> planes() {
        return planes;
    }

    public int layerCount() {
        return layers.size();
    }

    public int planeCount() {
        return planes.size();
    }

    public Layer layer(int index) {
        return layers.get(Math.max(0, Math.min(layers.size() - 1, index)));
    }

    public Plane plane(int index) {
        if (planes.isEmpty()) return new Plane(0.5F);
        return planes.get(Math.max(0, Math.min(planes.size() - 1, index)));
    }

    public VisualLayout withLayer(int index, Layer layer) {
        List<Layer> copy = new ArrayList<>(layers);
        copy.set(Math.max(0, Math.min(copy.size() - 1, index)), layer);
        return new VisualLayout(copy, planes, true);
    }

    public VisualLayout withPlane(int index, Plane plane) {
        if (planes.isEmpty()) return this;
        List<Plane> copy = new ArrayList<>(planes);
        copy.set(Math.max(0, Math.min(copy.size() - 1, index)), plane);
        return new VisualLayout(layers, copy, true);
    }

    /** Resets only the transform of the selected layer; its slice weight and visibility survive. */
    public VisualLayout resetLayer(int index) {
        Layer value = layer(index);
        return withLayer(index, new Layer(value.height(), value.visible(), V.ZERO, V.ZERO, V.ONE));
    }

    /** Resets only the transform of the selected plane; its slice position survives. */
    public VisualLayout resetPlane(int index) {
        Plane value = plane(index);
        return withPlane(index, new Plane(value.position()));
    }

    /** Normalised {@code [lower, upper]} slice bounds for a layer, measured from the feet. */
    public float[] bounds(int index) {
        Objects.checkIndex(index, layers.size());
        float total = 0.0F;
        for (Layer layer : layers) total += layer.height();
        float above = 0.0F;
        for (int i = index + 1; i < layers.size(); i++) above += layers.get(i).height();
        float height = layers.get(index).height();
        return new float[]{above / total, (above + height) / total};
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        ListTag layerTags = new ListTag();
        for (Layer layer : layers) {
            CompoundTag entry = new CompoundTag();
            entry.putFloat("Height", layer.height());
            entry.putBoolean("Visible", layer.visible());
            entry.put("Offset", vector(layer.offset()));
            entry.put("Rotation", vector(layer.rotation()));
            entry.put("Scale", vector(layer.scale()));
            layerTags.add(entry);
        }
        tag.put("Layers", layerTags);
        ListTag planeTags = new ListTag();
        for (Plane plane : planes) {
            CompoundTag entry = new CompoundTag();
            entry.putFloat("Position", plane.position());
            entry.put("Origin", vector(plane.origin()));
            entry.put("Rotation", vector(plane.rotation()));
            planeTags.add(entry);
        }
        tag.put("Planes", planeTags);
        return tag;
    }

    /** Returns {@code null} for a missing, malformed or incompatible layout. */
    public static VisualLayout load(CompoundTag tag) {
        if (tag == null) return null;
        try {
            if (!tag.contains("Version") || TagAccess.integer(tag, "Version") != VERSION) return null;
            ListTag layerTags = TagAccess.list(tag, "Layers");
            if (layerTags.isEmpty() || layerTags.size() > MAX_LAYERS) return null;
            ListTag planeTags = TagAccess.list(tag, "Planes");
            if (planeTags.size() > MAX_PLANES) return null;
            if (planeTags.size() != layerTags.size() - 1) return null;
            List<Layer> layers = new ArrayList<>(layerTags.size());
            for (int i = 0; i < layerTags.size(); i++) {
                CompoundTag entry = TagAccess.compound(layerTags, i);
                layers.add(new Layer((float) TagAccess.number(entry, "Height"),
                        !entry.contains("Visible") || EntityStorage.bool(entry, "Visible"),
                        requiredVector(entry, "Offset"), requiredVector(entry, "Rotation"),
                        requiredVector(entry, "Scale")));
            }
            List<Plane> planes = new ArrayList<>(planeTags.size());
            for (int i = 0; i < planeTags.size(); i++) {
                CompoundTag entry = TagAccess.compound(planeTags, i);
                planes.add(new Plane((float) TagAccess.number(entry, "Position"),
                        requiredVector(entry, "Origin"), requiredVector(entry, "Rotation")));
            }
            return new VisualLayout(layers, planes);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static CompoundTag vector(V value) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", value.x());
        tag.putDouble("Y", value.y());
        tag.putDouble("Z", value.z());
        return tag;
    }

    private static V requiredVector(CompoundTag tag, String key) {
        if (!tag.contains(key)) throw new IllegalArgumentException("Missing visual vector " + key);
        CompoundTag value = TagAccess.compound(tag, key);
        if (!value.contains("X") || !value.contains("Y") || !value.contains("Z")) {
            throw new IllegalArgumentException("Incomplete visual vector " + key);
        }
        return new V((float) TagAccess.number(value, "X"), (float) TagAccess.number(value, "Y"),
                (float) TagAccess.number(value, "Z"));
    }

    private static float clampPosition(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VisualLayout layout)) return false;
        return layers.equals(layout.layers) && planes.equals(layout.planes);
    }

    @Override
    public int hashCode() {
        return 31 * layers.hashCode() + planes.hashCode();
    }

    @Override
    public String toString() {
        return "VisualLayout[layers=" + layers + ", planes=" + planes + "]";
    }
}
