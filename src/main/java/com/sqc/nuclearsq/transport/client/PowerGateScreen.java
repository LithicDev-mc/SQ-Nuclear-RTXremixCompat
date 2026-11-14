package com.sqc.nuclearsq.transport.client;

import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.network.DeviceControlPayload;
import com.sqc.nuclearsq.transport.PowerGateBlockEntity;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class PowerGateScreen extends AbstractContainerScreen<DeviceMenu> {
    private static final int W = 256, H = 176, N = 5;
    private static final int KNOB_X = 10, KNOB_Y = 130, KNOB_W = 44, KNOB_H = 14, KNOB_GAP = 4;
    private static final String[] KEYS = {"rate", "charge_lo", "charge_hi", "release_lo", "release_hi"};

    public PowerGateScreen(DeviceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelY = -100;
        this.inventoryLabelY = -100;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.gate." + key, args);
    }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= leftPos + rx && mx < leftPos + rx + rw && my >= topPos + ry && my < topPos + ry + rh;
    }

    private int knob(double mx, double my) {
        for (int i = 0; i < N; i++) if (in(mx, my, KNOB_X + i * (KNOB_W + KNOB_GAP), KNOB_Y, KNOB_W, KNOB_H)) return i;
        return -1;
    }

    private int value(int i) { return menu.u(i); }

    private void bump(int i, int delta) {
        int min = i == 0 ? PowerGateBlockEntity.STEP : 0;
        PacketDistributor.sendToServer(new DeviceControlPayload(DeviceControlPayload.GATE_SET, i * 128 + Mth.clamp(value(i) + delta, min, 100)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        int k = button == 0 ? knob(mx, my) : -1;
        if (k >= 0) {
            boolean plus = mx >= leftPos + KNOB_X + k * (KNOB_W + KNOB_GAP) + KNOB_W / 2.0;
            int step = hasShiftDown() ? 1 : PowerGateBlockEntity.STEP;
            bump(k, plus ? step : -step);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int k = knob(mx, my);
        if (k >= 0 && sy != 0) {
            bump(k, sy > 0 ? PowerGateBlockEntity.STEP : -PowerGateBlockEntity.STEP);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8);
        TermKit.text(g, font, title, x + 8, y + 8, TermKit.GREEN);
        TermKit.rule(g, x + 8, y + 20, W - 16, TermKit.DIM);

        int stored = menu.i32(5), cap = PowerGateBlockEntity.CAPACITY, moved = menu.i32(8);
        boolean charging = (menu.u(7) & PowerGateBlockEntity.FLAG_CHARGING) != 0, releasing = (menu.u(7) & PowerGateBlockEntity.FLAG_RELEASING) != 0;
        float fill = stored / (float) cap;
        TermKit.vbar(g, x + 14, y + 30, 14, 84, fill, TermKit.AMBER);
        TermKit.tip(x + 14, y + 30, 14, 84, t("buffer"), Component.literal(Math.round(Mth.clamp(fill, 0f, 1f) * 100) + " %"));

        int ty = y + 30;
        TermKit.text(g, font, t("buffer.line", stored, cap), x + 40, ty, TermKit.GREEN);
        TermKit.text(g, font, t("flow", moved), x + 40, ty + 12, moved > 0 ? TermKit.CYAN : TermKit.DIM);
        TermKit.text(g, font, t(charging ? "in.open" : "in.closed"), x + 40, ty + 28, charging ? TermKit.GREEN : TermKit.AMBER);
        TermKit.text(g, font, t(releasing ? "out.open" : "out.closed"), x + 40, ty + 40, releasing ? TermKit.GREEN : TermKit.AMBER);
        TermKit.text(g, font, t("limit", value(0), Math.max(1, (int) ((long) PowerGateBlockEntity.MAX_RATE * value(0) / 100))),
                x + 40, ty + 56, TermKit.MID);

        for (int i = 0; i < N; i++) {
            int bx = KNOB_X + i * (KNOB_W + KNOB_GAP);
            boolean hv = in(mx, my, bx, KNOB_Y, KNOB_W, KNOB_H);
            TermKit.text(g, font, t(KEYS[i]), x + bx, y + KNOB_Y - 9, TermKit.MID);
            TermKit.button(g, font, x + bx, y + KNOB_Y, KNOB_W, KNOB_H, Component.literal("- " + value(i) + "% +"),
                    i == 0 ? TermKit.VIOLET : i < 3 ? TermKit.GREEN : TermKit.CYAN, true, hv, false);
            TermKit.tip(x + bx, y + KNOB_Y, KNOB_W, KNOB_H, t(KEYS[i]), t(KEYS[i] + ".tip"), t("use"));
        }
        TermKit.prompt(g, font, x + 8, y + 156, t("hint"), TermKit.DIM);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, H + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (!TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
