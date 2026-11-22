package com.sqc.nuclearsq.storage.client;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.storage.ItemPortBlockEntity;
import com.sqc.nuclearsq.storage.ItemPortMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class ItemPortScreen extends AbstractContainerScreen<ItemPortMenu> {
    private static final int ROW_Y = 22, BTN_H = 12;
    private static final int[][] BUTTONS = {{8, 76}, {88, 38}, {130, 38}};

    public ItemPortScreen(ItemPortMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = ItemPortMenu.WIDTH;
        imageHeight = menu.height();
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.item_port." + key, args);
    }

    private boolean over(int i, double mx, double my) {
        int x = leftPos + BUTTONS[i][0], y = topPos + ROW_Y;
        return mx >= x && mx < x + BUTTONS[i][1] && my >= y && my < y + BTN_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        for (int i = 0; i < BUTTONS.length; i++) {
            if (button == 0 && over(i, mx, my) && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private Component state() {
        if (!menu.enabled()) return t("state.off");
        String p = menu.problem();
        if (p.isEmpty()) return Component.translatable("message.nuclearsq.item_port.ok", menu.moved());
        return Component.translatable("message.nuclearsq.item_port.problem." + p);
    }

    private int stateColor() {
        if (!menu.enabled()) return TermKit.MID;
        String p = menu.problem();
        return p.isEmpty() ? TermKit.GREEN : p.equals("idle") ? TermKit.MID : TermKit.AMBER;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8, 20);
        TermKit.header(g, font, title, Component.empty(), x + 8, y + 7, w - 16, TermKit.MID);
        Component mode = t(menu.exporting() ? "mode.export" : "mode.import");
        Component[] labels = {mode, t(menu.enabled() ? "on" : "off"), t("clear")};
        Component[] tips = {t("mode.tip"), t("enable.tip"), t("clear.tip")};
        for (int i = 0; i < BUTTONS.length; i++) {
            boolean active = i == 0 ? menu.exporting() : i == 1 && menu.enabled();
            TermKit.button(g, font, x + BUTTONS[i][0], y + ROW_Y, BUTTONS[i][1], BTN_H, labels[i], TermKit.CYAN, true, over(i, mx, my), active);
            TermKit.tip(x + BUTTONS[i][0], y + ROW_Y, BUTTONS[i][1], BTN_H, labels[i], tips[i]);
        }
        Component filter = menu.hasFilter() ? t("filter") : t(menu.exporting() ? "filter.none_export" : "filter.none_import");
        TermKit.fit(g, font, filter, x + 8, y + ItemPortMenu.FILTER_Y - 11, w - 16, TermKit.MID);
        for (int i = 0; i < ItemPortBlockEntity.FILTER_SLOTS; i++) {
            Slot s = menu.slots.get(i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.CYAN);
            TermKit.tip(x + s.x - 1, y + s.y - 1, 18, 18, t("slot"), t("slot.tip"));
        }
        TermKit.fit(g, font, state(), x + 8, y + ItemPortMenu.FILTER_Y + 22, w - 16, stateColor());
        TermKit.rule(g, x + 8, y + ItemPortMenu.INV_Y - 6, w - 16, TermKit.FAINT);
        for (int i = ItemPortBlockEntity.FILTER_SLOTS; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.FAINT);
        }
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if ((hoveredSlot != null && hoveredSlot.hasItem()) || !TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
