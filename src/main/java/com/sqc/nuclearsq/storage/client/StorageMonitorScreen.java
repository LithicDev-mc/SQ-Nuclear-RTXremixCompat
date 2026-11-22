package com.sqc.nuclearsq.storage.client;

import java.util.List;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.storage.StorageFormat;
import com.sqc.nuclearsq.storage.StorageMonitorBlockEntity;
import com.sqc.nuclearsq.storage.StorageMonitorMenu;
import com.sqc.nuclearsq.storage.StorageRegistry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class StorageMonitorScreen extends AbstractContainerScreen<StorageMonitorMenu> {
    private static final int W = 300, H = 204, ROW = 26, LIST_Y = 60, TAB_W = 120, SEG_X = 120, SEG_W = 168, SET_H = 26;
    private static boolean display;
    private int scroll;

    public StorageMonitorScreen(StorageMonitorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.storage.monitor." + key, args);
    }

    private static Component rack(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.storage.rack." + key, args);
    }

    private StorageMonitorBlockEntity be() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof StorageMonitorBlockEntity be ? be : null;
    }

    private int tabX() {
        return leftPos + imageWidth - 8 - TAB_W;
    }

    private int visible() {
        return (imageHeight - LIST_Y - 6) / ROW;
    }

    private int maxScroll(StorageMonitorBlockEntity be) {
        return be == null ? 0 : Math.max(0, be.rows().size() - visible());
    }

    private static Component[] options(int kind) {
        return switch (kind) {
            case StorageMonitorBlockEntity.SORT -> new Component[] {t("sort.0"), t("sort.1"), t("sort.2")};
            case StorageMonitorBlockEntity.PAGE -> new Component[] {t("page.value", 5), t("page.value", 10), t("page.value", 30)};
            case StorageMonitorBlockEntity.HIDE -> new Component[] {t("hide.0"), t("hide.1")};
            default -> new Component[] {Component.literal("90 %"), Component.literal("80 %"), t("warn.off")};
        };
    }

    private static final String[] NAMES = {"sort", "page", "hide", "warn"};

    private int segY(int kind) {
        return topPos + 30 + kind * (SET_H + 4) + 7;
    }

    private int segment(int kind, double mx, double my) {
        int x = leftPos + SEG_X, y = segY(kind), n = options(kind).length;
        if (mx < x || mx >= x + SEG_W || my < y || my >= y + 12) return -1;
        return Math.min(n - 1, (int) ((mx - x) * n / SEG_W));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (TermKit.styleClick(mx, my)) return true;
            if (my >= topPos + 4 && my < topPos + 16 && mx >= tabX() && mx < tabX() + TAB_W) {
                display = mx >= tabX() + TAB_W / 2.0;
                return true;
            }
            if (display && minecraft != null && minecraft.gameMode != null) {
                for (int k = 0; k < NAMES.length; k++) {
                    int s = segment(k, mx, my);
                    if (s >= 0) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, k * 4 + s);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (!display) {
            scroll = Math.max(0, Math.min(maxScroll(be()), scroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private static int barColor(float f, float warn) {
        return f >= warn ? TermKit.RED : f >= warn - 0.15f ? TermKit.AMBER : TermKit.CYAN;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.recipes(new ItemStack(StorageRegistry.MONITOR.get()));
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8, 20);
        TermKit.title(g, font, title, x + 8, y + 7, w - 24 - TAB_W);
        int hover = my >= y + 4 && my < y + 16 && mx >= tabX() && mx < tabX() + TAB_W ? (mx >= tabX() + TAB_W / 2 ? 1 : 0) : -1;
        TermKit.segments(g, font, tabX(), y + 4, TAB_W, 12, new Component[] {t("tab.racks"), t("tab.display")}, display ? 1 : 0, TermKit.AMBER, hover);
        StorageMonitorBlockEntity be = be();
        if (be == null) return;
        if (display) renderDisplay(g, be, x, y, w, mx, my);
        else renderRacks(g, be, x, y, w, h);
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
    }

    private void renderRacks(GuiGraphics g, StorageMonitorBlockEntity be, int x, int y, int w, int h) {
        long used = be.used(), cap = be.capacity();
        float f = cap <= 0 ? 0 : Math.min(1f, used / (float) cap), warn = be.warnFrom();
        TermKit.frame(g, font, x + 6, y + 24, w - 12, 32, null, TermKit.DIM);
        TermKit.text(g, font, t("total", StorageFormat.count(used), StorageFormat.count(cap)), x + 10, y + 28, TermKit.GREEN);
        TermKit.right(g, font, Component.literal(Math.round(f * 100) + " %"), x + w - 10, y + 28, TermKit.MID);
        TermKit.bar(g, x + 10, y + 38, w - 20, 4, f, barColor(f, warn));
        int on = be.online(), all = be.rows().size();
        TermKit.text(g, font, t("powered", on, all), x + 10, y + 45, on < all ? TermKit.AMBER : TermKit.MID);
        TermKit.right(g, font, rack("space.types", be.types(), be.typeCapacity()), x + w - 10, y + 45, TermKit.MID);
        List<StorageMonitorBlockEntity.Row> rows = be.sorted(true);
        if (rows.isEmpty()) {
            TermKit.center(g, font, t("no_racks"), x + w / 2, y + LIST_Y + 30, TermKit.AMBER);
            return;
        }
        scroll = Math.min(scroll, maxScroll(be));
        int ry = y + LIST_Y, rw = w - 20;
        for (int i = scroll; i < Math.min(rows.size(), scroll + visible()); i++) {
            StorageMonitorBlockEntity.Row r = rows.get(i);
            TermKit.frame(g, font, x + 6, ry, rw, ROW - 3, null, TermKit.DIM);
            Component prio = rack("priority", r.priority());
            Component state = r.online() ? t("on") : rack("offline");
            int sideW = Math.max(font.width(prio), font.width(state)) + 8;
            Component pos = Component.literal("X " + r.pos().getX() + "  Y " + r.pos().getY() + "  Z " + r.pos().getZ());
            Component name = rack(r.machine());
            int nameW = Math.min(font.width(name), rw - sideW - 12);
            TermKit.fit(g, font, name, x + 10, ry + 3, nameW, r.online() ? TermKit.GREEN : TermKit.MID);
            TermKit.fit(g, font, pos, x + 16 + nameW, ry + 3, rw - sideW - nameW - 14, TermKit.DIM);
            TermKit.right(g, font, state, x + 6 + rw - 4, ry + 3, r.online() ? TermKit.GREEN : TermKit.RED);
            TermKit.right(g, font, prio, x + 6 + rw - 4, ry + 13, TermKit.CYAN);
            TermKit.tip(x + 6 + rw - sideW, ry + 12, sideW, 10, prio, rack("priority.tip"));
            Component amount = r.drives() == 0 ? t("empty") : Component.literal(StorageFormat.count(r.used()) + " / " + StorageFormat.count(r.capacity()));
            Component drives = rack("drives", r.drives(), r.slots());
            int barW = rw - sideW - font.width(amount) - font.width(drives) - 30;
            TermKit.bar(g, x + 10, ry + 15, Math.max(20, barW), 4, r.frac(), barColor(r.frac(), warn));
            TermKit.text(g, font, amount, x + 16 + Math.max(20, barW), ry + 13, r.online() ? TermKit.GREEN : TermKit.DIM);
            TermKit.right(g, font, drives, x + 6 + rw - sideW - 2, ry + 13, TermKit.MID);
            ry += ROW;
        }
        int max = maxScroll(be);
        if (max > 0) {
            int th = visible() * ROW - 3;
            TermKit.vbar(g, x + w - 11, y + LIST_Y, 4, th, 1f - scroll / (float) max, TermKit.MID);
        }
    }

    private void renderDisplay(GuiGraphics g, StorageMonitorBlockEntity be, int x, int y, int w, int mx, int my) {
        for (int k = 0; k < NAMES.length; k++) {
            int cy = y + 30 + k * (SET_H + 4);
            TermKit.frame(g, font, x + 6, cy, w - 12, SET_H, null, TermKit.DIM);
            TermKit.fit(g, font, t(NAMES[k]), x + 12, cy + 9, SEG_X - 16, TermKit.GREEN);
            TermKit.segments(g, font, x + SEG_X, segY(k), SEG_W, 12, options(k), be.setting(k), TermKit.AMBER, segment(k, mx, my));
        }
        TermKit.wrap(g, font, t("note"), x + 10, y + 30 + NAMES.length * (SET_H + 4) + 4, w - 20, 2, TermKit.MID);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        TermKit.renderTip(g, font, mx, my);
    }
}
