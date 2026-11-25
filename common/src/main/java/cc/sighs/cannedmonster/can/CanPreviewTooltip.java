package cc.sighs.cannedmonster.can;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Tooltip payload for a can preview; the client factory owns the actual rendering. */
public record CanPreviewTooltip(ItemStack stack) implements TooltipComponent {
}
