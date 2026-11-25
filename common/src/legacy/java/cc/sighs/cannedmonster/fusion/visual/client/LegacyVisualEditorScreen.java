package cc.sighs.cannedmonster.fusion.visual.client;

import cc.sighs.cannedmonster.fusion.visual.CanPreview;
import cc.sighs.cannedmonster.fusion.visual.EditorCanvas;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorClient;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorProjection;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorState;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Editor screen shared by the 1.20.1 and 1.21.1 targets, whose Screen/GuiGraphics input and
 * drawing contracts are the same. All layout work lives in {@link VisualEditorView}.
 */
public class LegacyVisualEditorScreen extends Screen {
    private final VisualEditorState state;
    private final VisualEditorView view = new VisualEditorView();

    protected LegacyVisualEditorScreen(VisualEditorState state) {
        super(Component.translatable("editor.cannedmonster.title"));
        this.state = state;
    }

    public static void open(VisualEditorState state) {
        Minecraft.getInstance().setScreen(new LegacyVisualEditorScreen(state));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0101018);
        VisualEditorView.Rect preview = view.preview(width, height);
        VisualEditorView.Rect content = view.previewContent(width, height);
        LivingEntity host = host();
        VisualEditorProjection.Camera camera = null;
        if (host != null) {
            camera = CanPreview.frame(host, state.layout(), frame(content), state.yaw(), state.pitch(),
                    state.zoom(), VisualEditorView.PREVIEW_MARGIN);
            CanPreview.draw(graphics, host, frame(preview), camera, state.layout());
        } else {
            String empty = Component.translatable("editor.cannedmonster.no_preview").getString();
            graphics.drawString(font, empty, preview.x() + (preview.w() - font.width(empty)) / 2,
                    preview.y() + preview.h() / 2 - 4, 0xFF9AA0B4, false);
        }
        view.draw(new Canvas(graphics), state, width, height, mouseX, mouseY, previewInfo(host, camera));
    }

    private static VisualEditorProjection.Frame frame(VisualEditorView.Rect rect) {
        return new VisualEditorProjection.Frame(rect.x(), rect.y(), rect.right(), rect.bottom());
    }

    protected boolean scrollZoom(double mouseX, double mouseY, double amount) {
        view.scroll(state, mouseX, mouseY, amount, width, height);
        return true;
    }

    protected static VisualEditorView.PreviewInfo previewInfo(LivingEntity host, VisualEditorProjection.Camera camera) {
        return host == null || camera == null ? null : new VisualEditorView.PreviewInfo(
                Math.max(0.01F, host.getBbWidth()), Math.max(0.01F, host.getBbHeight()), camera);
    }

    protected LivingEntity host() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack stack = player.getItemInHand(state.hand());
        return stack.isEmpty() ? null : CanPreview.host(stack);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        VisualEditorView.Action action = view.click(state, mouseX, mouseY, hasShiftDown());
        if (action == VisualEditorView.Action.CLOSE) {
            closeEditor();
            return true;
        }
        if (view.mouseOverPreview(mouseX, mouseY, width, height)) {
            view.beginDrag(state, mouseX, mouseY, width, height);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && view.dragMode() != VisualEditorView.Drag.NONE) {
            view.drag(state, mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) view.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state.editing()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                state.cancelEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                state.commitEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                state.backspaceEdit();
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && state.confirming()) {
            state.cancelClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && state.saveMenu()) {
            state.closeSaveMenu();
            return true;
        }
        if (state.saveMenu() || state.confirming()) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_R) {
            state.resetView();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) { state.rotateBy(-10.0, 0.0); return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) { state.rotateBy(10.0, 0.0); return true; }
        if (keyCode == GLFW.GLFW_KEY_UP) { state.rotateBy(0.0, -10.0); return true; }
        if (keyCode == GLFW.GLFW_KEY_DOWN) { state.rotateBy(0.0, 10.0); return true; }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            state.zoomBy(0.15F);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            state.zoomBy(-0.15F);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            state.resetUnit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (state.editing()) {
            state.editChar(character);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public void onClose() {
        if (state.editing()) {
            state.cancelEdit();
            return;
        }
        if (state.saveMenu()) {
            state.closeSaveMenu();
            return;
        }
        if (state.confirming()) {
            state.cancelClose();
            return;
        }
        if (state.dirty()) {
            state.requestClose();
            return;
        }
        closeEditor();
    }

    @Override
    public void tick() {
        super.tick();
        state.tick();
    }

    private void closeEditor() {
        Minecraft.getInstance().setScreen(null);
        VisualEditorClient.closed(state.session());
    }

    private final class Canvas implements EditorCanvas {
        private final GuiGraphics graphics;

        private Canvas(GuiGraphics graphics) {
            this.graphics = graphics;
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int color) {
            graphics.fill(x0, y0, x1, y1, color);
        }

        @Override
        public void text(String text, int x, int y, int color, boolean shadow) {
            graphics.drawString(font, text, x, y, color, shadow);
        }

        @Override
        public int width(String text) {
            return font.width(text);
        }

        @Override
        public void scissor(int x0, int y0, int x1, int y1) {
            graphics.enableScissor(x0, y0, x1, y1);
        }

        @Override
        public void unscissor() {
            graphics.disableScissor();
        }
    }
}
