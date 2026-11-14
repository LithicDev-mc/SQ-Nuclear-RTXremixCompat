package com.sqc.nuclearsq.futurechest.client;

import com.sqc.nuclearsq.futurechest.FutureChestBlockEntity;
import com.sqc.nuclearsq.futurechest.FutureChestMenu;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class FutureChestScreen extends AbstractContainerScreen<FutureChestMenu> {
    private static final String[] UPGRADES = {"capacity", "magnet", "sorter"};

    public FutureChestScreen(FutureChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = menu.width();
        this.imageHeight = menu.height();
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.future_chest." + key, args);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if ((hoveredSlot != null && hoveredSlot.hasItem()) || !TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8);
        TermKit.header(g, font, title, t("slots", menu.capacity), x + 8, y + 7, w - 16, TermKit.MID);
        int upFirst = menu.capacity, invFirst = upFirst + FutureChestBlockEntity.UPGRADE_SLOTS;
        for (Slot s : menu.slots) {
            int color = s.index < upFirst ? (s.hasItem() ? TermKit.GREEN : TermKit.MID)
                    : s.index < invFirst ? (s.hasItem() ? TermKit.GREEN : TermKit.AMBER) : TermKit.FAINT;
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, color);
            if (s.index >= upFirst && s.index < invFirst) {
                String k = UPGRADES[Math.min(UPGRADES.length - 1, s.index - upFirst)];
                TermKit.tip(x + s.x - 1, y + s.y - 1, 18, 18, t("upgrade." + k), t("upgrade." + k + ".tip"));
            }
        }
        TermKit.rule(g, x + 8, y + menu.slots.get(invFirst).y - 6, w - 16, TermKit.FAINT);
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }
}
