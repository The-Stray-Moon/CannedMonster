package cc.sighs.cannedmonster.fusion.visual.client;

import cc.sighs.cannedmonster.fusion.visual.VisualEditorState;
import net.minecraft.client.Minecraft;

/** 1.21.1 wheel handling; the rest of the screen is shared with 1.20.1. */
public final class NeoForge121VisualEditorScreen extends LegacyVisualEditorScreen {
    public NeoForge121VisualEditorScreen(VisualEditorState state) {
        super(state);
    }

    public static void open(VisualEditorState state) {
        Minecraft.getInstance().setScreen(new NeoForge121VisualEditorScreen(state));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollZoom(mouseX, mouseY, scrollY);
    }
}
