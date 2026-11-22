package com.sqc.nuclearsq.machine.client;

import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.network.DeviceControlPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class BigMachineScreen extends AbstractContainerScreen<DeviceMenu> {
    private static final int W = 340;
    private int scroll;
    private static final int GREEN = TermKit.GREEN, MID = TermKit.MID, DIM = TermKit.DIM;
    private static final int AMBER = TermKit.AMBER, RED = TermKit.RED, CYAN = TermKit.CYAN;

    private static final int PORT_Y = 78, PORT_H = 24, DUMP_W = 54, DUMP_H = 12;
    private static final int SEG_W = 120, SEG_H = 16;

    public BigMachineScreen(DeviceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = height();
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.device.big." + key, args);
    }

    private static void send(int action, int value) {
        PacketDistributor.sendToServer(new DeviceControlPayload(action, value));
    }

    private BigMachine machine() {
        BigMachine[] all = BigMachine.values();
        return all[Mth.clamp(menu.u(12), 0, all.length - 1)];
    }

    private static final int[] FUSION_PORTS = BigMachineBlockEntity.FUSION_PORTS;

    private boolean fusion() {
        return machine() == BigMachine.FUSION_REACTOR;
    }

    private int portY() {
        return PORT_Y + (fusion() ? FX_H : 0);
    }

    private int ports() {
        return BigMachineBlockEntity.termRows(machine());
    }

    private int pi(int i) {
        return fusion() ? FUSION_PORTS[i] : i;
    }

    private int rows() {
        return height == 0 ? ports() : Mth.clamp((height - 24 - portY() - 52) / PORT_H, 1, ports());
    }

    private int height() {
        return portY() + rows() * PORT_H + 52;
    }

    private int controlsY() {
        return portY() + rows() * PORT_H + 8;
    }

    private boolean emergency() {
        return machine().isEmergency();
    }

    private boolean steamGenerator() {
        return machine() == BigMachine.STEAM_GENERATOR;
    }

    private int base(int port) { return BigMachineBlockEntity.TERM_PORTS + BigMachineBlockEntity.PORT_VALUES * port; }
    private int amount(int port) { return menu.i32(base(port)); }
    private int capacity(int port) { return menu.i32(base(port) + 2); }

    private void tip(int rx, int ry, int rw, int rh, Component... lines) {
        TermKit.tip(leftPos + rx, topPos + ry, rw, rh, lines);
    }

    private static Component k(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.term." + key, args);
    }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= leftPos + rx && mx < leftPos + rx + rw && my >= topPos + ry && my < topPos + ry + rh;
    }

    private int seg(double mx, int rx, int rw, int n) {
        return Mth.clamp((int) (mx - leftPos - rx) * n / rw, 0, n - 1);
    }

    private static final int FX_H = 112, HIST = 96, LOG = 4, EX = BigMachineBlockEntity.FUSION_EXTRA;
    private static final int[] FX_COLORS = {CYAN, AMBER, TermKit.VIOLET, TermKit.VIOLET, TermKit.VIOLET, RED};
    private static net.minecraft.core.BlockPos fxPos;
    private static final short[] histCharge = new short[HIST], histTemp = new short[HIST];
    private static final java.util.ArrayDeque<Component> fxLog = new java.util.ArrayDeque<>();
    private static int histN, fxTicks, fxIgnite, fxStage, fxStep, fxBurnMax;
    private static boolean fxPowered;

    private int phase() { return menu.u(BigMachineBlockEntity.FUSION_DATA + 6); }
    private int permille() { return menu.u(EX); }
    private int draw() { return menu.i32(EX + 1); }

    private int stage() {
        int pm = permille();
        return switch (phase()) {
            case 1 -> pm < 600 ? 0 : pm < 850 ? 1 : 2;
            case 2 -> fxTicks - fxIgnite < 40 ? 3 : 4;
            case 3 -> 5;
            default -> -1;
        };
    }

    private static void fxLog(Component c) {
        int s = fxTicks / 20;
        fxLog.addLast(Component.literal(String.format("%02d:%02d ", s / 60, s % 60)).append(c));
        while (fxLog.size() > LOG) fxLog.removeFirst();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!fusion() || menu.u(0) != 1) return;
        if (!menu.pos.equals(fxPos)) {
            fxPos = menu.pos;
            histN = fxTicks = fxBurnMax = 0;
            fxStage = fxStep = -2;
            fxIgnite = -1000;
            fxLog.clear();
            fxLog(t("fx.log.connect"));
        }
        if (phase() == 2 && fxStage >= 0 && fxStage < 3) fxIgnite = fxTicks;
        int st = stage(), step = phase() == 1 ? permille() / 100 : -1;
        if (st != fxStage) {
            if (fxStage != -2 || st >= 0) fxLog(t("fx.log." + (st == -1 && fxStage == 4 ? "end" : "s" + st)));
            fxStage = st;
        } else if (step > fxStep && fxStep >= 0) {
            fxLog(t("fx.log.step", step * 10));
        }
        fxStep = step;
        boolean powered = phase() != 1 || draw() > 0;
        if (!powered && fxPowered) fxLog(t("fx.log.nopower"));
        fxPowered = powered;
        fxBurnMax = phase() == 2 ? Math.max(fxBurnMax, menu.u(EX + 3)) : 0;
        if (fxTicks++ % 10 == 0) {
            if (histN == HIST) {
                System.arraycopy(histCharge, 1, histCharge, 0, HIST - 1);
                System.arraycopy(histTemp, 1, histTemp, 0, HIST - 1);
                histN--;
            }
            histCharge[histN] = (short) (phase() == 2 ? 1000 : permille());
            histTemp[histN++] = (short) menu.u(4);
        }
    }

    private static int blend(int a, int b, float p) {
        int ca = TermKit.col(a), cb = TermKit.col(b), out = 0xFF000000;
        for (int sh = 0; sh <= 16; sh += 8) out |= Math.round((ca >> sh & 255) + ((cb >> sh & 255) - (ca >> sh & 255)) * p) << sh;
        return out;
    }

    private static String fe(double v) {
        return v >= 1e9 ? String.format("%.2f G", v / 1e9) : v >= 1e6 ? String.format("%.1f M", v / 1e6)
                : v >= 1e3 ? String.format("%.0f k", v / 1e3) : String.format("%.0f ", v);
    }

    private static String time(int s) {
        return s >= 6000 ? "--:--" : String.format("%02d:%02d", s / 60, s % 60);
    }

    private void fusionPanel(GuiGraphics g) {
        int x = leftPos + 8, y = topPos + PORT_Y - 12, w = W - 16, st = stage(), pm = permille(), draw = draw();
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 140.0);
        boolean plasma = phase() == 2;
        int accent = FX_COLORS[Math.max(0, st)];
        TermKit.caption(g, font, t("fx.title"), x, y);
        double cost = menu.u(EX + 4) * 1e6, done = cost * pm / 1000.0;
        Component head = st < 0 ? t("fx.ready") : phase() == 1 ? (draw > 0 ? t("fx.eta", time((int) ((cost - done) / draw / 20))) : t("fx.eta_none"))
                : st == 5 ? t("fx.phase.5") : t("fx.burn", String.format("%.1f", menu.u(EX + 3) / 20f));
        TermKit.right(g, font, head, x + w, y, st < 0 ? DIM : blend(accent, TermKit.FAINT, 0.6f * pulse));
        int sy = y + 11, segW = w / 5;
        for (int i = 0; i < 5; i++) {
            int sx = x + i * segW, sw = i == 4 ? w - 4 * segW : segW - 2;
            boolean now = i == st || st == 5, past = st > i && st != 5;
            int bg = st == 5 ? blend(RED, TermKit.FAINT, pulse) : now ? blend(FX_COLORS[i], TermKit.FAINT, 0.5f * pulse) : TermKit.col(past ? DIM : TermKit.FAINT);
            g.fill(sx, sy, sx + sw, sy + 13, bg);
            TermKit.center(g, font, t("fx.phase." + (st == 5 ? 5 : i)), sx + sw / 2, sy + 3, now ? TermKit.SCREEN : past ? GREEN : DIM);
        }
        int by = y + 28, cells = 20, cw = (w - 44) / cells;
        float frac = plasma ? menu.u(EX + 3) / (float) Math.max(1, fxBurnMax) : pm / 1000f;
        int lit = (int) (frac * cells);
        for (int i = 0; i < cells; i++) {
            int c = plasma ? TermKit.VIOLET : st == 5 ? RED : FX_COLORS[i < 12 ? 0 : i < 17 ? 1 : 2], cx = x + i * cw;
            int fill = i < lit ? (plasma ? blend(c, TermKit.FAINT, 0.4f * pulse) : TermKit.col(c))
                    : i == lit && phase() == 1 ? blend(TermKit.FAINT, c, pulse) : TermKit.col(TermKit.FAINT);
            g.fill(cx, by, cx + cw - 2, by + 16, fill);
        }
        TermKit.fill(g, x + 12 * cw - 1, by - 2, x + 12 * cw, by + 18, MID);
        TermKit.fill(g, x + 17 * cw - 1, by - 2, x + 17 * cw, by + 18, MID);
        TermKit.right(g, font, Component.literal(String.format("%.1f %%", frac * 100)), x + w, by + 4, st < 0 ? DIM : GREEN);
        tip(8, PORT_Y + 16, w, 18, t("fx.title"), t("fx.tip.bar", fe(done), fe(cost), fe(draw)));
        txt(g, plasma ? t("fx.net", menu.i32(BigMachineBlockEntity.FUSION_DATA)) : t("fx.energy", fe(done), fe(cost)), x, by + 20, plasma ? GREEN : MID);
        TermKit.right(g, font, t("fx.draw", fe(draw)), x + w, by + 20, draw > 0 ? CYAN : DIM);
        int gy = by + 38, gh = 42, gw = 176, mk = BigMachineBlockEntity.FUSION_MAX_MK;
        TermKit.frame(g, font, x, gy, gw, gh, t("fx.graph"), DIM);
        for (int k = 1; k < 4; k++) TermKit.fill(g, x + 2, gy + gh * k / 4, x + gw - 2, gy + gh * k / 4 + 1, TermKit.FAINT);
        for (int i = 1; i < histN; i++) {
            int x0 = x + 2 + (i - 1) * (gw - 5) / (HIST - 1), x1 = x + 2 + i * (gw - 5) / (HIST - 1), b = gy + gh - 3, h = gh - 6;
            TermKit.line(g, x0, b - histCharge[i - 1] * h / 1000, x1, b - histCharge[i] * h / 1000, CYAN);
            TermKit.line(g, x0, b - histTemp[i - 1] * h / mk, x1, b - histTemp[i] * h / mk, AMBER);
        }
        tip(8, gy - topPos, gw, gh, t("fx.graph"), t("fx.tip.graph"));
        int lx = x + gw + 6, lw = w - gw - 6, ly = gy + 4, n = 0;
        TermKit.frame(g, font, lx, gy, lw, gh, t("fx.log"), DIM);
        for (Component c : fxLog) {
            TermKit.fit(g, font, c, lx + 4, ly, lw - 8, ++n == fxLog.size() ? blend(GREEN, DIM, 0.5f * pulse) : MID);
            ly += 9;
        }
    }

    @Override
    protected void init() {
        imageHeight = height();
        super.init();
        topPos = Math.max(topPos, Math.min(16, height - imageHeight - 4));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (TermKit.styleClick(mx, my)) return true;
        for (int r = 0; r < rows(); r++) {
            int i = r + scroll;
            if (capacity(i) > 0 && in(mx, my, W - 8 - DUMP_W - bar(), portY() + r * PORT_H + 1, DUMP_W, DUMP_H)) {
                if (amount(i) > 0) send(DeviceControlPayload.BIG_DUMP, pi(i));
                return true;
            }
        }
        if (in(mx, my, W - 52, height() - 14, 44, 11)) {
            AccessScreen.request(menu.pos);
            return true;
        }
        int cy = controlsY() + 12;
        if (in(mx, my, 8, cy, SEG_W, SEG_H)) {
            send(DeviceControlPayload.BIG_ENABLE, seg(mx, 8, SEG_W, 2));
            return true;
        }
        if (emergency() && in(mx, my, W - 8 - SEG_W, cy, SEG_W, SEG_H)) {
            send(DeviceControlPayload.BIG_MANUAL, seg(mx, W - 8 - SEG_W, SEG_W, 2));
            return true;
        }
        if (fusion() && in(mx, my, W - 8 - SEG_W, cy, SEG_W, SEG_H)) {
            send(DeviceControlPayload.BIG_BURN, 25 * (seg(mx, W - 8 - SEG_W, SEG_W, 4) + 1));
            return true;
        }
        if (steamGenerator() && in(mx, my, W - 8 - SEG_W, cy, SEG_W, SEG_H)) {
            send(DeviceControlPayload.BIG_REPAIR, 1);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, ports() - rows());
        return true;
    }

    private int bar() {
        return rows() < ports() ? 6 : 0;
    }

    private void txt(GuiGraphics g, Component c, int x, int y, int color) {
        TermKit.text(g, font, c, x, y, color);
    }

    private static int stateColor(String key) {
        return switch (key) {
            case "running" -> GREEN;
            case "plasma" -> GREEN;
            case "idle", "standby", "venting", "heating" -> AMBER;
            case "off" -> DIM;
            default -> RED;
        };
    }

    private static String num(int v) {
        return v >= 10000 ? String.format("%.1fk", v / 1000f) : String.valueOf(v);
    }

    private void port(GuiGraphics g, int r, int mx, int my) {
        int i = r + scroll;
        BigMachine m = machine();
        BigMachine.Port p = m.ports.get(pi(i));
        int x = leftPos + 8, y = topPos + portY() + r * PORT_H, w = W - 16 - bar(), ry = portY() + r * PORT_H;
        Component name = Component.translatable("port.nuclearsq." + m.id + "." + p.name());
        boolean in = p.in() && !p.out(), out = p.out() && !p.in(), modern = TermKit.modern();
        txt(g, Component.literal(in ? "<<" : out ? ">>" : "<>"), x, y + 3, !modern ? MID : in ? CYAN : out ? AMBER : MID);
        int cap = capacity(i);
        Component dir = k(in ? "dir.in" : out ? "dir.out" : "dir.both");
        Component kind = k("kind." + p.kind().id());
        Component info = p.kind().isFluid() ? k("port.fluid", dir, kind, p.rate(), cap) : k("port.other", dir, kind);
        Component fill = cap > 0 ? k("port.content", amount(i), cap,
                amount(i) <= 0 ? t("empty") : new FluidStack(BuiltInRegistries.FLUID.byId(menu.u(base(i) + 4)), 1).getHoverName())
                : Component.empty();
        tip(8, ry, cap > 0 ? w - DUMP_W - 4 : w, cap > 0 ? PORT_H - 2 : 18, name, info, fill, k("kindhint." + p.kind().id()));
        if (cap > 0) {
            int amt = amount(i), fid = menu.u(base(i) + 4), right = x + w - DUMP_W - 6;
            float frac = amt / (float) cap;
            Component fluid = amt <= 0 ? t("empty")
                    : new FluidStack(BuiltInRegistries.FLUID.byId(fid), 1).getHoverName();
            Component amount = Component.literal(num(amt) + " / " + num(cap) + " mB");
            TermKit.right(g, font, amount, right, y + 3, frac > 0.95f ? AMBER : GREEN);
            TermKit.fit(g, font, name, x + 16, y + 3, right - TermKit.width(font, amount) - 8 - x - 16, modern ? GREEN : MID);
            TermKit.bar(g, x, y + 14, w - DUMP_W - 116, 7, frac, frac > 0.95f ? AMBER : modern ? CYAN : GREEN);
            int fw = Math.min(104, TermKit.width(font, fluid));
            TermKit.fit(g, font, fluid, right - fw, y + 14, fw, amt <= 0 ? DIM : CYAN);
            boolean hover = in(mx, my, w + 8 - DUMP_W, ry + 1, DUMP_W, DUMP_H);
            TermKit.button(g, font, x + w - DUMP_W, y + 1, DUMP_W, DUMP_H, t("dump"), AMBER, amt > 0, hover && amt > 0, false);
            tip(w + 8 - DUMP_W, ry + 1, DUMP_W, DUMP_H, t("dump"), t("tip.dump"));
            txt(g, t("rate", p.rate()), x + w - DUMP_W, y + 14, DIM);
            TermKit.rule(g, x, y + PORT_H - 2, w, TermKit.FAINT);
            return;
        }
        TermKit.fit(g, font, name, x + 16, y + 3, w / 2, modern ? GREEN : MID);
        Component value = switch (p.kind()) {
            case ENERGY -> machine() == BigMachine.SUBSTATION ? t(p.in() ? "energy_port_in" : "energy_port_out", menu.i32(BigMachineBlockEntity.FUSION_DATA + (p.in() ? 0 : 2)))
                    : t(machine() == BigMachine.SOLAR_FIELD || machine().isWind() ? "energy_port_out" : "energy_port", menu.u(5));
            case SIGNAL -> t("signal_port", p.in() ? menu.u(7) : menu.u(8));
            case ITEM -> t("items_port", menu.u(13), p.slots());
            default -> Component.empty();
        };
        TermKit.right(g, font, value, x + w, y + 3, p.kind() == BigPortKind.ENERGY && menu.u(6) == 0 && m.energy != null ? AMBER : GREEN);
        TermKit.rule(g, x, y + 17, w, TermKit.FAINT);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, h = height();
        BigMachine m = machine();
        boolean formed = menu.u(0) == 1, enabled = menu.u(2) == 1, manual = menu.u(9) == 1;
        String[] states = BigMachineBlockEntity.STATES;
        String state = !formed ? "unformed" : states[Mth.clamp(menu.u(3), 0, states.length - 1)];

        TermKit.recipes(com.sqc.nuclearsq.client.RecipeLink.machine(m));
        TermKit.monitor(g, x - 4, y - 4, W + 8, h + 8);
        TermKit.headBadge(g, font, title, t("state." + state), x + 8, y + 8, W - 16, !formed ? RED : stateColor(state));
        tip(W - 110, 4, 102, 14, t("state." + state), t("tip.state." + state));
        TermKit.rule(g, x + 8, y + 20, W - 16, DIM);

        int fw = (W - 16 - 12) / 3;
        boolean wind = machine().isWind();
        boolean solar = machine() == BigMachine.SOLAR_FIELD || wind;
        boolean sub = machine() == BigMachine.SUBSTATION;
        String tp = machine() == BigMachine.SHAFT_GENERATOR ? "shaft_power" : wind ? "wind_power" : solar ? "sun_power" : sub ? "feed" : "throughput";
        String unit = machine() == BigMachine.SHAFT_GENERATOR || solar || sub ? " FE/t" : " mB/t";
        int ty = 26, th = 36;
        if (fusion()) {
            int mk = menu.u(4);
            TermKit.tile(g, font, x + 8, y + ty, fw, th, t("plasma"), Component.literal(mk + " MK"), mk > 0 ? AMBER : DIM,
                    mk / (float) BigMachineBlockEntity.FUSION_MAX_MK);
            tip(8, ty, fw, th, t("plasma"), t("tip.plasma", mk, menu.u(BigMachineBlockEntity.FUSION_DATA + 4)));
        } else if (machine() == BigMachine.PRESSURIZER) {
            int pct = menu.u(4), bar = Math.round(pct * BigMachineBlockEntity.BAR_PER_PERCENT);
            TermKit.tile(g, font, x + 8, y + ty, fw, th, t("pressure"), Component.literal(bar + " bar"),
                    pct > BigMachineBlockEntity.PRESSURE_RELIEF || pct < BigMachineBlockEntity.PRESSURE_LOW ? RED : GREEN, pct / 100f);
            tip(8, ty, fw, th, t("pressure"), t("tip.pressure", bar, pct));
        } else {
            int in = sub ? menu.i32(BigMachineBlockEntity.FUSION_DATA) : 0, line = sub ? menu.i32(BigMachineBlockEntity.FUSION_DATA + 4) : 0;
            int v = sub ? in + line : menu.u(4), buf = menu.u(BigMachineBlockEntity.FUSION_DATA + 6);
            TermKit.tile(g, font, x + 8, y + ty, fw, th, t(tp), Component.literal(v + unit), GREEN, sub ? buf / 1000f : -1);
            if (sub) tip(8, ty, fw, th, t(tp), t("tip.sub_feed", in, line), t("tip.sub_buffer", buf / 10));
            else tip(8, ty, fw, th, t(tp), t("tip." + tp, v));
        }
        if (fusion()) {
            int net = menu.i32(BigMachineBlockEntity.FUSION_DATA), ign = menu.u(BigMachineBlockEntity.FUSION_DATA + 3);
            TermKit.tile(g, font, x + 14 + fw, y + ty, fw, th, t("net"), Component.literal(net + " FE/t"), net > 0 ? GREEN : DIM,
                    menu.u(BigMachineBlockEntity.FUSION_DATA + 6) == 1 ? ign / 100f : -1);
            tip(14 + fw, ty, fw, th, t("net"), t("tip.net", net, ign, m.energy.capacity()));
        } else if (solar || sub) {
            int out = sub ? menu.i32(BigMachineBlockEntity.FUSION_DATA + 2) : menu.u(5);
            boolean field = machine() == BigMachine.SOLAR_FIELD;
            int buf = sub || field ? menu.u(BigMachineBlockEntity.FUSION_DATA + 7) : -1;
            TermKit.tile(g, font, x + 14 + fw, y + ty, fw, th, t("out"), Component.literal(out + " FE/t"), out > 0 || buf > 0 ? AMBER : DIM, buf >= 0 ? buf / 1000f : -1);
            if (sub) tip(14 + fw, ty, fw, th, t("out"), t("tip.line_out", out), t("tip.sub_buffer", buf / 10));
            else if (field) tip(14 + fw, ty, fw, th, t("out"), t("tip.out_buffer", out), t("tip.sub_buffer", buf / 10));
            else tip(14 + fw, ty, fw, th, t("out"), t("tip.out", out));
        } else if (m.energy != null) {
            int e = menu.u(5);
            TermKit.tile(g, font, x + 14 + fw, y + ty, fw, th, t("power"), Component.literal(e + "%"),
                    menu.u(6) == 1 ? AMBER : RED, e / 100f);
            tip(14 + fw, ty, fw, th, t("power"), t("tip.power", e, m.energy.use()), t("tip.power2"));
        } else {
            TermKit.tile(g, font, x + 14 + fw, y + ty, fw, th, t("power"), t("passive"), DIM, -1);
            tip(14 + fw, ty, fw, th, t("power"), t("tip.passive"));
        }
        int matched = menu.u(10), total = Math.max(1, menu.u(11));
        TermKit.tile(g, font, x + 20 + 2 * fw, y + ty, fw, th, t("structure"),
                Component.literal(formed ? "OK" : Math.round(100f * matched / total) + "%"), formed ? GREEN : RED,
                formed ? 1f : matched / (float) total);
        tip(20 + 2 * fw, ty, fw, th, t("structure"), t("tip.structure", matched, total));

        if (fusion()) fusionPanel(g);
        TermKit.caption(g, font, t("ports"), x + 8, y + portY() - 12);
        scroll = Mth.clamp(scroll, 0, ports() - rows());
        for (int r = 0; r < rows(); r++) port(g, r, mx, my);
        if (bar() > 0) {
            int sy = y + portY(), sh = rows() * PORT_H - 2, bh = Math.max(8, sh * rows() / ports());
            TermKit.fill(g, x + W - 11, sy, x + W - 8, sy + sh, TermKit.FAINT);
            TermKit.fill(g, x + W - 11, sy + (sh - bh) * scroll / (ports() - rows()), x + W - 8, sy + (sh - bh) * scroll / (ports() - rows()) + bh, MID);
        }

        int cy = y + controlsY();
        TermKit.rule(g, x + 8, cy - 4, W - 16, DIM);
        TermKit.caption(g, font, t("operation"), x + 8, cy + 1);
        int he = in(mx, my, 8, controlsY() + 12, SEG_W, SEG_H) ? seg(mx, 8, SEG_W, 2) : -1;
        TermKit.segments(g, font, x + 8, cy + 12, SEG_W, SEG_H, new Component[] {t("off"), t("on")},
                enabled ? 1 : 0, enabled ? GREEN : AMBER, he);
        tip(8, controlsY() + 12, SEG_W / 2, SEG_H, t("off"), t("tip.off"));
        tip(8 + SEG_W / 2, controlsY() + 12, SEG_W / 2, SEG_H, t("on"), t("tip.on"));
        if (emergency()) {
            int sx = W - 8 - SEG_W;
            TermKit.right(g, font, t("mode", menu.u(14) == 1 ? t("signal_on") : t("signal_off")), x + W - 8, cy + 1, MID);
            int hm = in(mx, my, sx, controlsY() + 12, SEG_W, SEG_H) ? seg(mx, sx, SEG_W, 2) : -1;
            TermKit.segments(g, font, x + sx, cy + 12, SEG_W, SEG_H, new Component[] {t("auto"), t("manual")},
                    manual ? 1 : 0, manual ? AMBER : GREEN, hm);
            tip(sx, controlsY() + 12, SEG_W / 2, SEG_H, t("auto"), t("tip.auto"));
            tip(sx + SEG_W / 2, controlsY() + 12, SEG_W / 2, SEG_H, t("manual"), t("tip.manual"));
        }
        if (fusion()) {
            int sx = W - 8 - SEG_W, set = menu.u(9), wall = menu.u(BigMachineBlockEntity.FUSION_DATA + 2), wear = menu.u(14);
            TermKit.right(g, font, t("fusion_info", set, wall, wear), x + W - 8, cy + 1, wall >= 60 || wear >= 90 ? RED : wear >= 70 ? AMBER : MID);
            int hm = in(mx, my, sx, controlsY() + 12, SEG_W, SEG_H) ? seg(mx, sx, SEG_W, 4) : -1;
            TermKit.segments(g, font, x + sx, cy + 12, SEG_W, SEG_H, new Component[] {Component.literal("25"), Component.literal("50"),
                    Component.literal("75"), Component.literal("100")}, set <= 0 ? -1 : Math.min(3, (set - 1) / 25), AMBER, hm);
            tip(sx, controlsY() + 12, SEG_W, SEG_H, t("burn"), t("tip.burn", set));
        }
        if (steamGenerator()) {
            int sx = W - 8 - SEG_W;
            boolean leak = menu.u(9) == 1;
            TermKit.right(g, font, t("wear", menu.u(14)), x + W - 8, cy + 1, leak ? RED : menu.u(14) >= 75 ? AMBER : MID);
            int hm = in(mx, my, sx, controlsY() + 12, SEG_W, SEG_H) ? 0 : -1;
            TermKit.segments(g, font, x + sx, cy + 12, SEG_W, SEG_H, new Component[] {t("repair")}, enabled ? -1 : 0,
                    enabled ? DIM : leak ? RED : GREEN, hm);
            tip(sx, controlsY() + 12, SEG_W, SEG_H, t("repair"), t(enabled ? "tip.repair_off" : "tip.repair"));
        }
        TermKit.button(g, font, x + W - 52, y + h - 14, 44, 11, Component.translatable("gui.nuclearsq.access.button"), CYAN, true,
                in(mx, my, W - 52, h - 14, 44, 11), false);
        tip(W - 52, h - 14, 44, 11, Component.translatable("gui.nuclearsq.access.button"), Component.translatable("gui.nuclearsq.access.button.tip"));
        TermKit.prompt(g, font, x + 8, y + h - 12, t(emergency() ? "hint_emergency" : "hint"), DIM);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, h + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (imageHeight != height()) init(minecraft, width, height);
        super.render(g, mx, my, pt);
        if (!TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
