package com.sqc.nuclearsq.crate.client;

import com.sqc.nuclearsq.crate.StorageCrateBlockEntity;
import com.sqc.nuclearsq.crate.StorageCrateMenu;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class StorageCrateScreen extends AbstractContainerScreen<StorageCrateMenu> {
    private static final int B = 16, NET_Y = 42;
    private static final int[][] NET = {{1, 1}, {2, 2}, {0, 1}, {2, 1}, {1, 0}, {1, 2}};
    private static final int[] COLORS = {TermKit.DIM, TermKit.CYAN, TermKit.AMBER, TermKit.GREEN};

    public StorageCrateScreen(StorageCrateMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = menu.width();
        imageHeight = menu.height();
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.storage_crate." + key, args);
    }

    private int sideX(int rel) {
        return leftPos + menu.panelX() + NET[rel][0] * 18;
    }

    private int sideY(int rel) {
        return topPos + NET_Y + NET[rel][1] * 18;
    }

    private int ejectY() {
        return topPos + NET_Y + 3 * 18;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        if ((button == 0 || button == 1) && minecraft != null && minecraft.gameMode != null) {
            for (int rel = 0; rel < StorageCrateBlockEntity.SIDES; rel++) {
                if (in(mx, my, sideX(rel), sideY(rel), B, B)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, rel + (button == 1 ? 6 : 0));
                    return true;
                }
            }
            if (button == 0 && in(mx, my, leftPos + menu.panelX(), ejectY(), 52, 12)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StorageCrateMenu.BTN_EJECT);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if ((hoveredSlot != null && hoveredSlot.hasItem()) || !TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8, 20);
        int used = 0;
        for (int i = 0; i < menu.capacity; i++) if (menu.slots.get(i).hasItem()) used++;
        TermKit.header(g, font, title, t("slots", used, menu.capacity), x + 8, y + 7, w - 16, TermKit.MID);
        int inv = menu.capacity + 1;
        for (Slot s : menu.slots) {
            int color = s.index < menu.capacity ? (s.hasItem() ? TermKit.GREEN : TermKit.MID)
                    : s.index < inv ? (s.hasItem() ? TermKit.GREEN : TermKit.AMBER) : TermKit.FAINT;
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, color);
        }
        Slot up = menu.slots.get(menu.capacity);
        TermKit.tip(x + up.x - 1, y + up.y - 1, 18, 18, t("upgrade"), t("upgrade.tip"));
        TermKit.text(g, font, t("upgrade.short"), x + up.x + 20, y + up.y + 5, TermKit.MID);
        for (int rel = 0; rel < StorageCrateBlockEntity.SIDES; rel++) {
            int mode = menu.mode(rel), bx = sideX(rel), by = sideY(rel);
            boolean hover = in(mx, my, bx, by, B, B);
            TermKit.button(g, font, bx, by, B, B, t("side." + rel + ".short"), COLORS[mode], true, hover, mode != StorageCrateBlockEntity.OFF);
            TermKit.tip(bx, by, B, B, t("side." + rel), t("mode." + mode), t("side.tip"));
        }
        int ex = x + menu.panelX(), ey = ejectY();
        TermKit.button(g, font, ex, ey, 52, 12, t("eject"), menu.eject() ? TermKit.GREEN : TermKit.DIM, true, in(mx, my, ex, ey, 52, 12), menu.eject());
        TermKit.tip(ex, ey, 52, 12, t("eject"), t(menu.eject() ? "eject.on" : "eject.off"), t("eject.tip", StorageCrateBlockEntity.EJECT));
        TermKit.rule(g, x + 8, y + menu.invY() - 6, w - 16, TermKit.FAINT);
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }
}
