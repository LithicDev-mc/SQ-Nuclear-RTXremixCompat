package com.sqc.nuclearsq.turbine.client;

import com.sqc.nuclearsq.machine.EnergyUnits;
import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.network.TurbineControlPayload;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;
import com.sqc.nuclearsq.turbine.TurbineMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class TurbineScreen extends AbstractContainerScreen<TurbineMenu> {
    private static final int W = 300, H = 236;
    private static final int GREEN = TermKit.GREEN, MID = TermKit.MID, DIM = TermKit.DIM, FAINT = TermKit.FAINT;
    private static final int AMBER = TermKit.AMBER, RED = TermKit.RED, CYAN = TermKit.CYAN;

    private static final int RUN_X = 8, RUN_Y = 178, RUN_W = 120, RUN_H = 16;
    private static final int SL_X = 8, SL_Y = 204, SL_W = 284, SL_H = 14;

    private boolean dragging = false;
    private int localThrottle = -1;

    public TurbineScreen(TurbineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.turbine." + key, args);
    }

    private int throttle() {
        return dragging && localThrottle >= 0 ? localThrottle : menu.throttle();
    }

    private void send(boolean run, int thr) {
        PacketDistributor.sendToServer(new TurbineControlPayload(run, Mth.clamp(thr, 0, 100)));
    }

    private void tip(int rx, int ry, int rw, int rh, Component... lines) {
        TermKit.tip(leftPos + rx, topPos + ry, rw, rh, lines);
    }

    private void gauge(GuiGraphics g, int x, int y, int w, Component label, float value, float redFrom, String num, String unit) {
        TermKit.gauge(g, font, x, y, w, label, value, redFrom, num, unit);
    }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= leftPos + rx && mx < leftPos + rx + rw && my >= topPos + ry && my < topPos + ry + rh;
    }

    private void dragTo(double mx) {
        int v = Mth.clamp((int) Math.round((mx - leftPos - SL_X - 5) * 100.0 / (SL_W - 10)), 0, 100);
        if (v != localThrottle) {
            localThrottle = v;
            send(menu.running(), v);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        if (button == 0 && in(mx, my, RUN_X, RUN_Y, RUN_W, RUN_H)) {
            boolean on = (int) (mx - leftPos - RUN_X) * 2 / RUN_W >= 1;
            if (on != menu.running()) send(on, throttle());
            return true;
        }
        if (button == 0 && in(mx, my, SL_X, SL_Y - 2, SL_W, SL_H + 4)) {
            dragging = true;
            dragTo(mx);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            dragTo(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            localThrottle = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void stat(GuiGraphics g, int x, int y, int w, Component label, String value, float frac, int color) {
        TermKit.field(g, font, x, y, w, label, Component.literal(value), color);
        TermKit.bar(g, x, y + 11, w, 6, frac, color);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        TurbineMenu m = menu;
        boolean live = m.formed() && m.running() && m.mbNow() > 0;
        float maxFe = TurbineControllerBlockEntity.MAX_MB * TurbineControllerBlockEntity.SUPER_FE_PER_MB;

        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8);
        String state = !m.formed() ? "head.incomplete" : !m.running() ? "head.stopped" : live ? "head.running" : "head.idle";
        int sc = !m.formed() ? RED : !m.running() ? DIM : live ? GREEN : AMBER;
        TermKit.headBadge(g, font, t("title"), t(state), x + 8, y + 8, W - 16, sc);
        tip(W - 110, 4, 102, 14, t(state), t("tip." + state));
        TermKit.rule(g, x + 8, y + 20, W - 16, DIM);

        int gw = (W - 16 - 12) / 3;
        gauge(g, x + 8, y + 26, gw, t("g.speed"), m.rpm() / (float) TurbineControllerBlockEntity.MAX_RPM, 0.85f,
                String.valueOf(m.rpm()), "U/min");
        gauge(g, x + 14 + gw, y + 26, gw, t("g.power"), m.feNow() / maxFe, 0.9f, String.valueOf(m.feNow()), "FE/t");
        gauge(g, x + 20 + 2 * gw, y + 26, gw, t("g.temp"), m.temp() / 320f, 0.8f, String.valueOf(m.temp()), "°C");
        tip(8, 22, gw, 70, t("g.speed"), t("tip.g.speed", m.rpm(), TurbineControllerBlockEntity.MAX_RPM));
        tip(14 + gw, 22, gw, 70, t("g.power"), t("tip.g.power", m.feNow(), Math.round(maxFe)));
        tip(20 + 2 * gw, 22, gw, 70, t("g.temp"), t("tip.g.temp", m.temp()));

        int fw = (W - 16 - 8) / 2;
        stat(g, x + 8, y + 100, fw, t("f.steam"), m.steam() + "/" + TurbineControllerBlockEntity.TANK + " mB",
                m.steam() / (float) TurbineControllerBlockEntity.TANK, CYAN);
        stat(g, x + 16 + fw, y + 100, fw, t("f.storage"), m.energy() + "/" + TurbineControllerBlockEntity.CAP + " FE",
                m.energy() / (float) TurbineControllerBlockEntity.CAP, AMBER);
        stat(g, x + 8, y + 124, fw, t("f.load"), EnergyUnits.su(m.feNow()) + " SU", m.feNow() / maxFe, GREEN);
        stat(g, x + 16 + fw, y + 124, fw, t("f.blocks"), m.matched() + "/" + m.total(),
                m.matched() / (float) Math.max(1, m.total()), m.formed() ? GREEN : RED);
        tip(8, 99, fw, 20, t("f.steam"), t("tip.f.steam", m.steam(), TurbineControllerBlockEntity.TANK));
        tip(16 + fw, 99, fw, 20, t("f.storage"), t("tip.f.storage", m.energy(), TurbineControllerBlockEntity.CAP));
        tip(8, 123, fw, 20, t("f.load"), t("tip.f.load", EnergyUnits.su(m.feNow()), m.feNow()));
        tip(16 + fw, 123, fw, 20, t("f.blocks"), t("tip.f.blocks", m.matched(), m.total()));
        TermKit.field(g, font, x + 8, y + 148, fw, t("f.flow"), Component.literal(m.mbNow() + " mB/t"), live ? GREEN : DIM);
        boolean shaft = m.shaft() > 0;
        TermKit.field(g, font, x + 16 + fw, y + 148, fw, t(shaft ? "f.shaft" : "f.out"),
                Component.literal((shaft ? m.shaft() : m.output()) + " FE/t"), shaft || m.output() > 0 ? GREEN : DIM);
        tip(8, 147, fw, 10, t("f.flow"), t("tip.f.flow", m.mbNow()));
        if (shaft) tip(16 + fw, 147, fw, 10, t("f.shaft"), t("tip.f.shaft", m.shaft()), t("tip.f.out", m.output()));
        else tip(16 + fw, 147, fw, 10, t("f.out"), t("tip.f.out", m.output()), t("tip.f.out2"));

        TermKit.rule(g, x + 8, y + 163, W - 16, DIM);
        TermKit.text(g, font, t("controls"), x + 8, y + 167, MID);
        int hr = in(mx, my, RUN_X, RUN_Y, RUN_W, RUN_H) ? Mth.clamp((int) (mx - leftPos - RUN_X) * 2 / RUN_W, 0, 1) : -1;
        TermKit.segments(g, font, x + RUN_X, y + RUN_Y, RUN_W, RUN_H,
                new Component[] {Component.translatable("gui.nuclearsq.device.off"), Component.translatable("gui.nuclearsq.device.on")},
                m.running() ? 1 : 0, m.running() ? GREEN : AMBER, hr);
        tip(RUN_X, RUN_Y, RUN_W / 2, RUN_H, Component.translatable("gui.nuclearsq.device.off"), t("tip.off"));
        tip(RUN_X + RUN_W / 2, RUN_Y, RUN_W / 2, RUN_H, Component.translatable("gui.nuclearsq.device.on"), t("tip.on"));
        TermKit.right(g, font, t("throttle_label"), x + W - 8, y + RUN_Y + 4, DIM);

        int thr = throttle();
        TermKit.slider(g, font, x + SL_X, y + SL_Y, SL_W, SL_H, 20, thr / 100f, t("throttle", thr));
        tip(SL_X, SL_Y - 2, SL_W, SL_H + 4, t("tip.throttle"), t("tip.throttle2"));

        TermKit.prompt(g, font, x + 8, y + H - 12, t(m.formed() ? "ok" : "build"), m.formed() ? DIM : RED);
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
