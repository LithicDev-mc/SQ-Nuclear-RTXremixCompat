package com.sqc.nuclearsq.data.client;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.data.PowerMonitorBlockEntity;
import com.sqc.nuclearsq.data.PowerMonitorMenu;
import com.sqc.nuclearsq.data.PowerSample;
import com.sqc.nuclearsq.data.PowerStats;
import com.sqc.nuclearsq.data.PowerSummary;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PowerMonitorScreen extends AbstractContainerScreen<PowerMonitorMenu> {
    private static final int W = 340, H = 252;
    private static final int TILE_H = 34, STORE_H = 16, ROW = 9;
    private int GRAPH_H = 56, LIST_ROWS = 4;
    private static boolean slow;

    public PowerMonitorScreen(PowerMonitorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        boolean low = height < H + 20;
        GRAPH_H = low ? 30 : 56;
        LIST_ROWS = low ? 2 : 4;
        imageHeight = low ? H - 26 - 2 * ROW : H;
        super.init();
        topPos = Math.max(topPos, Math.min(16, height - imageHeight - 4));
    }

    private static int prodColor() { return TermKit.modern() ? TermKit.AMBER : TermKit.GREEN; }
    private static int consColor() { return TermKit.modern() ? TermKit.CYAN : TermKit.AMBER; }
    private static int storeColor() { return TermKit.modern() ? TermKit.GREEN : TermKit.CYAN; }

    private List<PowerSample> series() {
        return slow ? menu.coarse : menu.fine;
    }

    private int maxSamples() {
        return slow ? PowerMonitorBlockEntity.COARSE_MAX : PowerMonitorBlockEntity.FINE_MAX;
    }

    private int stepSeconds() {
        return slow ? PowerMonitorBlockEntity.COARSE_EVERY : 1;
    }

    private int graphX() {
        return leftPos + 34;
    }

    private int graphW() {
        return imageWidth - 34 - 10;
    }

    private int graphY() {
        return topPos + 76;
    }

    private int rangeX() {
        return leftPos + 8;
    }

    private int rangeY() {
        return topPos + 61;
    }

    private static final int RANGE_W = 110;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (TermKit.styleClick(mx, my)) return true;
            if (my >= rangeY() && my < rangeY() + 12 && mx >= rangeX() && mx < rangeX() + RANGE_W) {
                slow = mx >= rangeX() + RANGE_W / 2.0;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    public static long niceMax(long v) {
        long m = Math.max(1, v);
        long p = 1;
        while (p * 10 <= m) p *= 10;
        for (int f : new int[] {1, 2, 5, 10}) if (p * f >= m) return p * f;
        return p * 10;
    }

    private static Component fe(long v) {
        return Component.literal(PowerStats.compact(v) + " FE/t");
    }

    private static Component signed(long v) {
        return Component.literal((v > 0 ? "+" : "") + PowerStats.compact(v) + " FE/t");
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8);
        List<PowerSample> fine = menu.fine;
        PowerSample now = fine.isEmpty() ? PowerSample.ZERO : fine.get(fine.size() - 1);
        PowerSummary sum = menu.summary;
        TermKit.header(g, font, title, Component.translatable("gui.nuclearsq.power.plants", sum.plants()), x + 8, y + 7, w - 16, TermKit.MID);
        TermKit.rule(g, x + 8, y + 18, w - 16, TermKit.DIM);

        int gap = 4, tw = (w - 16 - 3 * gap) / 4, ty = y + 22;
        float load = PowerStats.load(now, sum);
        TermKit.tile(g, font, x + 8, ty, tw, TILE_H, Component.translatable("gui.nuclearsq.power.prod"), fe(now.prod()), prodColor(),
                sum.peak() > 0 ? load : -1f);
        TermKit.tip(x + 8, ty, tw, TILE_H, Component.translatable("gui.nuclearsq.power.prod.tip"),
                Component.translatable("gui.nuclearsq.power.prod.tip2", Math.round(load * 100), PowerStats.compact(sum.peak())));
        TermKit.tile(g, font, x + 8 + (tw + gap), ty, tw, TILE_H, Component.translatable("gui.nuclearsq.power.cons"), fe(now.cons()), consColor(),
                sum.peak() > 0 ? Math.min(1f, now.cons() / (float) sum.peak()) : -1f);
        TermKit.tip(x + 8 + (tw + gap), ty, tw, TILE_H, Component.translatable("gui.nuclearsq.power.cons.tip"),
                Component.translatable("gui.nuclearsq.power.cons.tip2"));
        long bal = now.balance();
        TermKit.tile(g, font, x + 8 + 2 * (tw + gap), ty, tw, TILE_H, Component.translatable("gui.nuclearsq.power.balance"), signed(bal),
                bal >= 0 ? TermKit.GREEN : TermKit.RED, -1f);
        TermKit.tip(x + 8 + 2 * (tw + gap), ty, tw, TILE_H, Component.translatable("gui.nuclearsq.power.balance.tip"),
                Component.translatable("gui.nuclearsq.power.balance.tip2"));
        Component storeVal = now.capacity() > 0 ? Component.literal(Math.round(now.fill() * 100) + " %") : Component.translatable("gui.nuclearsq.power.no_storage");
        TermKit.tile(g, font, x + 8 + 3 * (tw + gap), ty, tw, TILE_H, Component.translatable("gui.nuclearsq.power.storage"), storeVal,
                now.capacity() > 0 ? storeColor() : TermKit.DIM, now.capacity() > 0 ? now.fill() : -1f);
        if (now.capacity() > 0) {
            TermKit.tip(x + 8 + 3 * (tw + gap), ty, tw, TILE_H, Component.translatable("gui.nuclearsq.power.storage"),
                    Component.literal(PowerStats.compact(now.stored()) + " / " + PowerStats.compact(now.capacity()) + " FE"));
        }

        int hover = my >= rangeY() && my < rangeY() + 12 && mx >= rangeX() && mx < rangeX() + RANGE_W ? (mx >= rangeX() + RANGE_W / 2 ? 1 : 0) : -1;
        TermKit.segments(g, font, rangeX(), rangeY(), RANGE_W, 12, new Component[] {Component.translatable("gui.nuclearsq.power.range.fast"),
                Component.translatable("gui.nuclearsq.power.range.slow")}, slow ? 1 : 0, prodColor(), hover);
        int lx = rangeX() + RANGE_W + 12, ly = rangeY() + 2;
        TermKit.fill(g, lx, ly + 2, lx + 8, ly + 5, prodColor());
        TermKit.text(g, font, Component.translatable("gui.nuclearsq.power.prod"), lx + 12, ly, TermKit.MID);
        int lx2 = lx + 12 + TermKit.width(font, Component.translatable("gui.nuclearsq.power.prod")) + 12;
        TermKit.fill(g, lx2, ly + 2, lx2 + 8, ly + 5, consColor());
        TermKit.text(g, font, Component.translatable("gui.nuclearsq.power.cons"), lx2 + 12, ly, TermKit.MID);
        Component span = Component.translatable(slow ? "gui.nuclearsq.power.span.slow" : "gui.nuclearsq.power.span.fast");
        int legendEnd = lx2 + 12 + TermKit.width(font, Component.translatable("gui.nuclearsq.power.cons")) + 8;
        if (x + w - 8 - TermKit.width(font, span) >= legendEnd) TermKit.right(g, font, span, x + w - 8, ly, TermKit.DIM);

        graph(g, series(), mx, my);
        storageGraph(g, series());

        int ly0 = y + 114 + GRAPH_H;
        TermKit.rule(g, x + 8, ly0 - 2, w - 16, TermKit.FAINT);
        int colGap = 8, cw = (w - 16 - 2 * colGap) / 3;
        list(g, x + 8, ly0, cw, Component.translatable("gui.nuclearsq.power.list.producers"), sum.producers(), 0, prodColor());
        list(g, x + 8 + cw + colGap, ly0, cw, Component.translatable("gui.nuclearsq.power.list.consumers"), sum.consumers(), 1, consColor());
        list(g, x + 8 + 2 * (cw + colGap), ly0, cw, Component.translatable("gui.nuclearsq.power.list.storages"), sum.storages(), 2, storeColor());

        int fy = y + h - 14;
        TermKit.rule(g, x + 8, fy - 3, w - 16, TermKit.FAINT);
        PowerStats.Eta eta = PowerStats.eta(fine);
        TermKit.text(g, font, etaText(eta), x + 10, fy, eta.kind() == PowerStats.Eta.Kind.EMPTY_IN || eta.kind() == PowerStats.Eta.Kind.IS_EMPTY
                ? TermKit.RED : eta.kind() == PowerStats.Eta.Kind.NONE ? TermKit.DIM : TermKit.CYAN);
        if (!fine.isEmpty()) {
            int n = Math.min(60, fine.size());
            PowerSample avg = PowerSample.average(fine, fine.size() - n, n);
            TermKit.right(g, font, Component.translatable("gui.nuclearsq.power.avg", PowerStats.compact(avg.prod()), PowerStats.compact(avg.cons())),
                    x + w - 10, fy, TermKit.MID);
        }
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
    }

    private static Component etaText(PowerStats.Eta eta) {
        return switch (eta.kind()) {
            case NONE -> Component.translatable("gui.nuclearsq.power.eta.none");
            case STEADY -> Component.translatable("gui.nuclearsq.power.eta.steady");
            case IS_EMPTY -> Component.translatable("gui.nuclearsq.power.eta.is_empty");
            case IS_FULL -> Component.translatable("gui.nuclearsq.power.eta.is_full");
            case EMPTY_IN -> Component.translatable("gui.nuclearsq.power.eta.empty_in", PowerStats.duration(eta.seconds()));
            case FULL_IN -> Component.translatable("gui.nuclearsq.power.eta.full_in", PowerStats.duration(eta.seconds()));
        };
    }

    private void list(GuiGraphics g, int x, int y, int w, Component head, List<PowerSummary.Item> items, int kind, int color) {
        TermKit.fit(g, font, head, x, y, w, color);
        TermKit.rule(g, x, y + 10, w, TermKit.FAINT);
        if (items.isEmpty()) {
            TermKit.text(g, font, Component.translatable("gui.nuclearsq.power.list.empty"), x, y + 14, TermKit.DIM);
            return;
        }
        for (int i = 0; i < Math.min(LIST_ROWS, items.size()); i++) {
            PowerSummary.Item it = items.get(i);
            int ry = y + 14 + i * ROW;
            Component name = it.count() > 1 ? it.name().copy().append(" x" + it.count()) : it.name();
            Component value;
            if (kind == 2) {
                int pct = it.b() > 0 ? (int) Math.round(100.0 * it.a() / it.b()) : 0;
                value = Component.literal(pct + " %");
                TermKit.tip(x, ry - 1, w, ROW, it.name(), Component.literal(PowerStats.compact(it.a()) + " / " + PowerStats.compact(it.b()) + " FE"));
            } else {
                value = Component.literal(PowerStats.compact(it.a()));
                if (kind == 0) TermKit.tip(x, ry - 1, w, ROW, it.name(), Component.translatable("gui.nuclearsq.power.list.peak", PowerStats.compact(it.a()),
                        PowerStats.compact(it.b())));
            }
            TermKit.field(g, font, x, ry, w, name, value, color);
        }
        if (items.size() > LIST_ROWS) TermKit.text(g, font, Component.translatable("gui.nuclearsq.power.list.more", items.size() - LIST_ROWS),
                x, y + 14 + LIST_ROWS * ROW, TermKit.DIM);
    }

    private int xAt(int i, int n) {
        int gx = graphX(), gw = graphW();
        float step = (gw - 1) / (float) (maxSamples() - 1);
        return gx + gw - 1 - Math.round((n - 1 - i) * step);
    }

    private void graph(GuiGraphics g, List<PowerSample> list, int mx, int my) {
        int gx = graphX(), gy = graphY(), gw = graphW(), gh = GRAPH_H;
        TermKit.frame(g, font, gx - 2, gy - 2, gw + 4, gh + 4, null, TermKit.DIM);
        long top = niceMax(PowerStats.max(list));
        for (int k = 0; k <= 4; k++) {
            int yy = gy + gh - 1 - Math.round((gh - 1) * k / 4f);
            if (k > 0 && k < 4) TermKit.rule(g, gx, yy, gw, TermKit.FAINT);
        }
        TermKit.right(g, font, Component.literal(PowerStats.compact(top)), gx - 5, gy - 1, TermKit.MID);
        TermKit.right(g, font, Component.literal(PowerStats.compact(top / 2)), gx - 5, gy + gh / 2 - 4, TermKit.DIM);
        TermKit.right(g, font, Component.literal("0"), gx - 5, gy + gh - 8, TermKit.DIM);
        int n = list.size();
        if (n == 0) {
            TermKit.center(g, font, Component.translatable("gui.nuclearsq.power.wait"), gx + gw / 2, gy + gh / 2 - 4, TermKit.DIM);
            return;
        }
        int prevPx = 0, prevPy = 0, prevCx = 0, prevCy = 0;
        for (int i = 0; i < n; i++) {
            PowerSample s = list.get(i);
            int px = xAt(i, n);
            int py = gy + gh - 1 - (int) Math.round((gh - 1) * Math.min(1.0, s.prod() / (double) top));
            int cy = gy + gh - 1 - (int) Math.round((gh - 1) * Math.min(1.0, s.cons() / (double) top));
            int from = i == 0 ? px : prevPx + 1;
            if (py < gy + gh - 1) g.fill(from, py + 1, px + 1, gy + gh, TermKit.modern() ? 0x38E8A33D : TermKit.FAINT);
            if (i > 0) {
                TermKit.line(g, prevPx, prevPy, px, py, prodColor());
                TermKit.line(g, prevPx, prevCy, px, cy, consColor());
            }
            prevPx = px;
            prevPy = py;
            prevCy = cy;
        }
        TermKit.text(g, font, Component.translatable("gui.nuclearsq.power.ago", PowerStats.duration((long) (n - 1) * stepSeconds())), gx + 2, gy + gh + 4, TermKit.DIM);
        TermKit.right(g, font, Component.translatable("gui.nuclearsq.power.now"), gx + gw, gy + gh + 4, TermKit.DIM);
        int hi = hoverIndex(mx, my, n);
        if (hi >= 0) {
            int hx = xAt(hi, n);
            TermKit.fill(g, hx, gy, hx + 1, gy + gh, TermKit.CYAN);
        }
    }

    private void storageGraph(GuiGraphics g, List<PowerSample> list) {
        int gx = graphX(), gw = graphW(), gy = graphY() + GRAPH_H + 15, gh = STORE_H;
        TermKit.frame(g, font, gx - 2, gy - 2, gw + 4, gh + 4, null, TermKit.FAINT);
        TermKit.center(g, font, Component.translatable("gui.nuclearsq.power.storage"), gx + gw / 2, gy - 11, TermKit.DIM);
        int n = list.size();
        if (n == 0 || list.get(n - 1).capacity() <= 0) {
            TermKit.center(g, font, Component.translatable("gui.nuclearsq.power.no_storage_line"), gx + gw / 2, gy + gh / 2 - 4, TermKit.DIM);
            return;
        }
        int prevX = 0, prevY = 0;
        for (int i = 0; i < n; i++) {
            PowerSample s = list.get(i);
            int px = xAt(i, n);
            int py = gy + gh - 1 - Math.round((gh - 1) * s.fill());
            if (py < gy + gh - 1) g.fill(i == 0 ? px : prevX + 1, py + 1, px + 1, gy + gh, TermKit.modern() ? 0x3046C46A : TermKit.FAINT);
            if (i > 0) TermKit.line(g, prevX, prevY, px, py, storeColor());
            prevX = px;
            prevY = py;
        }
    }

    private int hoverIndex(int mx, int my, int n) {
        int gx = graphX(), gy = graphY(), gw = graphW();
        if (n == 0 || mx < gx || mx >= gx + gw || my < gy || my >= gy + GRAPH_H) return -1;
        float step = (gw - 1) / (float) (maxSamples() - 1);
        int back = Math.round((gx + gw - 1 - mx) / step);
        int i = n - 1 - back;
        return i < 0 || i >= n ? -1 : i;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        List<PowerSample> list = series();
        int hi = hoverIndex(mx, my, list.size());
        if (hi >= 0) {
            PowerSample s = list.get(hi);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.nuclearsq.power.ago", PowerStats.duration((long) (list.size() - 1 - hi) * stepSeconds())));
            lines.add(Component.translatable("gui.nuclearsq.power.prod").append(": " + PowerStats.compact(s.prod()) + " FE/t"));
            lines.add(Component.translatable("gui.nuclearsq.power.cons").append(": " + PowerStats.compact(s.cons()) + " FE/t"));
            lines.add(Component.translatable("gui.nuclearsq.power.balance").append(": " + (s.balance() > 0 ? "+" : "") + PowerStats.compact(s.balance()) + " FE/t"));
            if (s.capacity() > 0) lines.add(Component.translatable("gui.nuclearsq.power.storage").append(": " + Math.round(s.fill() * 100) + " %"));
            TermKit.tip(graphX(), graphY(), graphW(), GRAPH_H, lines.toArray(new Component[0]));
        }
        TermKit.renderTip(g, font, mx, my);
    }
}
