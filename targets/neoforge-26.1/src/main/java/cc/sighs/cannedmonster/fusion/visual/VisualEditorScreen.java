package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * 26.1 editor screen. Rendering uses the extraction pipeline, so only this adapter and its
 * canvas differ from the legacy screen; the editor layout is the shared {@link VisualEditorView}.
 */
public final class VisualEditorScreen extends Screen {
    private final VisualEditorState state;
    private final VisualEditorView view = new VisualEditorView();

    public VisualEditorScreen(VisualEditorState state) {
        super(Component.translatable("editor.cannedmonster.title"));
        this.state = state;
    }

    public static void open(VisualEditorState state) {
        Minecraft.getInstance().setScreen(new VisualEditorScreen(state));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
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
            graphics.text(font, empty, preview.x() + (preview.w() - font.width(empty)) / 2,
                    preview.y() + preview.h() / 2 - 4, 0xFF9AA0B4, false);
        }
        view.draw(new Canvas(graphics), state, width, height, mouseX, mouseY, previewInfo(host, camera));
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static VisualEditorProjection.Frame frame(VisualEditorView.Rect rect) {
        return new VisualEditorProjection.Frame(rect.x(), rect.y(), rect.right(), rect.bottom());
    }

    private static VisualEditorView.PreviewInfo previewInfo(LivingEntity host, VisualEditorProjection.Camera camera) {
        return host == null || camera == null ? null : new VisualEditorView.PreviewInfo(
                Math.max(0.01F, host.getBbWidth()), Math.max(0.01F, host.getBbHeight()), camera);
    }

    private LivingEntity host() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack stack = player.getItemInHand(state.hand());
        return stack.isEmpty() ? null : CanPreview.host(stack);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        VisualEditorView.Action action = view.click(state, event.x(), event.y(), event.hasShiftDown());
        if (action == VisualEditorView.Action.CLOSE) {
            closeEditor();
            return true;
        }
        if (view.mouseOverPreview(event.x(), event.y(), width, height)) {
            view.beginDrag(state, event.x(), event.y(), width, height);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && view.dragMode() != VisualEditorView.Drag.NONE) {
            view.drag(state, event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) view.endDrag();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        view.scroll(state, mouseX, mouseY, scrollY, width, height);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (state.editing()) {
            if (event.isEscape()) {
                state.cancelEdit();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                state.commitEdit();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                state.backspaceEdit();
                return true;
            }
            return true;
        }
        if (event.isEscape() && state.confirming()) {
            state.cancelClose();
            return true;
        }
        if (event.isEscape() && state.saveMenu()) {
            state.closeSaveMenu();
            return true;
        }
        if (state.saveMenu() || state.confirming()) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_R) {
            state.resetView();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_LEFT) { state.rotateBy(-10.0, 0.0); return true; }
        if (event.key() == GLFW.GLFW_KEY_RIGHT) { state.rotateBy(10.0, 0.0); return true; }
        if (event.key() == GLFW.GLFW_KEY_UP) { state.rotateBy(0.0, -10.0); return true; }
        if (event.key() == GLFW.GLFW_KEY_DOWN) { state.rotateBy(0.0, 10.0); return true; }
        if (event.key() == GLFW.GLFW_KEY_EQUAL || event.key() == GLFW.GLFW_KEY_KP_ADD) {
            state.zoomBy(0.15F);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_MINUS || event.key() == GLFW.GLFW_KEY_KP_SUBTRACT) {
            state.zoomBy(-0.15F);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE) {
            state.resetUnit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (state.editing()) {
            state.editChar((char) event.codepoint());
            return true;
        }
        return super.charTyped(event);
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
        private final GuiGraphicsExtractor graphics;

        private Canvas(GuiGraphicsExtractor graphics) {
            this.graphics = graphics;
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int color) {
            graphics.fill(x0, y0, x1, y1, color);
        }

        @Override
        public void text(String text, int x, int y, int color, boolean shadow) {
            graphics.text(font, text, x, y, color, shadow);
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
