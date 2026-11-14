package com.sqc.nuclearsq.machine.client;

import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.big.BigBattery;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.network.DeviceControlPayload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class DeviceScreen extends AbstractContainerScreen<DeviceMenu> {
    private static final int W = 256, H = 196, HB = 268;
    private static final int GREEN = TermKit.GREEN, MID = TermKit.MID, DIM = TermKit.DIM, AMBER = TermKit.AMBER;
    private static final int RED = TermKit.RED, CYAN = TermKit.CYAN, VIOLET = TermKit.VIOLET;

    private record G(Component cap, float v, float red, String num, String unit) {}
    private record B(float v, int color, Component tip) {}
    private record L(Component text, int color) {}

    private final List<G> gauges = new ArrayList<>();
    private final List<B> bars = new ArrayList<>();
    private final List<L> lines = new ArrayList<>();
    private Component foot = Component.empty();
    private int footColor = DIM;

    public DeviceScreen(DeviceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = menu.kind == DeviceMenu.BIG_BATTERY ? HB : H;
        this.titleLabelY = -100;
        this.inventoryLabelY = -100;
    }

    private boolean bigBattery() { return menu.kind == DeviceMenu.BIG_BATTERY; }

    private List<BigMachine.Port> batteryPorts() {
        List<BigMachine.Port> out = new ArrayList<>();
        BigMachine[] all = BigMachine.values();
        int ord = menu.u(11);
        if (ord >= all.length) return out;
        for (BigMachine.Port p : all[ord].ports) if (p.kind() == BigPortKind.ENERGY && out.size() < BigBattery.MAX_PORTS) out.add(p);
        return out;
    }

    private boolean portOut(int i) { return (menu.u(13 + i) & BigBattery.PORT_OUT) != 0; }

    private boolean portOn(int i) { return (menu.u(13 + i) & BigBattery.PORT_ON) != 0; }

    private Component portLabel(int i) {
        return Component.translatable("gui.nuclearsq.device.battery.port." + (portOut(i) ? "out" : "in"), i + 1);
    }

    private static final int P_X = 10, P_Y = 174, P_W = 56, P_H = 20, P_GAP = 4;
    private static final int M_X = 10, M_Y = 202, M_W = 236, M_H = 16;
    private static final int S_Y = 232, S_H = 14;

    private int thresh(int i) { return menu.u(BigBattery.THRESH_FIRST + i); }

    private void bump(int i, int delta) {
        send(DeviceControlPayload.BATTERY_THRESH, i * 128 + Mth.clamp(thresh(i) + delta, 0, 100));
    }

    private int stepper(double mx, double my) {
        for (int i = 0; i < 4; i++) if (in(mx, my, P_X + i * (P_W + P_GAP), S_Y, P_W, S_H)) return i;
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int s = bigBattery() ? stepper(mx, my) : -1;
        if (s >= 0 && sy != 0) {
            bump(s, sy > 0 ? BigBattery.STEP : -BigBattery.STEP);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= leftPos + rx && mx < leftPos + rx + rw && my >= topPos + ry && my < topPos + ry + rh;
    }

    private static void send(int action, int value) {
        PacketDistributor.sendToServer(new DeviceControlPayload(action, value));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        if ((button == 0 || button == 1) && bigBattery()) {
            int n = batteryPorts().size();
            for (int i = 0; i < n; i++) {
                if (in(mx, my, P_X + i * (P_W + P_GAP), P_Y, P_W, P_H)) {
                    if (button == 0) send(DeviceControlPayload.BATTERY_PORT, i * 2 + (portOn(i) ? 0 : 1));
                    else send(DeviceControlPayload.BATTERY_PORT_DIR, i * 2 + (portOut(i) ? 0 : 1));
                    return true;
                }
            }
        }
        if (button == 0 && bigBattery()) {
            int s = stepper(mx, my);
            if (s >= 0) {
                boolean plus = mx >= leftPos + P_X + s * (P_W + P_GAP) + P_W / 2.0;
                int step = hasShiftDown() ? 1 : BigBattery.STEP;
                bump(s, plus ? step : -step);
                return true;
            }
        }
        if (button == 0 && bigBattery() && in(mx, my, W - 52, 252, 44, 11)) {
            AccessScreen.request(menu.pos);
            return true;
        }
        if (button == 0 && bigBattery()) {
            if (in(mx, my, M_X, M_Y, M_W, M_H)) {
                send(DeviceControlPayload.BATTERY_MODE, Mth.clamp((int) (mx - leftPos - M_X) * BigBattery.MODES / M_W, 0, BigBattery.MODES - 1));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.device." + key, args);
    }

    private void add(G g) { gauges.add(g); }

    private void refresh() {
        gauges.clear();
        bars.clear();
        lines.clear();
        DeviceMenu m = menu;
        refreshBattery(m);
    }

    private void refreshBattery(DeviceMenu m) {
        int e = m.i32(0), cap = Math.max(1, m.i32(2)), in = m.i32(4), out = m.i32(6), rate = Math.max(1, m.i32(8));
        boolean held = m.u(10) == 1;
        boolean charging = (m.u(BigBattery.THRESH_FLAGS) & BigBattery.FLAG_CHARGING) != 0;
        boolean releasing = (m.u(BigBattery.THRESH_FLAGS) & BigBattery.FLAG_RELEASING) != 0;
        boolean regIn = bigBattery() && !charging && e < cap, regOut = bigBattery() && !releasing && e > 0;

        add(new G(t("battery.charge"), e / (float) cap, 2f, String.valueOf(Math.round(e * 100f / cap)), "%"));
        add(new G(t("battery.in"), in / (float) rate, 2f, String.valueOf(in), "FE/t"));
        add(new G(t("battery.out"), out / (float) rate, 2f, String.valueOf(out), "FE/t"));

        bars.add(new B(e / (float) cap, AMBER, t("battery.tip.charge")));
        bars.add(new B(in / (float) rate, GREEN, t("battery.tip.in")));
        bars.add(new B(out / (float) rate, CYAN, t("battery.tip.out")));

        int net = in - out;
        lines.add(new L(t(held ? "battery.state.held" : regIn && regOut ? "battery.state.reg_both" : regIn ? "battery.state.reg_in"
                : regOut ? "battery.state.reg_out" : net > 0 ? "battery.state.charging" : net < 0 ? "battery.state.draining" : "battery.state.idle"),
                held || regIn || regOut ? AMBER : net < 0 && e < cap / 10 ? RED : GREEN));
        lines.add(new L(t("energyline", e, cap), GREEN));
        lines.add(new L(t("battery.flow", in, out), GREEN));
        lines.add(new L(t("battery.rate", rate), GREEN));
        if (net < 0) lines.add(new L(t("battery.empty_in", Math.max(0, e / -net / 20)), e < cap / 10 ? RED : GREEN));
        else if (net > 0) lines.add(new L(t("battery.full_in", Math.max(0, (cap - e) / net / 20)), GREEN));
        foot = t(bigBattery() ? "battery.hint_big" : "battery.hint");
        footColor = DIM;
    }

    private void tip(int rx, int ry, int rw, int rh, Component... lines) {
        TermKit.tip(leftPos + rx, topPos + ry, rw, rh, lines);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        if (gauges.isEmpty()) refresh();
        int x = leftPos, y = topPos;
        TermKit.monitor(g, x - 4, y - 4, W + 8, imageHeight + 8);
        TermKit.text(g, font, title, x + 8, y + 8, GREEN);
        TermKit.rule(g, x + 8, y + 20, W - 16, DIM);

        int gw = (W - 16 - 12) / 3;
        for (int i = 0; i < gauges.size() && i < 3; i++) {
            G ga = gauges.get(i);
            int gx = 8 + i * (gw + 6);
            TermKit.gauge(g, font, x + gx, y + 26, gw, ga.cap, ga.v, ga.red, ga.num, ga.unit);
            tip(gx, 22, gw, 70, ga.cap, Component.literal(ga.num + " " + ga.unit), t("tip.gauge"));
        }

        for (int i = 0; i < bars.size(); i++) {
            B b = bars.get(i);
            TermKit.vbar(g, x + 12 + 16 * i, y + 100, 12, 64, b.v, b.color);
            tip(12 + 16 * i, 100, 12, 64, b.tip, Component.literal(Math.round(Mth.clamp(b.v, 0f, 1f) * 100) + " %"));
        }

        int tx = x + 80, ty = y + 99, tw = 166, th = 66;
        TermKit.frame(g, font, tx, ty, tw, th, null, DIM);
        for (int i = 0; i < lines.size() && i < 6; i++) {
            L l = lines.get(i);
            TermKit.text(g, font, l.text, tx + 4, ty + 3 + 10 * i, l.color);
        }

        if (bigBattery()) {
            List<BigMachine.Port> ports = batteryPorts();
            for (int i = 0; i < ports.size(); i++) {
                int bx = P_X + i * (P_W + P_GAP);
                boolean on = portOn(i);
                Component label = Component.literal("").append(portLabel(i)).append(" ").append(t(on ? "battery.on" : "battery.off"));
                TermKit.button(g, font, x + bx, y + P_Y, P_W, P_H, label, !on ? RED : portOut(i) ? CYAN : GREEN, true,
                        in(mx, my, bx, P_Y, P_W, P_H), on);
                tip(bx, P_Y, P_W, P_H, portLabel(i), Component.translatable("port.nuclearsq." + BigMachine.values()[menu.u(11)].id + "."
                        + ports.get(i).name()), t(portOut(i) ? "battery.tip.port_out" : "battery.tip.port_in", ports.get(i).rate()));
            }
            String[] cap = {"battery.reg.charge_lo", "battery.reg.charge_hi", "battery.reg.release_lo", "battery.reg.release_hi"};
            for (int i = 0; i < 4; i++) {
                int bx = P_X + i * (P_W + P_GAP);
                boolean hv = in(mx, my, bx, S_Y, P_W, S_H);
                TermKit.text(g, font, t(cap[i]), x + bx, y + S_Y - 9, MID);
                int v = thresh(i);
                TermKit.button(g, font, x + bx, y + S_Y, P_W, S_H, Component.literal("- " + v + " % +"), i < 2 ? GREEN : CYAN, true, hv, false);
                tip(bx, S_Y, P_W, S_H, t(cap[i]), t(cap[i] + ".tip"), t("battery.reg.use"));
            }
            TermKit.text(g, font, t("battery.mode_caption"), x + M_X, y + M_Y - 9, MID);
            int mode = Math.min(BigBattery.MODES - 1, menu.u(12));
            int seg = in(mx, my, M_X, M_Y, M_W, M_H) ? Mth.clamp((mx - x - M_X) * BigBattery.MODES / M_W, 0, BigBattery.MODES - 1) : -1;
            Component[] modes = new Component[BigBattery.MODES];
            for (int i = 0; i < modes.length; i++) modes[i] = t("battery.mode." + i);
            TermKit.segments(g, font, x + M_X, y + M_Y, M_W, M_H, modes, mode, mode == 0 ? GREEN : AMBER, seg);
            for (int i = 0; i < modes.length; i++) {
                tip(M_X + i * M_W / modes.length, M_Y, M_W / modes.length, M_H, modes[i], t("battery.tip.mode." + i));
            }
        }
        if (bigBattery()) {
            TermKit.button(g, font, x + W - 52, y + 252, 44, 11, Component.translatable("gui.nuclearsq.access.button"), CYAN, true,
                    in(mx, my, W - 52, 252, 44, 11), false);
            tip(W - 52, 252, 44, 11, Component.translatable("gui.nuclearsq.access.button"), Component.translatable("gui.nuclearsq.access.button.tip"));
        }
        TermKit.prompt(g, font, x + 8, y + (bigBattery() ? 254 : 180), foot, footColor);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, imageHeight + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        refresh();
        super.render(g, mx, my, pt);
        if (!TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
