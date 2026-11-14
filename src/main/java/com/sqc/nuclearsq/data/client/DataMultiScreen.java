package com.sqc.nuclearsq.data.client;

import com.sqc.nuclearsq.data.ControlWallBlockEntity;
import com.sqc.nuclearsq.data.DataEntry;
import com.sqc.nuclearsq.data.DataMultiBlockEntity;
import com.sqc.nuclearsq.data.DataMultiMenu;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DataMultiScreen extends AbstractContainerScreen<DataMultiMenu> {
    private static final int TOP = 24, ROW_H = 20, BW = 16, BH = 12;

    public DataMultiScreen(DataMultiMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 240;
        this.imageHeight = 140;
    }

    private int hit(double mx, double my, boolean wheel, int dir) {
        int r = (int) Math.floor((my - topPos - TOP) / ROW_H);
        if (r < 0 || r > DataMultiBlockEntity.ROWS || my - topPos - TOP - r * ROW_H >= BH + 4) return -1;
        if (wheel) return mx >= leftPos + 8 && mx < leftPos + imageWidth - 8 ? r * 2 + (dir > 0 ? 1 : 0) : -1;
        if (my - topPos - TOP - r * ROW_H >= BH) return -1;
        if (mx >= leftPos + 8 && mx < leftPos + 8 + BW) return r * 2;
        if (mx >= leftPos + imageWidth - 8 - BW && mx < leftPos + imageWidth - 8) return r * 2 + 1;
        return -1;
    }

    private boolean press(int id) {
        if (id < 0) return false;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && (TermKit.styleClick(mx, my) || press(hit(mx, my, false, 0)))) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return sy != 0 && press(hit(mx, my, true, sy < 0 ? 1 : -1));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8);
        ControlWallBlockEntity.Tile t = minecraft.level.getBlockEntity(menu.pos) instanceof DataMultiBlockEntity be ? be.tile() : null;
        if (t == null) TermKit.title(g, font, title, x + 8, y + 7, w - 16);
        else TermKit.headBadge(g, font, title, t.stateText(), x + 8, y + 7, w - 16, DataEntry.color(t.state()));
        TermKit.rule(g, x + 8, y + 18, w - 16, TermKit.DIM);
        int hover = hit(mx, my, false, 0), fx = x + 8 + BW + 6, fw = w - 2 * (8 + BW + 6);
        for (int r = 0; r <= DataMultiBlockEntity.ROWS; r++) {
            int ry = y + TOP + r * ROW_H;
            TermKit.button(g, font, x + 8, ry, BW, BH, Component.literal("<"), TermKit.GREEN, t != null, hover == r * 2, false);
            TermKit.button(g, font, x + w - 8 - BW, ry, BW, BH, Component.literal(">"), TermKit.GREEN, t != null, hover == r * 2 + 1, false);
            if (t == null) {
                if (r == 0) TermKit.text(g, font, Component.translatable("data.nuclearsq.screen.no_signal"), fx, ry + 2, TermKit.AMBER);
            } else if (r == 0) {
                TermKit.fit(g, font, t.name(), fx, ry + 2, fw, TermKit.GREEN);
            } else {
                ControlWallBlockEntity.Line ln = t.lines().get(r - 1);
                TermKit.field(g, font, fx, ry + 2, fw, ln.label(), Component.literal(ln.text()), TermKit.GREEN);
                if (ln.scaled()) TermKit.bar(g, fx, ry + 13, fw, 3, ln.frac(), TermKit.MID);
            }
        }
        TermKit.rule(g, x + 8, y + h - 15, w - 16, TermKit.FAINT);
        TermKit.text(g, font, Component.translatable("gui.nuclearsq.data_multi.hint"), x + 10, y + h - 12, TermKit.DIM);
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
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
