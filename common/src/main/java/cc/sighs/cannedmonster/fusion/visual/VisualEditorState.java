package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/**
 * Client-side editor session state shared by every version's screen. All mutations are confined
 * to the render thread; the screen adapters translate vanilla input into these calls.
 */
public final class VisualEditorState {
    public static final int SCOPE_INDIVIDUAL = 0;
    public static final int SCOPE_ITEM = 1;
    public static final int SCOPE_PLAYER = 2;
    public static final int SCOPE_SERVER = 3;
    public static final int SCOPE_COUNT = 4;
    public static final int SCOPE_SESSION = SCOPE_INDIVIDUAL;
    public static final int SCOPE_MENU = SCOPE_ITEM;
    public static final int SCOPE_ADMIN = SCOPE_SERVER;

    public enum Tab { LAYER, PLANE }

    private static final float OFFSET_STEP = 0.05F;
    private static final float OFFSET_LARGE = 0.5F;
    private static final float ROTATION_STEP = 5.0F;
    private static final float ROTATION_LARGE = 15.0F;
    private static final float SCALE_STEP = 0.05F;
    private static final float SCALE_LARGE = 0.25F;
    private static final float MIN_SCALE = 0.05F;
    private static final float MAX_SCALE = 8.0F;
    private static final float MAX_TRANSLATION = 16.0F;
    private static final float MIN_ZOOM = 0.25F;
    private static final float MAX_ZOOM = 4.0F;

    private final InteractionHand hand;
    private final long session;
    private final boolean admin;
    private int revision;
    private VisualLayout layout;
    private VisualLayout saved;
    private VisualLayout pending;
    private Tab tab = Tab.LAYER;
    private int index;
    private float yaw = 25.0F;
    private float pitch = 10.0F;
    private float zoom = 1.0F;
    private boolean saving;
    private int saveTicks;
    private boolean saveMenu;
    private boolean confirming;
    private boolean scaleLock;
    private boolean editing;
    private int editGroup = -1;
    private int editAxis = -1;
    private String editBuffer = "";
    private boolean editFresh = true;
    private boolean messageError;
    private Component message = Component.empty();

    VisualEditorState(InteractionHand hand, long session, int revision, VisualLayout layout, boolean admin) {
        this.hand = hand;
        this.session = session;
        this.revision = Math.max(0, revision);
        this.admin = admin;
        this.layout = layout == null || layout.layers().isEmpty() ? VisualLayout.reset(1) : layout;
        this.saved = this.layout;
    }

    public InteractionHand hand() { return hand; }

    public long session() { return session; }

    public int revision() { return revision; }

    public boolean admin() { return admin; }

    public VisualLayout layout() { return layout; }

    public Tab tab() { return tab; }

    public int index() { return index; }

    public int count() { return tab == Tab.LAYER ? layout.layerCount() : layout.planeCount(); }

    public float yaw() { return yaw; }

    public float pitch() { return pitch; }

    public float zoom() { return zoom; }

    public boolean saving() { return saving; }

    public boolean saveMenu() { return saveMenu; }

    public void openSaveMenu() {
        if (!saving) saveMenu = true;
    }

    public void closeSaveMenu() { saveMenu = false; }

    public boolean confirming() { return confirming; }

    public boolean scaleLock() { return scaleLock; }

    public void toggleScaleLock() {
        scaleLock = !scaleLock;
        clearMessage();
    }

    public boolean editing() { return editing; }

    public int editGroup() { return editGroup; }

    public int editAxis() { return editAxis; }

    public String editBuffer() { return editBuffer; }

    public boolean editingField(int group, int axis) {
        return editing && editGroup == group && editAxis == axis;
    }

    public void beginEdit(int group, int axis) {
        if (!editable(group, axis)) return;
        editing = true;
        editGroup = group;
        editAxis = axis;
        editBuffer = String.format(java.util.Locale.ROOT, "%.2f", numericValue(group, axis));
        editFresh = true;
        saveMenu = false;
    }

    public void editChar(char value) {
        if (!editing) return;
        if (value == ',') value = '.';
        if ((value < '0' || value > '9') && value != '.' && value != '-') return;
        if (editFresh) {
            editBuffer = "";
            editFresh = false;
        }
        if (value == '-' && !editBuffer.isEmpty()) return;
        if (value == '.' && editBuffer.indexOf('.') >= 0) return;
        if (editBuffer.length() >= 9) return;
        editBuffer += value;
    }

    public void backspaceEdit() {
        if (!editing) return;
        if (editFresh) {
            editBuffer = "";
            editFresh = false;
            return;
        }
        if (editBuffer.isEmpty()) return;
        editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
    }

    public void commitEdit() {
        if (!editing) return;
        String raw = editBuffer.replace(',', '.');
        editing = false;
        int group = editGroup;
        int axis = editAxis;
        editGroup = -1;
        editAxis = -1;
        editBuffer = "";
        editFresh = true;
        float value;
        try {
            value = Float.parseFloat(raw);
        } catch (NumberFormatException invalid) {
            return;
        }
        if (!Float.isFinite(value)) return;
        applyValue(group, axis, value);
    }

    public void cancelEdit() {
        editing = false;
        editGroup = -1;
        editAxis = -1;
        editBuffer = "";
        editFresh = true;
    }

    public float translation(int axis) {
        if (axis < 0 || axis > 2 || count() <= 0) return 0.0F;
        if (tab == Tab.LAYER) return layout.layer(index).offset().get(axis);
        return layout.plane(index).origin().get(axis);
    }

    public void setTranslation(int axis, float value) {
        if (!editable(0, axis)) return;
        applyValue(0, axis, value);
    }

    public boolean messageError() { return messageError; }

    public Component message() { return message; }

    public boolean dirty() { return !layout.equals(saved); }

    public void selectTab(Tab value) {
        if (value == null || value == tab) return;
        cancelEdit();
        tab = value;
        index = 0;
    }

    public void select(int value) {
        cancelEdit();
        if (count() <= 0) {
            index = 0;
            return;
        }
        index = Math.max(0, Math.min(count() - 1, value));
    }

    public void next(boolean forward) {
        if (count() <= 0) return;
        index = Math.floorMod(index + (forward ? 1 : -1), count());
    }

    /** group: 0 offset/origin, 1 rotation, 2 scale (layers only). axis: 0 x, 1 y, 2 z. */
    public void adjust(int group, int axis, int direction, boolean large) {
        if (!editable(group, axis) || direction == 0) return;
        index = Math.min(index, count() - 1);
        if (tab == Tab.LAYER) {
            VisualLayout.Layer layer = layout.layer(index);
            float value = switch (group) {
                case 0 -> layer.offset().get(axis) + direction * (large ? OFFSET_LARGE : OFFSET_STEP);
                case 1 -> wrap(layer.rotation().get(axis) + direction * (large ? ROTATION_LARGE : ROTATION_STEP));
                default -> clampScale(layer.scale().get(axis) + direction * (large ? SCALE_LARGE : SCALE_STEP));
            };
            VisualLayout.Layer changed = switch (group) {
                case 0 -> new VisualLayout.Layer(layer.height(), layer.visible(),
                        layer.offset().with(axis, clampTranslation(value)), layer.rotation(), layer.scale());
                case 1 -> new VisualLayout.Layer(layer.height(), layer.visible(),
                        layer.offset(), layer.rotation().with(axis, value), layer.scale());
                default -> new VisualLayout.Layer(layer.height(), layer.visible(),
                        layer.offset(), layer.rotation(), scaled(layer.scale(), axis, value));
            };
            setLayout(layout.withLayer(index, changed));
        } else {
            VisualLayout.Plane plane = layout.plane(index);
            float value = switch (group) {
                case 0 -> plane.origin().get(axis) + direction * (large ? OFFSET_LARGE : OFFSET_STEP);
                default -> wrap(plane.rotation().get(axis) + direction * (large ? ROTATION_LARGE : ROTATION_STEP));
            };
            VisualLayout.Plane changed = group == 0
                    ? new VisualLayout.Plane(plane.origin().with(axis, clampTranslation(value)), plane.rotation())
                    : new VisualLayout.Plane(plane.origin(), plane.rotation().with(axis, value));
            setLayout(layout.withPlane(index, changed));
        }
        clearMessage();
    }

    private boolean editable(int group, int axis) {
        if (axis < 0 || axis > 2 || count() <= 0) return false;
        if (group < 0 || group > 2) return false;
        return group != 2 || tab == Tab.LAYER;
    }

    private float numericValue(int group, int axis) {
        if (tab == Tab.LAYER) {
            VisualLayout.Layer layer = layout.layer(index);
            return switch (group) {
                case 0 -> layer.offset().get(axis);
                case 1 -> layer.rotation().get(axis);
                default -> layer.scale().get(axis);
            };
        }
        VisualLayout.Plane plane = layout.plane(index);
        return group == 0 ? plane.origin().get(axis) : plane.rotation().get(axis);
    }

    private void applyValue(int group, int axis, float value) {
        if (!editable(group, axis)) return;
        index = Math.min(index, count() - 1);
        if (tab == Tab.LAYER) {
            VisualLayout.Layer layer = layout.layer(index);
            VisualLayout.Layer changed = switch (group) {
                case 0 -> new VisualLayout.Layer(layer.height(), layer.visible(),
                        layer.offset().with(axis, clampTranslation(value)), layer.rotation(), layer.scale());
                case 1 -> new VisualLayout.Layer(layer.height(), layer.visible(),
                        layer.offset(), layer.rotation().with(axis, wrap(value)), layer.scale());
                default -> new VisualLayout.Layer(layer.height(), layer.visible(),
                        layer.offset(), layer.rotation(), scaled(layer.scale(), axis, value));
            };
            setLayout(layout.withLayer(index, changed));
        } else {
            VisualLayout.Plane plane = layout.plane(index);
            VisualLayout.Plane changed = group == 0
                    ? new VisualLayout.Plane(plane.origin().with(axis, clampTranslation(value)), plane.rotation())
                    : new VisualLayout.Plane(plane.origin(), plane.rotation().with(axis, wrap(value)));
            setLayout(layout.withPlane(index, changed));
        }
        clearMessage();
    }

    /**
     * Applies a scale to one axis. With the proportional lock enabled the other axes follow the
     * same ratio, so relative proportions survive numeric entry and button steps.
     */
    private VisualLayout.V scaled(VisualLayout.V scale, int axis, float value) {
        float target = clampScale(value);
        if (!scaleLock) return scale.with(axis, target);
        float previous = scale.get(axis);
        float ratio = Math.abs(previous) <= 1.0E-4F ? 1.0F : Math.abs(target) / Math.abs(previous);
        float[] values = {scale.x(), scale.y(), scale.z()};
        values[axis] = target;
        for (int i = 0; i < 3; i++) {
            if (i == axis) continue;
            values[i] = clampScale(values[i] * ratio);
        }
        return new VisualLayout.V(values[0], values[1], values[2]);
    }

    public void resetUnit() {
        cancelEdit();
        if (count() <= 0) return;
        if (tab == Tab.LAYER) setLayout(layout.resetLayer(index));
        else setLayout(layout.resetPlane(index));
        clearMessage();
    }

    public void resetAll() {
        cancelEdit();
        setLayout(VisualLayout.reset(layout.layerCount()));
        clearMessage();
    }

    public void resetView() {
        yaw = 25.0F;
        pitch = 10.0F;
        zoom = 1.0F;
    }

    public void rotateBy(double dx, double dy) {
        yaw = wrap((float) (yaw + dx * 0.9F));
        pitch = (float) Math.max(-89.0, Math.min(89.0, pitch + dy * 0.9F));
    }

    public void zoomBy(float delta) {
        zoom = (float) Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + delta));
    }

    public void requestClose() {
        cancelEdit();
        confirming = true;
        saveMenu = false;
    }

    public void cancelClose() { confirming = false; }

    public void clearMessage() {
        message = Component.empty();
        messageError = false;
    }

    void beginSave(int scope) {
        saving = true;
        saveTicks = 0;
        saveMenu = false;
        pending = layout;
        message = Component.translatable("editor.cannedmonster.saving");
        messageError = false;
    }

    public void tick() {
        if (!saving) return;
        if (++saveTicks > 200) {
            saving = false;
            messageError = true;
            message = Component.translatable("editor.cannedmonster.save_timeout");
        }
    }

    void acknowledge(int revision, boolean ok, String text) {
        saving = false;
        this.revision = Math.max(this.revision, revision);
        if (ok) {
            if (pending != null) saved = pending;
            pending = null;
            messageError = false;
            message = text == null || text.isEmpty()
                    ? Component.translatable("editor.cannedmonster.saved")
                    : Component.literal(text);
        } else {
            pending = null;
            messageError = true;
            message = text == null || text.isEmpty()
                    ? Component.translatable("editor.cannedmonster.save_failed")
                    : Component.literal(text);
        }
    }

    void fail(String langKey) {
        saving = false;
        pending = null;
        messageError = true;
        message = Component.translatable(langKey);
    }

    void closeConfirmed() { confirming = false; }

    private void setLayout(VisualLayout value) {
        layout = value;
        if (index >= count()) index = Math.max(0, count() - 1);
    }

    private static float clampTranslation(float value) {
        return Math.max(-MAX_TRANSLATION, Math.min(MAX_TRANSLATION, value));
    }

    private static float clampScale(float value) {
        float magnitude = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.abs(value)));
        return value < 0.0F ? -magnitude : magnitude;
    }

    private static float wrap(float degrees) {
        float value = degrees % 360.0F;
        if (value >= 180.0F) value -= 360.0F;
        if (value < -180.0F) value += 360.0F;
        return value;
    }
}
