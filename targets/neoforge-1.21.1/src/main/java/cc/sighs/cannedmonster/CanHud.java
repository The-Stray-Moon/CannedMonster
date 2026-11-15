package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.can.CanContents;
import cc.sighs.cannedmonster.can.MonsterCanItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class CanHud implements LayeredDraw.Layer {
    public static final CanHud INSTANCE = new CanHud();

    private CanHud() {
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof MonsterCanItem can) || !CanContents.filled(stack)) return;
        Component text;
        if (can.storesFusion()) {
            text = Component.translatable("tooltip.cannedmonster.hud_count", CanContents.count(stack));
        } else {
            String name = CanContents.name(stack);
            if (name.isEmpty()) return;
            text = Component.translatable("tooltip.cannedmonster.hud_single", name);
        }
        graphics.drawString(minecraft.font, text, (graphics.guiWidth() - minecraft.font.width(text)) / 2,
                graphics.guiHeight() - 45, 0xFFFFFF, true);
    }
}
