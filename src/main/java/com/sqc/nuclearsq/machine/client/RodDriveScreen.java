package com.sqc.nuclearsq.machine.client;

import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.CoreChemistry;
import com.sqc.nuclearsq.machine.big.DriveCore;
import com.sqc.nuclearsq.network.DeviceControlPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class RodDriveScreen extends AbstractContainerScreen<DeviceMenu> {
    private static final int W = 320, H = 368;
    private static final int ROD_MAX = BigMachineBlockEntity.ROD_MAX, CHANNELS = BigMachineBlockEntity.CHANNELS;

    private static final int GREEN = TermKit.GREEN, MID = TermKit.MID, DIM = TermKit.DIM, FAINT = TermKit.FAINT;
    private static final int AMBER = TermKit.AMBER, RED = TermKit.RED, CYAN = TermKit.CYAN, ROD = TermKit.VIOLET;

    private static final int CARDS = 9, CARD_W = 98, CARD_H = 48, CARD_DX = 103, CARD_DY = 52, CARD_X0 = 8, CARD_Y0 = 64;
    private static final int BTN_DX = 22, BTN_DY = 33, BTN_W = 72, BTN_H = 13;

    private static final int V_Y = 221, V_ROW = 237, V_W = 71, V_GAP = 7;

    private static final int K_Y = 260, K_ROW = 276;
    private static final int SC_X = 8 + 3 * (V_W + V_GAP), SC_W = V_W, SC_H = 18;

    private static final int C_Y = 298;
    private static final int RB_X = 8, RB_Y = 313, RB_W = 150, RB_H = 18;
    private static final int RM_X = 164, RM_Y = 313, RM_W = 88, RM_H = 18;
    private static final int RC_X = 258, RC_Y = 313, RC_W = 54, RC_H = 18;
    private static final int RS_X = 8, RS_Y = 337, RS_W = 304, RS_H = 14;

    private boolean dragging;
    private int localTarget = -1;

    public RodDriveScreen(DeviceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.device.rod." + key, args);
    }

    private static void send(int action, int value) {
        PacketDistributor.sendToServer(new DeviceControlPayload(action, value));
    }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= leftPos + rx && mx < leftPos + rx + rw && my >= topPos + ry && my < topPos + ry + rh;
    }

    private int seg(double mx, int rx, int rw, int n) {
        return Mth.clamp((int) (mx - leftPos - rx) * n / rw, 0, n - 1);
    }

    private int target() {
        return dragging && localTarget >= 0 ? localTarget : menu.u(2);
    }

    private void tip(int rx, int ry, int rw, int rh, Component... lines) {
        TermKit.tip(leftPos + rx, topPos + ry, rw, rh, lines);
    }

    private static String num(int v) {
        return v >= 1000 ? String.format("%.1fk", v / 1000f) : String.valueOf(v);
    }

    private static final int V = com.sqc.nuclearsq.machine.DeviceMenu.ROD_VESSEL;

    private boolean has(int s) { return menu.u(12 + 4 * s) == 1; }
    private boolean locked(int s) { return menu.u(13 + 4 * s) == 1; }
    private int depth(int s) { return menu.u(14 + 4 * s); }
    private int open(int s) { return menu.u(15 + 4 * s); }

    private static int shaftOf(int c) { return CARDS - 1 - c; }
    private static int rodNo(int c) { return (2 - c / 3) * 3 + c % 3 + 1; }
    private int cardX(int c) { return CARD_X0 + c % 3 * CARD_DX; }
    private int cardY(int c) { return CARD_Y0 + c / 3 * CARD_DY; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (TermKit.styleClick(mx, my)) return true;
        for (int c = 0; c < CARDS; c++) {
            int s = shaftOf(c);
            if (in(mx, my, cardX(c) + BTN_DX, cardY(c) + BTN_DY, BTN_W, BTN_H)) {
                if (has(s)) send(DeviceControlPayload.ROD_LOCK, s * 2 + (locked(s) ? 0 : 1));
                return true;
            }
        }
        if (in(mx, my, SC_X, K_ROW, SC_W, SC_H)) {
            send(DeviceControlPayload.SCRAM, 0);
            return true;
        }
        if (in(mx, my, RB_X, RB_Y, RB_W, RB_H)) {
            int cur = menu.u(4) == 1 ? menu.u(2) : menu.u(1);
            int v = switch (seg(mx, RB_X, RB_W, 4)) {
                case 0 -> 0;
                case 1 -> cur - 1;
                case 2 -> cur + 1;
                default -> ROD_MAX;
            };
            send(DeviceControlPayload.ROD_TARGET, Mth.clamp(v, 0, ROD_MAX));
            return true;
        }
        if (in(mx, my, RM_X, RM_Y, RM_W, RM_H)) {
            send(DeviceControlPayload.ROD_MODE, seg(mx, RM_X, RM_W, 2));
            return true;
        }
        if (in(mx, my, RC_X, RC_Y, RC_W, RC_H)) {
            int s = seg(mx, RC_X, RC_W, 3);
            if (s != 1) send(DeviceControlPayload.ROD_CHANNEL, Mth.clamp(menu.u(5) + (s == 0 ? -1 : 1), 0, CHANNELS));
            return true;
        }
        if (in(mx, my, RS_X, RS_Y - 2, RS_W, RS_H + 4)) {
            dragging = true;
            drag(mx);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void drag(double mx) {
        int v = Mth.clamp((int) Math.round((mx - leftPos - RS_X - 5) * ROD_MAX / (double) (RS_W - 10)), 0, ROD_MAX);
        if (v != localTarget) {
            localTarget = v;
            send(DeviceControlPayload.ROD_TARGET, v);
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            drag(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            localTarget = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void txt(GuiGraphics g, Component c, int x, int y, int color) {
        TermKit.text(g, font, c, x, y, color);
    }

    private void stat(GuiGraphics g, int x, int y, int w, Component label, String value, float frac, int color) {
        TermKit.field(g, font, x, y, w, label, Component.literal(value), color);
        TermKit.bar(g, x, y + 11, w, 7, frac, color);
    }

    private void card(GuiGraphics g, int c, int mx, int my) {
        int s = shaftOf(c), x = leftPos + cardX(c), y = topPos + cardY(c);
        boolean has = has(s), locked = locked(s);
        int depth = depth(s), open = open(s);
        int accent = !has ? DIM : !locked ? AMBER : depth > 0 ? GREEN : CYAN;
        TermKit.frame(g, font, x, y, CARD_W, CARD_H, t("card", rodNo(c)), accent);

        int gx = x + 6, gy = y + 8, gw = 12, gh = 36;
        TermKit.fill(g, gx, gy + 4, gx + 1, gy + gh, DIM);
        TermKit.fill(g, gx + gw - 1, gy + 4, gx + gw, gy + gh, DIM);
        TermKit.fill(g, gx, gy + gh - 1, gx + gw, gy + gh, DIM);
        for (int yy = gy + 8; yy < gy + gh - 2; yy += 6) TermKit.fill(g, gx + 2, yy, gx + gw - 2, yy + 1, FAINT);
        if (has) {
            int rodH = 14, inside = Math.round((gh - 6) * depth / 100f);
            int top = gy + 4 + inside - rodH;
            int bot = top + rodH;
            g.fill(gx + 3, Math.max(y + 2, top), gx + gw - 3, bot, (TermKit.col(ROD) & 0x00FFFFFF) | 0x80000000);
            TermKit.fill(g, gx + 4, Math.max(y + 2, top + 2), gx + gw - 4, bot - 1, ROD);
            TermKit.fill(g, gx + 2, Math.max(y + 2, top - 2), gx + gw - 2, Math.max(y + 2, top + 1), MID);
        }
        int gap = 1 + Math.round(3 * open / 100f);
        int jaw = open < 100 && open > 0 ? AMBER : open == 0 ? CYAN : MID;
        TermKit.fill(g, gx - 1, gy + 2, gx + gw / 2 - gap + 1, gy + 5, jaw);
        TermKit.fill(g, gx + gw / 2 + gap - 1, gy + 2, gx + gw + 1, gy + 5, jaw);

        String state = !has ? "state.empty" : !locked ? (depth > 0 ? "state.releasing" : "state.released")
                : open > 0 ? "state.opening" : depth > 0 ? "state.inserted" : "state.clamped";
        int sc = !has ? DIM : !locked ? AMBER : open > 0 ? AMBER : depth > 0 ? GREEN : CYAN;
        txt(g, t(state), x + 22, y + 13, sc);
        if (has) txt(g, t("depth", depth), x + 22, y + 23, MID);
        tip(cardX(c), cardY(c), CARD_W, BTN_DY, Component.empty().append(t("card", rodNo(c))).append(" · ").append(t("where." + s)),
                t(state), has ? t("tip.card", depth, open) : t("tip.empty"), t("tip.card2"));

        boolean hover = in(mx, my, cardX(c) + BTN_DX, cardY(c) + BTN_DY, BTN_W, BTN_H);
        TermKit.button(g, font, x + BTN_DX, y + BTN_DY, BTN_W, BTN_H, locked ? t("btn.release") : t("btn.lock"),
                locked ? AMBER : GREEN, has, hover && has, false);
        tip(cardX(c) + BTN_DX, cardY(c) + BTN_DY, BTN_W, BTN_H, locked ? t("btn.release") : t("btn.lock"),
                !has ? t("tip.empty") : locked ? t("tip.release") : t("tip.lock"));
    }

    private void vessel(GuiGraphics g) {
        int x = leftPos, y = topPos;
        int water = menu.u(V), waterCap = Math.max(1, menu.u(V + 1)), steam = menu.u(V + 2), steamCap = Math.max(1, menu.u(V + 3));
        int mb = menu.u(V + 4), temp = menu.u(V + 5), want = menu.u(V + 7);
        boolean dry = menu.u(V + 6) == 1;
        Component inName = Component.translatable("port.nuclearsq.control_rod_drive.primary_cold_in");
        Component outName = Component.translatable("port.nuclearsq.control_rod_drive.primary_hot_out");

        TermKit.rule(g, x + 8, y + V_Y, W - 16, DIM);
        txt(g, t("vessel"), x + 8, y + V_Y + 4, MID);
        tip(8, V_Y + 2, 150, 11, t("vessel"), t("tip.vessel"), t("tip.vessel2"));
        Component st = dry ? t("v.dry") : mb > 0 ? t("v.hot", mb) : t("v.cold");
        TermKit.right(g, font, st, x + W - 8, y + V_Y + 4, dry ? RED : mb > 0 ? GREEN : DIM);
        int sw = TermKit.width(font, st);
        if (dry) tip(W - 8 - sw, V_Y + 2, sw, 11, t("tip.v.dry"), t("tip.v.dry2"));

        int cx = 8;
        stat(g, x + cx, y + V_ROW, V_W, t("v.water"), num(water), water / (float) waterCap, dry ? RED : CYAN);
        tip(cx, V_ROW - 1, V_W, 20, t("tip.v.water", water, waterCap), t("tip.v.water2", inName));
        cx += V_W + V_GAP;
        stat(g, x + cx, y + V_ROW, V_W, t("v.steam"), num(steam), steam / (float) steamCap, steam >= steamCap ? AMBER : GREEN);
        tip(cx, V_ROW - 1, V_W, 20, t("tip.v.steam", steam, steamCap), t("tip.v.steam2", outName));
        cx += V_W + V_GAP;
        stat(g, x + cx, y + V_ROW, V_W, t("v.rate"), String.valueOf(mb), want > 0 ? mb / (float) want : 0f, mb > 0 ? GREEN : DIM);
        tip(cx, V_ROW - 1, V_W, 20, t("tip.v.rate", mb, want), t("tip.v.rate2"));
        cx += V_W + V_GAP;
        stat(g, x + cx, y + V_ROW, V_W, t("v.temp"), temp + "°C", temp / 400f, dry ? RED : temp >= 340 ? AMBER : GREEN);
        tip(cx, V_ROW - 1, V_W, 20, t("tip.v.temp", temp), t("tip.v.temp2"));
    }

    private void core(GuiGraphics g, int mx, int my) {
        int x = leftPos, y = topPos;
        int heat = menu.u(V + 8), cool = menu.u(V + 9), power = menu.u(V + 10), left = menu.u(V + 12);
        boolean over = menu.u(V + 11) == 1;
        int redZone = Math.round(DriveCore.RED_ZONE * 100);

        TermKit.rule(g, x + 8, y + K_Y, W - 16, DIM);
        txt(g, t("core"), x + 8, y + K_Y + 4, MID);
        tip(8, K_Y + 2, 150, 11, t("core"), t("tip.core"), t("tip.core2"));
        Component st = left > 0 ? t("k.meltdown", (left + 19) / 20) : over ? t("k.overheated") : heat >= redZone ? t("k.hot") : t("k.ok");
        TermKit.right(g, font, st, x + W - 8, y + K_Y + 4, left > 0 || over ? RED : heat >= redZone ? AMBER : GREEN);
        int xe = menu.u(V + 13), filter = menu.u(V + 14), gas = menu.u(V + 15);
        boolean red = xe >= CoreChemistry.XE_WARN || gas >= CoreChemistry.GAS_ALARM;
        if (left <= 0) txt(g, t("k.xenon", xe, gas), x + 84, y + K_Y + 4, red ? RED : xe > CoreChemistry.XE_FREE || gas >= CoreChemistry.GAS_WARN ? AMBER : GREEN);
        tip(84, K_Y + 2, 140, 11, t("k.xenon", xe, gas), t("tip.k.xenon", xe, filter), t("tip.k.xenon2"), t("tip.k.gas", gas), t("tip.k.gas2"));

        int cx = 8;
        stat(g, x + cx, y + K_ROW, V_W, t("k.heat"), heat + "%", heat / 100f, over || heat >= redZone ? RED : heat >= 50 ? AMBER : GREEN);
        tip(cx, K_ROW - 1, V_W, 20, t("tip.k.heat", heat), t("tip.k.heat2"));
        cx += V_W + V_GAP;
        stat(g, x + cx, y + K_ROW, V_W, t("k.cooling"), String.format("%.2f", cool / 100f), Math.min(1f, cool / 140f), CYAN);
        tip(cx, K_ROW - 1, V_W, 20, t("tip.k.cooling", String.format("%.2f", cool / 100f)), t("tip.k.cooling2"));
        cx += V_W + V_GAP;
        stat(g, x + cx, y + K_ROW, V_W, t("k.power"), String.valueOf(power), power / (float) DriveCore.MAX_POWER, over ? RED : power > 0 ? GREEN : DIM);
        tip(cx, K_ROW - 1, V_W, 20, t("tip.k.power", power, DriveCore.MAX_POWER), t("tip.k.power2"));
        boolean scrammed = menu.u(4) == 1 && menu.u(2) == 0;
        TermKit.button(g, font, x + SC_X, y + K_ROW, SC_W, SC_H, t("btn.scram"), RED, true, in(mx, my, SC_X, K_ROW, SC_W, SC_H), scrammed);
        tip(SC_X, K_ROW, SC_W, SC_H, t("btn.scram"), t("tip.scram"), t("tip.scram2"));
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        boolean formed = menu.u(0) == 1, terminal = menu.u(4) == 1, powered = menu.u(10) == 1;
        int rod = menu.u(1), signal = menu.u(3), channel = menu.u(5), group = menu.u(6);
        int hyd = menu.u(7), hydMax = Math.max(1, menu.u(8)), energy = menu.u(9);
        int want = terminal ? target() : signal;

        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8);
        String st = !formed ? "head.unformed" : !powered ? "head.nopower"
                : rod != want ? (want > rod ? "head.in" : "head.out") : "head.hold";
        int sc = !formed || !powered ? RED : rod != want ? AMBER : GREEN;
        TermKit.headBadge(g, font, title, t(st), x + 8, y + 8, W - 16, sc);
        tip(W - 100, 4, 92, 14, t(st), t("tip.head"));
        TermKit.rule(g, x + 8, y + 20, W - 16, DIM);

        stat(g, x + 8, y + 27, 98, t("position"), rod + "/" + ROD_MAX, rod / (float) ROD_MAX, GREEN);
        tip(8, 26, 98, 20, t("tip.position"), t("tip.position2", rod, ROD_MAX, want));
        stat(g, x + 111, y + 27, 98, t("hydraulic"), Math.round(hyd * 100f / hydMax) + "%", hyd / (float) hydMax,
                hyd <= 0 ? RED : CYAN);
        tip(111, 26, 98, 20, t("tip.hydraulic"), t("tip.hydraulic2", hyd, hydMax,
                Component.translatable("port.nuclearsq.control_rod_drive.hydraulic_in")));
        stat(g, x + 214, y + 27, 98, t("power"), energy + "%", energy / 100f, !powered ? RED : AMBER);
        tip(214, 26, 98, 20, t("tip.power", energy), t("tip.power2"));

        Component src = terminal ? t("source.terminal") : t("source.redstone", signal);
        txt(g, t("shafts"), x + 8, y + 50, MID);
        TermKit.right(g, font, src, x + W - 8, y + 50, DIM);
        int srw = TermKit.width(font, src);
        tip(W - 8 - srw, 49, srw, 10, src, t(terminal ? "tip.mode.terminal" : "tip.mode.redstone"));
        for (int c = 0; c < CARDS; c++) card(g, c, mx, my);

        vessel(g);
        core(g, mx, my);

        TermKit.rule(g, x + 8, y + C_Y, W - 16, DIM);
        txt(g, t("controls"), x + 8, y + C_Y + 4, MID);
        Component grp = channel == 0 ? t("single") : t("group", channel, group);
        TermKit.right(g, font, grp, x + W - 8, y + C_Y + 4, channel == 0 ? DIM : GREEN);
        int hb = in(mx, my, RB_X, RB_Y, RB_W, RB_H) ? seg(mx, RB_X, RB_W, 4) : -1;
        TermKit.segments(g, font, x + RB_X, y + RB_Y, RB_W, RB_H,
                new Component[] {t("btn.out"), Component.literal("-"), Component.literal("+"), t("btn.in")}, -1, GREEN, hb);
        String[] segTips = {"out", "minus", "plus", "in"};
        for (int i = 0; i < 4; i++) {
            tip(RB_X + i * RB_W / 4, RB_Y, RB_W / 4, RB_H, t("tip.seg." + segTips[i]), t("tip.seg2"));
        }
        int hm = in(mx, my, RM_X, RM_Y, RM_W, RM_H) ? seg(mx, RM_X, RM_W, 2) : -1;
        TermKit.segments(g, font, x + RM_X, y + RM_Y, RM_W, RM_H, new Component[] {t("mode.redstone"), t("mode.terminal")},
                terminal ? 1 : 0, terminal ? GREEN : AMBER, hm);
        tip(RM_X, RM_Y, RM_W / 2, RM_H, t("mode.redstone"), t("tip.mode.redstone"));
        tip(RM_X + RM_W / 2, RM_Y, RM_W / 2, RM_H, t("mode.terminal"), t("tip.mode.terminal"));
        int hc = in(mx, my, RC_X, RC_Y, RC_W, RC_H) ? seg(mx, RC_X, RC_W, 3) : -1;
        TermKit.segments(g, font, x + RC_X, y + RC_Y, RC_W, RC_H, new Component[] {Component.literal("<"),
                Component.literal(channel == 0 ? "-" : String.valueOf(channel)), Component.literal(">")}, -1, GREEN, hc == 1 ? -1 : hc);
        tip(RC_X, RC_Y, RC_W, RC_H, t("tip.channel", channel), t("tip.channel2"));
        tip(W - 120, C_Y + 2, 112, 11, grp, t("tip.channel2"));

        int tg = target();
        TermKit.slider(g, font, x + RS_X, y + RS_Y, RS_W, RS_H, ROD_MAX, tg / (float) ROD_MAX, t("slider", tg, ROD_MAX));
        tip(RS_X, RS_Y - 2, RS_W, RS_H + 4, t("tip.slider"), t("tip.slider2"));

        TermKit.prompt(g, font, x + 8, y + H - 12, t("hint2"), DIM);
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
