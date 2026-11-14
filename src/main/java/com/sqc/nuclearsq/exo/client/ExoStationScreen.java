package com.sqc.nuclearsq.exo.client;

import com.sqc.nuclearsq.exo.ExoChamber;
import com.sqc.nuclearsq.exo.ExoStationMenu;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class ExoStationScreen extends AbstractContainerScreen<ExoStationMenu> {
    private static final int W = ExoStationMenu.WIDTH, H = ExoStationMenu.HEIGHT;

    public ExoStationScreen(ExoStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.exo_station." + key, args);
    }

    private void tip(int x, int y, int w, int h, Component... lines) {
        TermKit.tip(leftPos + x, topPos + y, w, h, lines);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return (button == 0 && TermKit.styleClick(mx, my)) || super.mouseClicked(mx, my, button);
    }

    private void slotFrame(GuiGraphics g, Slot s, int color) {
        TermKit.frame(g, font, leftPos + s.x - 1, topPos + s.y - 1, 18, 18, null, color);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, inner = W - 16;
        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8);
        int tier = menu.data(6), slots = menu.data(7);
        TermKit.header(g, font, title, tier > 0 ? t("tier", tier) : Component.empty(), x + 8, y + 7, inner, TermKit.MID);

        boolean piece = menu.getSlot(ExoStationMenu.PIECE).hasItem();
        int stored = menu.data32(2), max = menu.data32(4);
        String state = !piece ? "no_piece" : tier == 0 ? "no_core" : stored >= max ? "full" : "charging";
        TermKit.wrap(g, font, t("state." + state), x + 8, y + 19, inner, 2, state.equals("charging") ? TermKit.AMBER : state.equals("full") ? TermKit.GREEN : TermKit.DIM);

        TermKit.field(g, font, x + 8, y + 41, inner, t("core_charge"), tier > 0 ? t("energy", stored, max) : Component.literal("-"), TermKit.CYAN);
        TermKit.bar(g, x + 8, y + 52, inner, 4, max > 0 ? Math.min(1f, stored / (float) max) : 0f, TermKit.CYAN);
        tip(8, 40, inner, 17, t("core_charge.title"), t("core_charge.tip"));
        TermKit.field(g, font, x + 8, y + 62, inner, t("buffer"), t("energy", menu.data32(0), ExoChamber.BUFFER), TermKit.MID);
        tip(8, 61, inner, 11, t("buffer.title"), t("buffer.tip"));

        TermKit.rule(g, x + 8, y + 74 - 3, inner, TermKit.FAINT);
        TermKit.fit(g, font, t("piece"), x + 8, y + ExoStationMenu.PIECE_Y - 10, 24, TermKit.MID);
        TermKit.fit(g, font, t("core"), x + ExoStationMenu.CORE_X, y + ExoStationMenu.PIECE_Y - 10, 40, TermKit.MID);
        slotFrame(g, menu.getSlot(ExoStationMenu.PIECE), piece ? TermKit.GREEN : TermKit.MID);
        slotFrame(g, menu.getSlot(ExoStationMenu.CORE), tier > 0 ? TermKit.GREEN : TermKit.MID);
        tip(ExoStationMenu.PIECE_X - 1, ExoStationMenu.PIECE_Y - 1, 18, 18, t("piece.title"), t("piece.tip"));
        tip(ExoStationMenu.CORE_X - 1, ExoStationMenu.PIECE_Y - 1, 18, 18, t("core.title"), t("core.tip"));

        TermKit.fit(g, font, t("modules", slots), x + 8, y + ExoStationMenu.MOD_Y - 11, inner, TermKit.MID);
        for (int i = 0; i < ExoChamber.MODULE_MAX; i++) {
            Slot s = menu.getSlot(ExoStationMenu.MODULE_FIRST + i);
            slotFrame(g, s, i >= slots ? TermKit.FAINT : s.hasItem() ? TermKit.GREEN : TermKit.MID);
        }
        tip(ExoStationMenu.MOD_X - 1, ExoStationMenu.MOD_Y - 1, ExoChamber.MODULE_MAX * 18, 18, t("modules.title"), t("modules.tip"));

        TermKit.rule(g, x + 8, y + ExoStationMenu.INV_Y - 5, inner, TermKit.FAINT);
        for (int i = ExoStationMenu.INV_FIRST; i < menu.slots.size(); i++) slotFrame(g, menu.slots.get(i), TermKit.FAINT);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, H + 8);
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
