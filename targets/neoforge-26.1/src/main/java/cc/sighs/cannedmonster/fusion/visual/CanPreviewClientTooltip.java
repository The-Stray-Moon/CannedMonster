package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.can.CanPreviewSelection;
import cc.sighs.cannedmonster.can.CanPreviewSource;
import cc.sighs.cannedmonster.can.CanPreviewTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Rotating tooltip image for cans on the 26.1 extraction pipeline. */
public final class CanPreviewClientTooltip implements ClientTooltipComponent {
    private static final int SIZE = 64;
    private final ItemStack stack;

    public CanPreviewClientTooltip(CanPreviewTooltip tooltip) {
        this.stack = tooltip.stack();
    }

    @Override
    public int getHeight(Font font) {
        return CanPreview.host(stack) == null ? 0 : SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return CanPreview.host(stack) == null ? 0 : SIZE;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
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
