package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Version-independent editor layout: hotspots, labels and interaction. Screen adapters only
 * forward drawing primitives and vanilla input, so all versions share one UI implementation.
 */
public final class VisualEditorView {
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }

        public int right() { return x + w; }

        public int bottom() { return y + h; }
    }

    public enum Action { NONE, CLOSE }

    public enum Drag { NONE, VIEW, AXIS_X, AXIS_Y, AXIS_Z }

    /** Body dimensions of the preview host and the camera the renderer used; null means no preview. */
    public record PreviewInfo(float bodyWidth, float bodyHeight, VisualEditorProjection.Camera camera) {}

    private record AxisHandle(Drag axis, float x0, float y0, float x1, float y1, float worldLength) {}

    private static final class Hit {
        final Rect rect;
        final String id;
        final boolean enabled;
        final String hint;

        private Hit(Rect rect, String id, boolean enabled, String hint) {
            this.rect = rect;
            this.id = id;
            this.enabled = enabled;
            this.hint = hint;
        }
    }

    private static final int COLOR_PANEL = 0xF0181820;
    private static final int COLOR_BOX = 0xFF262633;
    private static final int COLOR_BOX_HOVER = 0xFF34344A;
    private static final int COLOR_BOX_SELECTED = 0xFF3A6EA5;
    private static final int COLOR_BORDER = 0xFF6C7A96;
    private static final int COLOR_TEXT = 0xFFE6E6F0;
    private static final int COLOR_MUTED = 0xFF9AA0B4;
    private static final int COLOR_DISABLED = 0xFF666C7A;
    private static final int COLOR_ACCENT = 0xFF8CCBFF;
    private static final int COLOR_ERROR = 0xFFFF7A7A;
    private static final int COLOR_OK = 0xFF8CE38C;

    private static final int ROW = 15;
    private static final int SMALL = 14;

    /** Fraction of the fitting frame kept free on every side for the editor preview. */
    public static final float PREVIEW_MARGIN = 0.04F;

    private final List<Hit> hits = new ArrayList<>();
    private final List<AxisHandle> handles = new ArrayList<>();
    private int page;
    private int visibleRows = 3;
    private VisualEditorState.Tab lastTab;
    private String hoveredHint;
    private Drag drag = Drag.NONE;
    private double dragStartX;
    private double dragStartY;
    private double dragLastX;
    private double dragLastY;
    private float dragStartValue;
    private float dragWorldLength;
    private float dragScreenX;
    private float dragScreenY;

    public Rect preview(int width, int height) {
        int panelWidth = panelWidth(width);
        return new Rect(6, 26, Math.max(40, width - panelWidth - 18), Math.max(40, height - 32));
    }

    /**
     * Area of the preview that stays free of the hint line on top and the zoom/reset toolbar at the
     * bottom, inset a little on every side. Cameras fit the whole composition into this rect.
     */
    public Rect previewContent(int width, int height) {
        Rect preview = preview(width, height);
        int left = preview.x() + 5;
        int right = preview.right() - 5;
        int top = preview.y() + 16;
        int bottom = preview.bottom() - 21;
        if (right - left < 24) {
            left = preview.x() + 1;
            right = preview.right() - 1;
        }
        if (bottom - top < 24) {
            top = preview.y() + 1;
            bottom = preview.bottom() - 1;
        }
        return new Rect(left, top, Math.max(24, right - left), Math.max(24, bottom - top));
    }

    public boolean mouseOverPreview(double mx, double my, int width, int height) {
        return preview(width, height).contains(mx, my);
    }

    public Action click(VisualEditorState state, double mx, double my, boolean shift) {
        Hit target = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.rect.contains(mx, my)) {
                target = hit;
                break;
            }
        }
        if (state.editing()) {
            boolean same = target != null && target.id.equals("edit:" + state.editGroup() + ":" + state.editAxis());
            if (!same) state.commitEdit();
        }
        if (state.confirming()) {
            if (target == null) return Action.NONE;
            if (target.id.equals("confirm:discard")) {
                state.closeConfirmed();
                return Action.CLOSE;
            }
            if (target.id.equals("confirm:cancel")) {
                state.cancelClose();
                return Action.NONE;
            }
            return Action.NONE;
        }
        if (state.saveMenu()) {
            if (target == null) {
                state.closeSaveMenu();
                return Action.NONE;
            }
            if (target.id.equals("save:cancel")) {
                state.closeSaveMenu();
                return Action.NONE;
            }
            if (target.id.startsWith("save:") && target.enabled) {
                return handle(state, target.id, shift);
            }
            return Action.NONE;
        }
        if (target != null) {
            if (!target.enabled) return Action.NONE;
            return handle(state, target.id, shift);
        }
        return Action.NONE;
    }

    /**
     * Starts a preview drag. A nearby X/Y/Z handle drags the selected layer offset or plane origin;
     * anywhere else in the preview rotates the view.
     */
    public Drag beginDrag(VisualEditorState state, double mouseX, double mouseY, int width, int height) {
        if (!mouseOverPreview(mouseX, mouseY, width, height)) return Drag.NONE;
        if (state.editing()) state.commitEdit();
        AxisHandle best = null;
        double bestDistance = 6.0;
        for (AxisHandle handle : handles) {
            double distance = distanceToSegment(mouseX, mouseY, handle.x0, handle.y0, handle.x1, handle.y1);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = handle;
            }
        }
        dragStartX = mouseX;
        dragStartY = mouseY;
        dragLastX = mouseX;
        dragLastY = mouseY;
        if (best != null) {
            drag = best.axis;
            dragStartValue = state.translation(axisIndex(best.axis));
            dragWorldLength = best.worldLength;
            dragScreenX = best.x1 - best.x0;
            dragScreenY = best.y1 - best.y0;
        } else {
            drag = Drag.VIEW;
        }
        return drag;
    }

    public Drag dragMode() { return drag; }

    public void drag(VisualEditorState state, double mouseX, double mouseY) {
        if (drag == Drag.NONE) return;
        if (drag == Drag.VIEW) {
            state.rotateBy(mouseX - dragLastX, mouseY - dragLastY);
            dragLastX = mouseX;
            dragLastY = mouseY;
            return;
        }
        double length = Math.hypot(dragScreenX, dragScreenY);
        if (length < 2.0) return;
        double dx = mouseX - dragStartX;
        double dy = mouseY - dragStartY;
        double along = (dx * dragScreenX + dy * dragScreenY) / length;
        float world = (float) (along * (dragWorldLength / length));
        state.setTranslation(axisIndex(drag), dragStartValue + world);
        dragLastX = mouseX;
        dragLastY = mouseY;
    }

    public void endDrag() {
        drag = Drag.NONE;
        dragWorldLength = 0.0F;
    }

    private static int axisIndex(Drag axis) {
        return switch (axis) {
            case AXIS_X -> 0;
            case AXIS_Y -> 1;
            default -> 2;
        };
    }

    private static double distanceToSegment(double px, double py, float x0, float y0, float x1, float y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 1.0E-6) return Math.hypot(px - x0, py - y0);
        double t = Math.max(0.0, Math.min(1.0, ((px - x0) * dx + (py - y0) * dy) / lengthSquared));
        return Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy));
    }

    public void scroll(VisualEditorState state, double mx, double my, double amount, int width, int height) {
        if (mouseOverPreview(mx, my, width, height)) {
            state.zoomBy(amount > 0 ? 0.1F : -0.1F);
            return;
        }
        if (amount == 0) return;
        int total = state.count();
        if (total <= 0) return;
        int maxPage = Math.max(0, (total - 1) / Math.max(1, visibleRows));
        page = Math.max(0, Math.min(maxPage, page + (amount > 0 ? 1 : -1)));
        state.select(page * visibleRows);
    }

    private Action handle(VisualEditorState state, String id, boolean shift) {
        if (id.equals("close")) {
            if (state.dirty()) {
                state.requestClose();
                return Action.NONE;
            }
            return Action.CLOSE;
        }
        if (id.equals("confirm:discard")) {
            state.closeConfirmed();
            return Action.CLOSE;
        }
        if (id.equals("confirm:cancel")) {
            state.cancelClose();
            return Action.NONE;
        }
        if (id.startsWith("tab:")) {
            state.selectTab(id.equals("tab:1") ? VisualEditorState.Tab.PLANE : VisualEditorState.Tab.LAYER);
            page = 0;
            return Action.NONE;
        }
        if (id.equals("list:prev")) {
            page = Math.max(0, page - 1);
            state.select(page * visibleRows);
            return Action.NONE;
        }
        if (id.equals("list:next")) {
            int maxPage = Math.max(0, (state.count() - 1) / Math.max(1, visibleRows));
            page = Math.min(maxPage, page + 1);
            state.select(page * visibleRows);
            return Action.NONE;
        }
        if (id.startsWith("list:")) {
            int row = Integer.parseInt(id.substring(5));
            state.select(page * visibleRows + row);
            return Action.NONE;
        }
        if (id.startsWith("adj:")) {
            String[] parts = id.split(":");
            state.adjust(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), shift);
            return Action.NONE;
        }
        if (id.startsWith("edit:")) {
            String[] parts = id.split(":");
            state.beginEdit(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            return Action.NONE;
        }
        if (id.equals("scale:lock")) {
            state.toggleScaleLock();
            return Action.NONE;
        }
        if (id.equals("unit")) {
            state.resetUnit();
            return Action.NONE;
        }
        if (id.equals("all")) {
            state.resetAll();
            page = 0;
            return Action.NONE;
        }
        if (id.equals("view")) {
            state.resetView();
            return Action.NONE;
        }
        if (id.equals("zoom:in")) {
            state.zoomBy(0.15F);
            return Action.NONE;
        }
        if (id.equals("zoom:out")) {
            state.zoomBy(-0.15F);
            return Action.NONE;
        }
        if (id.equals("save")) {
            state.openSaveMenu();
            return Action.NONE;
        }
        if (id.equals("save:cancel")) {
            state.closeSaveMenu();
            return Action.NONE;
        }
        if (id.startsWith("save:")) {
            VisualEditorClient.save(state, Integer.parseInt(id.substring(5)));
            return Action.NONE;
        }
        return Action.NONE;
    }

    public void draw(EditorCanvas canvas, VisualEditorState state, int width, int height, int mouseX, int mouseY) {
        draw(canvas, state, width, height, mouseX, mouseY, null);
    }

    public void draw(EditorCanvas canvas, VisualEditorState state, int width, int height, int mouseX, int mouseY,
                     PreviewInfo info) {
        hits.clear();
        handles.clear();
        hoveredHint = null;
        if (lastTab != state.tab()) {
            lastTab = state.tab();
            page = 0;
        }
        Rect preview = preview(width, height);
        Rect panel = new Rect(preview.right() + 6, 26, panelWidth(width), Math.max(40, height - 32));

        drawHeader(canvas, state, width);

        canvas.border(preview.x(), preview.y(), preview.w(), preview.h(), COLOR_BORDER);
        if (info != null) {
            canvas.scissor(preview.x() + 1, preview.y() + 1, preview.right() - 1, preview.bottom() - 1);
            drawPreviewOverlay(canvas, state, preview, info);
            canvas.unscissor();
        }
        canvas.text(text("editor.cannedmonster.preview_hint"), preview.x() + 5, preview.y() + 5, COLOR_MUTED, false);
        if (preview.w() >= 100) {
            int controls = preview.y() + preview.h() - 19;
            addButton(canvas, preview.x() + 4, controls, 18, SMALL, "zoom:out", "-", true,
                    "editor.cannedmonster.hint.zoom", mouseX, mouseY);
            addButton(canvas, preview.x() + 24, controls, 18, SMALL, "zoom:in", "+", true,
                    "editor.cannedmonster.hint.zoom", mouseX, mouseY);
            addButton(canvas, preview.x() + 44, controls, 42, SMALL, "view", text("editor.cannedmonster.reset_view"), true,
                    "editor.cannedmonster.hint.reset_view", mouseX, mouseY);
            canvas.text(String.format(Locale.ROOT, "%.0f / %.0f / %.2fx", state.yaw(), state.pitch(), state.zoom()),
                    preview.x() + 90, controls + 3, COLOR_MUTED, false);
        }

        canvas.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), COLOR_PANEL);
        canvas.border(panel.x(), panel.y(), panel.w(), panel.h(), COLOR_BORDER);

        int innerX = panel.x() + 4;
        int innerW = panel.w() - 8;
        int y = panel.y() + 4;
        int half = (innerW - 3) / 2;
        boolean layerTab = state.tab() == VisualEditorState.Tab.LAYER;
        addButton(canvas, innerX, y, half, SMALL, "tab:0", text("editor.cannedmonster.layers"), true,
                "editor.cannedmonster.hint.tab", mouseX, mouseY, layerTab);
        addButton(canvas, innerX + half + 3, y, innerW - half - 3, SMALL, "tab:1", text("editor.cannedmonster.planes"), true,
                "editor.cannedmonster.hint.tab", mouseX, mouseY, !layerTab);

        int bottom = panel.bottom() - 4;
        int closeY = bottom - SMALL;
        int menuY = closeY - SMALL - 3;
        int unitY = menuY - SMALL - 4;
        boolean plane = !layerTab;
        int transformRows = plane ? 2 : 3;
        int transformTop = unitY - 4 - (18 + transformRows * ROW);
        int listTop = y + SMALL + 5;
        int listHeight = Math.max(16, transformTop - 4 - listTop);
        int rows = Math.max(1, Math.min(6, listHeight / ROW));
        visibleRows = rows;

        drawList(canvas, state, innerX, listTop, innerW, rows, mouseX, mouseY);
        drawTransforms(canvas, state, innerX, innerW, transformTop, plane, mouseX, mouseY);

        addButton(canvas, innerX, unitY, innerW, SMALL, "unit", text("editor.cannedmonster.reset_unit"), state.count() > 0,
                "editor.cannedmonster.hint.reset_unit", mouseX, mouseY);
        addButton(canvas, innerX, menuY, innerW, SMALL, "save", text("editor.cannedmonster.save"), !state.saving(),
                "editor.cannedmonster.hint.save", mouseX, mouseY, false, COLOR_OK);

        int actionW = (innerW - 6) / 3;
        addButton(canvas, innerX, closeY, actionW, SMALL, "all", text("editor.cannedmonster.reset_all"), true,
                "editor.cannedmonster.hint.reset_all", mouseX, mouseY);
        addButton(canvas, innerX + actionW + 3, closeY, actionW, SMALL, "view", text("editor.cannedmonster.reset_view"), true,
                "editor.cannedmonster.hint.reset_view", mouseX, mouseY);
        addButton(canvas, innerX + (actionW + 3) * 2, closeY, innerW - (actionW + 3) * 2, SMALL, "close",
                text("editor.cannedmonster.close"), true, "editor.cannedmonster.hint.close", mouseX, mouseY);

        if (state.confirming()) drawConfirm(canvas, width, height, mouseX, mouseY);
        else if (state.saveMenu()) drawSaveMenu(canvas, state, width, height, mouseX, mouseY);
        drawHint(canvas, state, width, height);
    }

    private void drawPreviewOverlay(EditorCanvas canvas, VisualEditorState state, Rect preview, PreviewInfo info) {
        if (state.count() <= 0 || info.camera() == null) return;
        VisualEditorProjection.Camera camera = info.camera();
        boolean plane = state.tab() == VisualEditorState.Tab.PLANE;
        float width = Math.max(0.01F, info.bodyWidth());
        float height = Math.max(0.01F, info.bodyHeight());

        float ax;
        float ay;
        float az;
        if (plane) {
            VisualLayout.Plane value = state.layout().plane(state.index());
            ax = value.origin().x() * width;
            ay = value.origin().y() * height;
            az = value.origin().z() * width;
            drawPlaneGuide(canvas, camera, value, ax, ay, az, width, height);
        } else {
            VisualLayout.Layer value = state.layout().layer(state.index());
            ax = value.offset().x() * width;
            ay = (VisualGeometry.bandFloor(state.layout(), state.index()) + value.offset().y()) * height;
            az = value.offset().z() * width;
        }
        drawHandle(canvas, camera, Drag.AXIS_X, ax, ay, az, 1.0F, 0.0F, 0.0F, 0.3F * width, "X", 0xFFFF6060);
        drawHandle(canvas, camera, Drag.AXIS_Y, ax, ay, az, 0.0F, 1.0F, 0.0F, 0.3F * height, "Y", 0xFF70E070);
        drawHandle(canvas, camera, Drag.AXIS_Z, ax, ay, az, 0.0F, 0.0F, 1.0F, 0.3F * width, "Z", 0xFF70A0FF);
        VisualEditorProjection.Point anchor = camera.project(ax, ay, az);
        canvas.fill(Math.round(anchor.x()) - 1, Math.round(anchor.y()) - 1,
                Math.round(anchor.x()) + 2, Math.round(anchor.y()) + 2, COLOR_TEXT);
    }

    private void drawHandle(EditorCanvas canvas, VisualEditorProjection.Camera camera, Drag axis,
                            float x, float y, float z, float dx, float dy, float dz, float length,
                            String label, int color) {
        VisualEditorProjection.Point start = camera.project(x, y, z);
        VisualEditorProjection.Point end = VisualEditorProjection.endpoint(camera, x, y, z, dx, dy, dz, length);
        canvas.thickLine(Math.round(start.x()), Math.round(start.y()), Math.round(end.x()), Math.round(end.y()), color);
        canvas.fill(Math.round(end.x()) - 2, Math.round(end.y()) - 2,
                Math.round(end.x()) + 3, Math.round(end.y()) + 3, color);
        canvas.text(label, Math.round(end.x()) + 3, Math.round(end.y()) - 4, color, false);
        handles.add(new AxisHandle(axis, start.x(), start.y(), end.x(), end.y(), length));
    }

    private void drawPlaneGuide(EditorCanvas canvas, VisualEditorProjection.Camera camera, VisualLayout.Plane plane,
                                float x, float y, float z, float width, float height) {
        float size = 0.4F * Math.max(width, height);
        float[] u = VisualEditorProjection.rotate(plane.rotation(), 1.0F, 0.0F, 0.0F);
        float[] v = VisualEditorProjection.rotate(plane.rotation(), 0.0F, 0.0F, 1.0F);
        float[][] corners = {
                corner(camera, x, y, z, u, v, size, -1.0F, -1.0F),
                corner(camera, x, y, z, u, v, size, 1.0F, -1.0F),
                corner(camera, x, y, z, u, v, size, 1.0F, 1.0F),
                corner(camera, x, y, z, u, v, size, -1.0F, 1.0F)
        };
        int color = 0xFFA0C8FF;
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % 4];
            canvas.line(Math.round(a[0]), Math.round(a[1]), Math.round(b[0]), Math.round(b[1]), color);
        }
        VisualEditorProjection.Point center = camera.project(x, y, z);
        canvas.line(Math.round(center.x()), Math.round(center.y()),
                Math.round((corners[0][0] + corners[2][0]) * 0.5F), Math.round((corners[0][1] + corners[2][1]) * 0.5F), 0x60A0C8FF);

        float[] normal = VisualEditorProjection.rotate(plane.rotation(), 0.0F, 1.0F, 0.0F);
        float normalLength = 0.35F * size;
        VisualEditorProjection.Point tip = VisualEditorProjection.endpoint(camera, x, y, z,
                normal[0], normal[1], normal[2], normalLength);
        int normalColor = 0xFFFFD060;
        canvas.thickLine(Math.round(center.x()), Math.round(center.y()), Math.round(tip.x()), Math.round(tip.y()), normalColor);
        double dx = tip.x() - center.x();
        double dy = tip.y() - center.y();
        double length = Math.hypot(dx, dy);
        if (length > 2.0) {
            double ux = dx / length;
            double uy = dy / length;
            int head = 5;
            canvas.line(Math.round(tip.x()), Math.round(tip.y()),
                    (int) Math.round(tip.x() - ux * head + uy * head * 0.5), (int) Math.round(tip.y() - uy * head - ux * head * 0.5), normalColor);
            canvas.line(Math.round(tip.x()), Math.round(tip.y()),
                    (int) Math.round(tip.x() - ux * head - uy * head * 0.5), (int) Math.round(tip.y() - uy * head + ux * head * 0.5), normalColor);
        }
        canvas.text("n", Math.round(tip.x()) + 3, Math.round(tip.y()) - 4, normalColor, false);
    }

    private static float[] corner(VisualEditorProjection.Camera camera, float x, float y, float z,
                                  float[] u, float[] v, float size, float su, float sv) {
        VisualEditorProjection.Point point = camera.project(
                x + u[0] * size * su + v[0] * size * sv,
                y + u[1] * size * su + v[1] * size * sv,
                z + u[2] * size * su + v[2] * size * sv);
        return new float[]{point.x(), point.y()};
    }

    private void drawHeader(EditorCanvas canvas, VisualEditorState state, int width) {
        canvas.text(text("editor.cannedmonster.title"), 8, 6, COLOR_TEXT, false);
        String info = "#" + Long.toHexString(state.session()) + "  r" + state.revision();
        int infoWidth = canvas.width(info);
        canvas.text(info, width - infoWidth - 8, 6, COLOR_MUTED, false);
        if (state.dirty()) {
            canvas.text("*", width - infoWidth - 16, 6, COLOR_ACCENT, false);
        }
    }

    private void drawHint(EditorCanvas canvas, VisualEditorState state, int width, int height) {
        String line;
        int color;
        if (state.confirming()) {
            line = text("editor.cannedmonster.confirm_hint");
            color = COLOR_MUTED;
        } else if (state.saveMenu()) {
            line = hoveredHint != null ? text(hoveredHint)
                    : text("editor.cannedmonster.save_menu_hint");
            color = COLOR_MUTED;
        } else if (state.messageError()) {
            line = messageText(state.message());
            color = COLOR_ERROR;
        } else if (hoveredHint != null) {
            line = text(hoveredHint);
            color = COLOR_MUTED;
        } else {
            line = messageText(state.message());
            color = COLOR_OK;
        }
        if (line == null || line.isEmpty()) return;
        canvas.text(fit(canvas, line, width - 16), 8, 16, color, false);
    }

    private void drawList(EditorCanvas canvas, VisualEditorState state, int x, int top, int width, int rows, int mouseX, int mouseY) {
        int total = state.count();
        int maxPage = Math.max(0, (total - 1) / Math.max(1, rows));
        page = Math.max(0, Math.min(maxPage, page));
        boolean showPager = total > rows;
        int listWidth = width - (showPager ? 15 : 0);
        if (state.tab() == VisualEditorState.Tab.PLANE && total == 0) {
            canvas.text(text("editor.cannedmonster.no_planes"), x + 2, top + 3, COLOR_MUTED, false);
            return;
        }
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= total) break;
            int ry = top + row * ROW;
            String label = (state.tab() == VisualEditorState.Tab.LAYER
                    ? text("editor.cannedmonster.layer") : text("editor.cannedmonster.plane")) + " " + (index + 1);
            if (state.tab() == VisualEditorState.Tab.LAYER) {
                VisualLayout.Layer layer = state.layout().layer(index);
                label += "  " + fmt(layer.offset().y()) + "/" + fmt(layer.rotation().y()) + "°/" + fmtScale(layer.scale().y());
            } else {
                VisualLayout.Plane plane = state.layout().plane(index);
                label += "  " + fmt(plane.origin().y()) + "  " + fmt(plane.rotation().y()) + "°";
            }
            addButton(canvas, x, ry, listWidth, ROW - 1, "list:" + row, fit(canvas, label, listWidth - 4), true,
                    "editor.cannedmonster.hint.select", mouseX, mouseY, index == state.index());
        }
        if (showPager) {
            int px = x + listWidth + 2;
            addButton(canvas, px, top, width - listWidth - 2, (rows * ROW - 3) / 2, "list:prev", "<", page > 0,
                    "editor.cannedmonster.hint.page", mouseX, mouseY);
            addButton(canvas, px, top + (rows * ROW - 3) / 2 + 2, width - listWidth - 2,
                    (rows * ROW - 3) - (rows * ROW - 3) / 2, "list:next", ">", page < maxPage,
                    "editor.cannedmonster.hint.page", mouseX, mouseY);
        }
    }

    private void drawTransforms(EditorCanvas canvas, VisualEditorState state, int x, int width, int top, boolean plane, int mouseX, int mouseY) {
        int y = top + 2;
        if (state.count() <= 0) return;
        String selected = (plane ? text("editor.cannedmonster.plane") : text("editor.cannedmonster.layer")) + " " + (state.index() + 1);
        canvas.text(selected, x + 2, y, COLOR_ACCENT, false);
        if (!plane) {
            String lock = text(state.scaleLock()
                    ? "editor.cannedmonster.scale_lock" : "editor.cannedmonster.scale_free");
            addButton(canvas, x + width - 54, y - 2, 54, 11, "scale:lock", lock, true,
                    "editor.cannedmonster.hint.scale_lock", mouseX, mouseY, state.scaleLock());
        }
        y += 10;
        int labelWidth = 38;
        int cell = (width - labelWidth) / 3;
        String[] axes = {"X", "Y", "Z"};
        for (int axis = 0; axis < 3; axis++) {
            int cellX = x + labelWidth + axis * cell;
            String axisLabel = axes[axis];
            canvas.text(axisLabel, cellX + (cell - canvas.width(axisLabel)) / 2, y, COLOR_MUTED, false);
        }
        y += 9;
        String[] labels = plane
                ? new String[]{text("editor.cannedmonster.origin"), text("editor.cannedmonster.rotation")}
                : new String[]{text("editor.cannedmonster.offset"), text("editor.cannedmonster.rotation"), text("editor.cannedmonster.scale")};
        String[] hints = plane
                ? new String[]{"editor.cannedmonster.hint.origin", "editor.cannedmonster.hint.plane_rotation"}
                : new String[]{"editor.cannedmonster.hint.offset", "editor.cannedmonster.hint.rotation",
                "editor.cannedmonster.hint.scale"};
        for (int group = 0; group < labels.length; group++) {
            int rowY = y + group * ROW;
            canvas.text(fit(canvas, labels[group], labelWidth - 2), x, rowY + 3, COLOR_MUTED, false);
            for (int axis = 0; axis < 3; axis++) {
                int cellX = x + labelWidth + axis * cell;
                String value = state.editingField(group, axis) ? state.editBuffer() : value(state, plane, group, axis);
                addButton(canvas, cellX, rowY, 12, SMALL, "adj:" + group + ":" + axis + ":-1", "-", true,
                        hints[group], mouseX, mouseY);
                addField(canvas, cellX + 13, rowY, cell - 26, SMALL, "edit:" + group + ":" + axis,
                        value, state.editingField(group, axis), mouseX, mouseY);
                addButton(canvas, cellX + cell - 12, rowY, 12, SMALL, "adj:" + group + ":" + axis + ":1", "+", true,
                        hints[group], mouseX, mouseY);
            }
        }
    }

    private String value(VisualEditorState state, boolean plane, int group, int axis) {
        if (plane) {
            VisualLayout.Plane value = state.layout().plane(state.index());
            return group == 0 ? fmt(value.origin().get(axis)) : fmt(value.rotation().get(axis)) + "°";
        }
        VisualLayout.Layer value = state.layout().layer(state.index());
        return switch (group) {
            case 0 -> fmt(value.offset().get(axis));
            case 1 -> fmt(value.rotation().get(axis)) + "°";
            default -> fmtScale(value.scale().get(axis));
        };
    }

    private void drawSaveMenu(EditorCanvas canvas, VisualEditorState state, int width, int height,
                              int mouseX, int mouseY) {
        hoveredHint = null;
        canvas.fill(0, 0, width, height, 0xC0000000);
        int w = Math.min(250, width - 20);
        int buttonW = w - 16;
        int rows = 4;
        int h = 20 + rows * (SMALL + 4) + SMALL + 12;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        canvas.fill(x, y, x + w, y + h, COLOR_PANEL);
        canvas.border(x, y, w, h, COLOR_BORDER);
        canvas.text(text("editor.cannedmonster.save_menu_title"), x + 8, y + 7, COLOR_TEXT, false);
        int rowY = y + 20;
        saveOption(canvas, state, x + 8, rowY, buttonW, "save:0",
                "editor.cannedmonster.save_embedded", "editor.cannedmonster.hint.save_embedded", true, mouseX, mouseY);
        rowY += SMALL + 4;
        saveOption(canvas, state, x + 8, rowY, buttonW, "save:1",
                "editor.cannedmonster.save_item", "editor.cannedmonster.hint.save_item", true, mouseX, mouseY);
        rowY += SMALL + 4;
        saveOption(canvas, state, x + 8, rowY, buttonW, "save:2",
                "editor.cannedmonster.save_player", "editor.cannedmonster.hint.save_player", true, mouseX, mouseY);
        rowY += SMALL + 4;
        saveOption(canvas, state, x + 8, rowY, buttonW, "save:3",
                "editor.cannedmonster.save_server",
                state.admin() ? "editor.cannedmonster.hint.save_server" : "editor.cannedmonster.hint.admin_only",
                state.admin(), mouseX, mouseY);
        addButton(canvas, x + 8, y + h - SMALL - 8, buttonW, SMALL, "save:cancel",
                text("editor.cannedmonster.cancel"), true, "editor.cannedmonster.hint.cancel", mouseX, mouseY);
    }

    private void saveOption(EditorCanvas canvas, VisualEditorState state, int x, int y, int width, String id,
                            String labelKey, String hint, boolean enabled, int mouseX, int mouseY) {
        String label = text(labelKey);
        addButton(canvas, x, y, width, SMALL, id, label, enabled && !state.saving(), hint, mouseX, mouseY,
                false, id.equals("save:3") ? COLOR_ACCENT : COLOR_BOX);
    }

    private void drawConfirm(EditorCanvas canvas, int width, int height, int mouseX, int mouseY) {
        canvas.fill(0, 0, width, height, 0xC0000000);
        int w = Math.min(220, width - 20);
        int h = 64;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        canvas.fill(x, y, x + w, y + h, COLOR_PANEL);
        canvas.border(x, y, w, h, COLOR_BORDER);
        canvas.text(text("editor.cannedmonster.confirm_close"), x + 8, y + 8, COLOR_TEXT, false);
        int half = (w - 20) / 2;
        addButton(canvas, x + 8, y + h - SMALL - 8, half, SMALL, "confirm:discard",
                text("editor.cannedmonster.discard"), true, "editor.cannedmonster.hint.discard", mouseX, mouseY, false, COLOR_ERROR);
        addButton(canvas, x + 12 + half, y + h - SMALL - 8, w - 20 - half, SMALL, "confirm:cancel",
                text("editor.cannedmonster.cancel"), true, "editor.cannedmonster.hint.cancel", mouseX, mouseY);
    }

    private void addField(EditorCanvas canvas, int x, int y, int width, int height, String id,
                          String label, boolean editing, int mouseX, int mouseY) {
        Rect rect = new Rect(x, y, width, height);
        boolean hover = rect.contains(mouseX, mouseY);
        canvas.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                editing ? 0xFF1E2A3A : hover ? COLOR_BOX_HOVER : COLOR_BOX);
        canvas.border(rect.x(), rect.y(), rect.w(), rect.h(), editing ? COLOR_ACCENT : COLOR_BORDER);
        String value = label == null ? "" : label;
        if (editing) value += "|";
        String shown = fit(canvas, value, width - 4);
        canvas.text(shown, x + 2, y + (height - 8) / 2, editing ? COLOR_ACCENT : COLOR_TEXT, false);
        hits.add(new Hit(rect, id, true, "editor.cannedmonster.hint.edit"));
        if (hover && hoveredHint == null) hoveredHint = "editor.cannedmonster.hint.edit";
    }

    private void addButton(EditorCanvas canvas, int x, int y, int width, int height, String id, String label,
                           boolean enabled, String hint, int mouseX, int mouseY) {
        addButton(canvas, x, y, width, height, id, label, enabled, hint, mouseX, mouseY, false, COLOR_BOX);
    }

    private void addButton(EditorCanvas canvas, int x, int y, int width, int height, String id, String label,
                           boolean enabled, String hint, int mouseX, int mouseY, boolean selected) {
        addButton(canvas, x, y, width, height, id, label, enabled, hint, mouseX, mouseY, selected, COLOR_BOX);
    }

    private void addButton(EditorCanvas canvas, int x, int y, int width, int height, String id, String label,
                           boolean enabled, String hint, int mouseX, int mouseY, boolean selected, int baseColor) {
        Rect rect = new Rect(x, y, width, height);
        boolean hover = rect.contains(mouseX, mouseY);
        int color = !enabled ? 0xFF1C1C24 : selected ? COLOR_BOX_SELECTED : hover ? COLOR_BOX_HOVER : baseColor;
        canvas.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
        canvas.border(rect.x(), rect.y(), rect.w(), rect.h(), enabled ? COLOR_BORDER : COLOR_DISABLED);
        String labelText = fit(canvas, label, width - 2);
        int textY = y + (height - 8) / 2;
        canvas.text(labelText, x + Math.max(1, (width - canvas.width(labelText)) / 2), textY,
                enabled ? COLOR_TEXT : COLOR_DISABLED, false);
        hits.add(new Hit(rect, id, enabled, hint));
        if (hover && enabled && hoveredHint == null) hoveredHint = hint;
    }

    private static String text(String key) {
        try {
            return Component.translatable(key).getString();
        } catch (RuntimeException | LinkageError ignored) {
            // Language data is loaded by the game; unit tests and early startup show the key.
            return key;
        }
    }

    private static String messageText(Component message) {
        try {
            return message == null ? "" : message.getString();
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static String fit(EditorCanvas canvas, String text, int width) {
        if (text == null) return "";
        if (width <= 0) return "";
        if (canvas.width(text) <= width) return text;
        String ellipsis = "..";
        int end = text.length();
        while (end > 0 && canvas.width(text.substring(0, end) + ellipsis) > width) end--;
        return end <= 0 ? "" : text.substring(0, end) + ellipsis;
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String fmtScale(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int panelWidth(int width) {
        return Math.max(210, Math.min(240, width / 3));
    }
}
