package cc.sighs.cannedmonster.fusion.visual.client;

import cc.sighs.cannedmonster.can.CanPreviewSelection;
import cc.sighs.cannedmonster.can.CanPreviewSource;
import cc.sighs.cannedmonster.can.CanPreviewTooltip;
import cc.sighs.cannedmonster.fusion.visual.CanPreview;
import cc.sighs.cannedmonster.fusion.visual.VisualEditorProjection;
import cc.sighs.cannedmonster.fusion.visual.VisualLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Rotating tooltip image for cans; shared by the 1.20.1 and 1.21.1 targets. */
public final class CanPreviewClientTooltip implements ClientTooltipComponent {
    private static final int SIZE = 64;
    private final ItemStack stack;

    public CanPreviewClientTooltip(CanPreviewTooltip tooltip) {
        this.stack = tooltip.stack();
    }

    @Override
    public int getHeight() {
        return CanPreview.host(stack) == null ? 0 : SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return CanPreview.host(stack) == null ? 0 : SIZE;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        LivingEntity host = CanPreview.host(stack);
        if (host == null) return;
        VisualLayout layout = CanPreviewSource.layout(stack);
        float yaw = Math.floorMod(CanPreviewSelection.nowMillis(), CanPreviewSelection.ROTATE_MILLIS)
                * 360.0F / CanPreviewSelection.ROTATE_MILLIS;
        VisualEditorProjection.Frame frame = new VisualEditorProjection.Frame(x + 2, y + 2, x + SIZE - 2, y + SIZE - 2);
        VisualEditorProjection.Camera camera = VisualEditorProjection.camera(frame.left(), frame.top(),
                frame.right(), frame.bottom(), Math.max(0.01F, host.getBbWidth()),
                Math.max(0.01F, host.getBbHeight()), yaw, -8.0F, 1.0F);
        CanPreview.draw(graphics, host, frame, camera, layout);
    }
}
