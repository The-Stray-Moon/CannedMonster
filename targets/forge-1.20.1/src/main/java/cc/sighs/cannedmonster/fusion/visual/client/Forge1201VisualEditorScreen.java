package cc.sighs.cannedmonster.fusion.visual.client;

import cc.sighs.cannedmonster.fusion.visual.VisualEditorState;
import net.minecraft.client.Minecraft;

/** 1.20.1 wheel handling; the rest of the screen is shared with 1.21.1. */
public final class Forge1201VisualEditorScreen extends LegacyVisualEditorScreen {
    public Forge1201VisualEditorScreen(VisualEditorState state) {
        super(state);
    }

    public static void open(VisualEditorState state) {
        Minecraft.getInstance().setScreen(new Forge1201VisualEditorScreen(state));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollZoom(mouseX, mouseY, amount);
    }
}
