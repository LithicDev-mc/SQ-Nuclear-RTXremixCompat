package com.sqc.nuclearsq.data.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.sqc.nuclearsq.client.ScrollbarDrag;
import com.sqc.nuclearsq.data.DataEntry;
import com.sqc.nuclearsq.data.DataPayloads;
import com.sqc.nuclearsq.data.DataSearch;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataTerminalMenu;
import com.sqc.nuclearsq.data.DataValue;
import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.storage.StorageFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class DataTerminalScreen extends AbstractContainerScreen<DataTerminalMenu> {
    private static final int LONG_LIST = 8;
    private static final int ROWS = 14, ROW_H = 12, TOP = 38, INDENT = 10;
    private static final int ALL = 0, ALARMS = 1, STORAGE = 2, OPEN_W = 110, LOCATE_W = 60;

    private static final Map<BlockPos, Set<String>> OPEN = new HashMap<>();
    private static final Map<BlockPos, Integer> FILTER = new HashMap<>();

    private enum Kind { MACHINE, GROUP, ACTIONS, VALUE, SWITCH, STEP, SLOT, ITEM, MORE, NONE }

    private record Row(Kind kind, int depth, String id, DataEntry e, Component label, DataEntry.Line line, DataEntry.Slot slot,
                       DataEntry.Stock stock, int count) {}

    private final Set<String> open;
    private int scroll, filter;
    private final ScrollbarDrag bar = new ScrollbarDrag();
    private EditBox search;
    private List<Row> rows = List.of();

    public DataTerminalScreen(DataTerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 300;
        this.imageHeight = TOP + ROWS * ROW_H + 28;
        boolean first = !OPEN.containsKey(menu.pos);
        this.open = OPEN.computeIfAbsent(menu.pos.immutable(), p -> new HashSet<>());
        this.filter = FILTER.getOrDefault(menu.pos, ALL);
        if (first && menu.entries.size() == 1) openAll(open, menu.entries.get(0));
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 206, topPos + 23, imageWidth - 216, 9, Component.empty());
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(TermKit.tcol(TermKit.GREEN));
        search.setFormatter((s, pos) -> FormattedCharSequence.forward(s, TermKit.mono(s).getStyle()));
        search.setResponder(s -> scroll = 0);
        addRenderableWidget(search);
        if (menu.entries.size() > LONG_LIST) {
            search.setFocused(true);
            setFocused(search);
        }
    }

    private static String machineId(DataEntry e) {
        return e.pos().getX() + "," + e.pos().getY() + "," + e.pos().getZ();
    }

    private String query() {
        return search == null ? "" : search.getValue().trim();
    }

    private static boolean nameMatch(DataEntry e, List<String> terms) {
        return DataSearch.hasAll(e.name().getString() + " " + e.type(), terms);
    }

    private static boolean itemMatch(DataEntry.Stock s, List<String> terms) {
        return DataSearch.hasAll(s.item().getHoverName().getString(), terms);
    }

    private boolean shown(DataEntry e) {
        return switch (filter) {
            case ALARMS -> e.state() == DataSource.WARN || e.state() == DataSource.ALARM;
            case STORAGE -> e.rack();
            default -> true;
        };
    }

    private List<Row> build() {
        List<Row> out = new ArrayList<>();
        List<String> q = DataSearch.terms(query());
        for (DataEntry e : menu.entries) {
            if (!shown(e)) continue;
            boolean nameHit = q.isEmpty() || nameMatch(e, q);
            boolean itemHit = false;
            if (!q.isEmpty() && !nameHit) {
                for (DataEntry.Slot s : e.slots()) for (DataEntry.Stock st : s.items()) itemHit |= itemMatch(st, q);
                if (!itemHit) continue;
            }
            String id = machineId(e);
            out.add(new Row(Kind.MACHINE, 0, id, e, e.name(), null, null, null, 0));
            if (!itemHit && !open.contains(id)) continue;
            if (itemHit) {
                for (int i = 0; i < e.slots().size(); i++) {
                    DataEntry.Slot s = e.slots().get(i);
                    List<DataEntry.Stock> hits = s.items().stream().filter(st -> itemMatch(st, q)).toList();
                    if (hits.isEmpty()) continue;
                    out.add(new Row(Kind.SLOT, 1, id + "/slot/" + i, e, slotLabel(i, s), null, s, null, i));
                    for (DataEntry.Stock st : hits) out.add(new Row(Kind.ITEM, 2, "", e, st.item().getHoverName(), null, s, st, 0));
                }
                continue;
            }
            group(out, e, id + "/values", Component.translatable("gui.nuclearsq.data.terminal.group.values"), e.values().size());
            if (open.contains(id + "/values")) {
                if (e.values().isEmpty()) out.add(new Row(Kind.NONE, 2, "", e, Component.translatable("gui.nuclearsq.data.terminal.none"), null, null, null, 0));
                for (DataEntry.Line l : e.values()) out.add(new Row(Kind.VALUE, 2, "", e, l.label(), l, null, null, 0));
            }
            if (!e.controls().isEmpty()) {
                group(out, e, id + "/controls", Component.translatable("gui.nuclearsq.data.terminal.group.controls"), e.controls().size());
                if (open.contains(id + "/controls")) for (DataEntry.Line l : e.controls()) {
                    out.add(new Row(l.kind() == DataEntry.SWITCH ? Kind.SWITCH : Kind.STEP, 2, "", e, l.label(), l, null, null, 0));
                }
            }
            if (e.rack()) {
                int used = 0;
                for (DataEntry.Slot s : e.slots()) if (s.kind() != DataEntry.EMPTY) used++;
                group(out, e, id + "/slots", Component.translatable("gui.nuclearsq.data.terminal.group.slots"), used, e.slots().size());
                if (open.contains(id + "/slots")) for (int i = 0; i < e.slots().size(); i++) {
                    DataEntry.Slot s = e.slots().get(i);
                    String sid = id + "/slot/" + i;
                    out.add(new Row(Kind.SLOT, 2, sid, e, slotLabel(i, s), null, s, null, i));
                    if (!open.contains(sid) || s.kind() != DataEntry.DRIVE) continue;
                    if (s.items().isEmpty()) out.add(new Row(Kind.NONE, 3, "", e, Component.translatable("gui.nuclearsq.data.terminal.drive_empty"), null, s, null, 0));
                    for (DataEntry.Stock st : s.items()) out.add(new Row(Kind.ITEM, 3, "", e, st.item().getHoverName(), null, s, st, 0));
                    if (s.more() > 0) out.add(new Row(Kind.MORE, 3, "", e, Component.translatable("gui.nuclearsq.data.terminal.more", s.more()), null, s, null, 0));
                }
            }
            out.add(new Row(Kind.ACTIONS, 1, "", e, Component.empty(), null, null, null, 0));
        }
        return out;
    }

    private void group(List<Row> out, DataEntry e, String id, Component label, int count) {
        out.add(new Row(Kind.GROUP, 1, id, e, label.copy().append(" (" + count + ")"), null, null, null, count));
    }

    private void group(List<Row> out, DataEntry e, String id, Component label, int used, int total) {
        out.add(new Row(Kind.GROUP, 1, id, e, label.copy().append(" (" + used + "/" + total + ")"), null, null, null, used));
    }

    private static Component slotLabel(int i, DataEntry.Slot s) {
        return Component.literal((i + 1) + ": ").append(s.name());
    }

    private static boolean folder(Row r) {
        return r.kind == Kind.MACHINE || r.kind == Kind.GROUP
                || (r.kind == Kind.SLOT && r.depth == 2 && r.slot.kind() == DataEntry.DRIVE);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - ROWS);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        rows = build();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8);
        Component count = Component.translatable("gui.nuclearsq.data.terminal.count", menu.entries.size());
        if (!query().isEmpty()) {
            int hits = 0;
            for (Row r : rows) if (r.kind == Kind.MACHINE) hits++;
            count = Component.translatable("gui.nuclearsq.data.terminal.count_hits", hits, menu.entries.size());
        }
        TermKit.header(g, font, title, count, x + 8, y + 7, w - 16, TermKit.MID);
        TermKit.rule(g, x + 8, y + 18, w - 16, TermKit.DIM);
        toolbar(g, x, y, mx, my);
        for (int r = 0; r < ROWS; r++) {
            int i = r + scroll;
            if (i >= rows.size()) break;
            row(g, rows.get(i), x, y + TOP + r * ROW_H, w, mx, my);
        }
        if (rows.isEmpty()) {
            TermKit.center(g, font, Component.translatable(query().isEmpty() ? "gui.nuclearsq.data.terminal.empty_filter" : "gui.nuclearsq.data.terminal.no_hits"),
                    x + w / 2, y + TOP + 20, TermKit.DIM);
        }
        bar.clear();
        if (maxScroll() > 0) {
            int bh = ROWS * ROW_H;
            float f = scroll / (float) maxScroll();
            TermKit.vbar(g, x + w - 6, y + TOP - 2, 3, bh, 1f, TermKit.FAINT);
            int knob = Math.max(8, bh * ROWS / rows.size());
            int ky = y + TOP - 2 + Math.round((bh - knob) * f);
            bar.track(x + w - 6, y + TOP - 2, 3, bh, knob, maxScroll());
            TermKit.fill(g, x + w - 6, ky, x + w - 3, ky + knob, TermKit.MID);
        }
        footer(g, x, y + h - 26, w);
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
    }

    private static final Component[] FILTERS = {
            Component.translatable("gui.nuclearsq.data.terminal.filter.all"),
            Component.translatable("gui.nuclearsq.data.terminal.filter.alarms"),
            Component.translatable("gui.nuclearsq.data.terminal.filter.storage")};

    private void toolbar(GuiGraphics g, int x, int y, int mx, int my) {
        int sy = y + 21, segW = 150;
        int hover = mx >= x + 8 && mx < x + 8 + segW && my >= sy && my < sy + 12 ? (int) ((mx - x - 8) * 3 / segW) : -1;
        TermKit.segments(g, font, x + 8, sy, segW, 12, FILTERS, filter, TermKit.GREEN, hover);
        boolean hOpen = in(mx, my, x + 162, sy, 16, 12), hClose = in(mx, my, x + 180, sy, 16, 12);
        TermKit.button(g, font, x + 162, sy, 16, 12, Component.literal("+"), TermKit.GREEN, true, hOpen, false);
        TermKit.button(g, font, x + 180, sy, 16, 12, Component.literal("-"), TermKit.GREEN, true, hClose, false);
        TermKit.tip(x + 162, sy, 16, 12, Component.translatable("gui.nuclearsq.data.terminal.expand_all"));
        TermKit.tip(x + 180, sy, 16, 12, Component.translatable("gui.nuclearsq.data.terminal.collapse_all"));
        TermKit.frame(g, font, x + 202, sy, imageWidth - 210, 12, null, search.isFocused() ? TermKit.MID : TermKit.DIM);
        if (search.getValue().isEmpty() && !search.isFocused()) {
            TermKit.text(g, font, Component.translatable("gui.nuclearsq.data.terminal.search"), x + 206, sy + 2, TermKit.DIM);
        }
        TermKit.tip(x + 202, sy, imageWidth - 210, 12, Component.translatable("gui.nuclearsq.data.terminal.search"),
                Component.translatable("gui.nuclearsq.data.terminal.search.tip"));
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void arrow(GuiGraphics g, int x, int y, boolean opened, int color) {
        int c = TermKit.col(color);
        if (opened) for (int i = 0; i < 3; i++) g.fill(x + i, y + 2 + i, x + 6 - i, y + 3 + i, c);
        else for (int i = 0; i < 3; i++) g.fill(x + 1 + i, y + i, x + 2 + i, y + 6 - i, c);
    }

    private Component fit(Component c, int w) {
        if (TermKit.width(font, c) <= w) return c;
        String s = c.getString();
        while (s.length() > 1 && TermKit.width(font, Component.literal(s + "..")) > w) s = s.substring(0, s.length() - 1);
        return Component.literal(s + "..");
    }

    private void row(GuiGraphics g, Row r, int x, int ry, int w, int mx, int my) {
        int x0 = x + 8, xr = x + w - 10, cx = x0 + r.depth * INDENT + 8;
        boolean hover = in(mx, my, x + 6, ry - 2, w - 12, ROW_H);
        for (int d = 1; d <= r.depth; d++) {
            int lx = x0 + (d - 1) * INDENT + 2;
            TermKit.fill(g, lx, ry - 2, lx + 1, ry + ROW_H - 2, TermKit.FAINT);
            if (d == r.depth) TermKit.fill(g, lx, ry + 3, lx + INDENT - 3, ry + 4, TermKit.FAINT);
        }
        if (folder(r)) {
            if (hover) TermKit.fill(g, x + 6, ry - 2, x + w - 6, ry + ROW_H - 2, TermKit.FAINT);
            arrow(g, x0 + r.depth * INDENT, ry + 1, open.contains(r.id), r.kind == Kind.MACHINE ? TermKit.GREEN : TermKit.MID);
        }
        switch (r.kind) {
            case MACHINE -> {
                DataEntry e = r.e;
                g.fill(cx, ry + 1, cx + 6, ry + 7, TermKit.col(DataEntry.color(e.state())));
                Component dist = Component.literal(e.dist() + " m");
                TermKit.right(g, font, dist, xr, ry, TermKit.DIM);
                int stateRight = xr - TermKit.width(font, dist) - 8;
                Component state = fit(e.stateText(), 110);
                TermKit.right(g, font, state, stateRight, ry, e.state() == DataSource.OK ? TermKit.MID : DataEntry.color(e.state()));
                TermKit.text(g, font, fit(e.name(), stateRight - TermKit.width(font, state) - 8 - (cx + 9)), cx + 9, ry, TermKit.GREEN);
                TermKit.tip(x + 10, ry - 2, w - 20, ROW_H, e.name(), e.stateText(),
                        Component.translatable("gui.nuclearsq.data.terminal.tip", e.type(), e.pos().getX(), e.pos().getY(), e.pos().getZ()),
                        Component.translatable(open.contains(r.id) ? "gui.nuclearsq.data.terminal.tip.collapse" : "gui.nuclearsq.data.terminal.tip.expand"));
            }
            case GROUP -> TermKit.text(g, font, r.label, cx, ry, TermKit.MID);
            case VALUE -> {
                DataEntry.Line l = r.line;
                Component v = Component.literal(l.text());
                int vw = TermKit.width(font, v);
                TermKit.right(g, font, v, xr, ry, TermKit.GREEN);
                int barX = Math.min(xr - 116, xr - vw - 54);
                if (l.frac() >= 0) TermKit.bar(g, barX, ry + 1, 48, 6, l.frac(), barColor(l));
                TermKit.text(g, font, fit(l.label(), (l.frac() >= 0 ? barX : xr - vw) - 6 - cx), cx, ry, TermKit.MID);
                TermKit.tip(cx, ry - 2, xr - cx, ROW_H, l.label(), Component.literal(l.text()), Component.literal(l.key()));
            }
            case SWITCH -> {
                DataEntry.Line l = r.line;
                boolean on = l.value() != 0;
                int bx = xr - 40;
                TermKit.button(g, font, bx, ry - 2, 40, 11, Component.translatable(on ? "gui.nuclearsq.data.terminal.on" : "gui.nuclearsq.data.terminal.off"),
                        on ? TermKit.GREEN : TermKit.AMBER, true, in(mx, my, bx, ry - 2, 40, 11), on);
                TermKit.text(g, font, fit(l.label(), bx - 6 - cx), cx, ry, TermKit.MID);
                TermKit.tip(bx, ry - 2, 40, 11, l.label(), Component.translatable("gui.nuclearsq.data.terminal.tip.switch"));
            }
            case STEP -> {
                DataEntry.Line l = r.line;
                int plus = xr - 12, minus = xr - 70;
                TermKit.button(g, font, minus, ry - 2, 12, 11, Component.literal("-"), TermKit.GREEN, l.value() > 0, in(mx, my, minus, ry - 2, 12, 11), false);
                TermKit.button(g, font, plus, ry - 2, 12, 11, Component.literal("+"), TermKit.GREEN, l.value() < l.max(), in(mx, my, plus, ry - 2, 12, 11), false);
                TermKit.center(g, font, Component.literal(l.text()), (minus + 12 + plus) / 2, ry, TermKit.GREEN);
                TermKit.bar(g, minus - 54, ry + 1, 48, 6, (float) (l.value() / l.max()), TermKit.CYAN);
                TermKit.text(g, font, fit(l.label(), minus - 60 - cx), cx, ry, TermKit.MID);
                TermKit.tip(minus, ry - 2, plus + 12 - minus, 11, l.label(),
                        Component.translatable("gui.nuclearsq.data.terminal.tip.step", DataValue.number(l.step()), DataValue.number(l.max())));
            }
            case SLOT -> {
                DataEntry.Slot s = r.slot;
                int color = s.kind() == DataEntry.EMPTY ? TermKit.DIM : s.kind() == DataEntry.MODULE ? TermKit.CYAN : TermKit.GREEN;
                Component t = Component.literal(s.text());
                int tw = TermKit.width(font, t);
                TermKit.right(g, font, t, xr, ry, color);
                int barX = Math.min(xr - 116, xr - tw - 54);
                if (s.frac() >= 0) TermKit.bar(g, barX, ry + 1, 48, 6, s.frac(), s.frac() > 0.95f ? TermKit.RED : s.frac() > 0.8f ? TermKit.AMBER : TermKit.GREEN);
                TermKit.text(g, font, fit(r.label, (s.frac() >= 0 ? barX : xr - tw) - 6 - cx), cx, ry, color == TermKit.GREEN ? TermKit.MID : color);
                if (s.kind() == DataEntry.DRIVE) {
                    TermKit.tip(x + 10, ry - 2, w - 20, ROW_H, s.name(), Component.translatable("gui.nuclearsq.data.terminal.tip.drive", s.text(), s.items().size() + s.more()));
                }
            }
            case ITEM -> {
                DataEntry.Stock st = r.stock;
                g.pose().pushPose();
                g.pose().translate(cx, ry - 2, 0);
                g.pose().scale(0.625f, 0.625f, 1f);
                g.renderItem(st.item(), 0, 0);
                g.pose().popPose();
                Component n = Component.literal(StorageFormat.count(st.count()));
                TermKit.right(g, font, n, xr, ry, TermKit.GREEN);
                TermKit.text(g, font, fit(r.label, xr - TermKit.width(font, n) - 6 - (cx + 13)), cx + 13, ry, TermKit.MID);
                TermKit.tip(cx, ry - 2, xr - cx, ROW_H, r.label, Component.translatable("gui.nuclearsq.data.terminal.tip.item", st.count()));
            }
            case MORE, NONE -> TermKit.text(g, font, r.label, cx, ry, TermKit.DIM);
            case ACTIONS -> {
                int ox = cx, ow = OPEN_W, lx = ox + ow + 4, lw = LOCATE_W;
                TermKit.button(g, font, ox, ry - 2, ow, 11, Component.translatable("gui.nuclearsq.data.terminal.open"), TermKit.GREEN, true, in(mx, my, ox, ry - 2, ow, 11), false);
                TermKit.button(g, font, lx, ry - 2, lw, 11, Component.translatable("gui.nuclearsq.data.terminal.locate"), TermKit.CYAN, true, in(mx, my, lx, ry - 2, lw, 11), false);
                TermKit.tip(ox, ry - 2, ow, 11, Component.translatable("gui.nuclearsq.data.terminal.open"), Component.translatable("gui.nuclearsq.data.terminal.tip.click"));
                TermKit.tip(lx, ry - 2, lw, 11, Component.translatable("gui.nuclearsq.data.terminal.locate"), Component.translatable("gui.nuclearsq.data.terminal.tip.locate"));
            }
        }
    }

    private static int barColor(DataEntry.Line l) {
        boolean fill = l.key().equals("storage_used") || l.key().equals("types") || l.key().equals("drives") || l.key().equals("heat")
                || l.key().equals("pressure") || l.key().equals("wear");
        if (!fill) return TermKit.GREEN;
        float f = l.frac();
        return f > 0.95f ? TermKit.RED : f > 0.8f ? TermKit.AMBER : TermKit.GREEN;
    }

    private void footer(GuiGraphics g, int x, int fy, int w) {
        TermKit.rule(g, x + 8, fy, w - 16, TermKit.FAINT);
        int warn = 0, alarm = 0;
        long used = 0, cap = 0;
        for (DataEntry e : menu.entries) {
            if (e.state() == DataSource.ALARM) alarm++;
            else if (e.state() == DataSource.WARN) warn++;
            DataEntry.Line s = e.line("storage_used");
            if (s != null) {
                used += (long) s.value();
                cap += (long) s.max();
            }
        }
        int ty = fy + 4;
        Component a = Component.translatable("gui.nuclearsq.data.terminal.sum.alarms", alarm, warn);
        TermKit.text(g, font, a, x + 8, ty, alarm > 0 ? TermKit.RED : warn > 0 ? TermKit.AMBER : TermKit.MID);
        if (cap > 0) {
            Component s = Component.translatable("gui.nuclearsq.data.terminal.sum.storage", StorageFormat.count(used), StorageFormat.count(cap));
            TermKit.text(g, font, s, x + 8, fy + 15, TermKit.MID);
        }
        DataTerminalMenu.Summary c = menu.summary;
        Component cc = Component.translatable("gui.nuclearsq.data.terminal.sum.compute", c.cpuUsed(), c.cpu(), com.sqc.nuclearsq.storage.StorageValues.gb(c.ramUsed()), com.sqc.nuclearsq.storage.StorageValues.gb(c.ram()));
        TermKit.right(g, font, cc, x + w - 8, ty, c.cpu() == 0 && c.ram() == 0 ? TermKit.DIM : TermKit.CYAN);
        TermKit.tip(x + w - 8 - TermKit.width(font, cc), ty - 1, TermKit.width(font, cc), 10, Component.translatable("gui.nuclearsq.data.terminal.sum.compute.tip"));
        TermKit.right(g, font, Component.translatable("gui.nuclearsq.data.terminal.hint"), x + w - 8, fy + 15, TermKit.DIM);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        int bs = button == 0 ? bar.press(mx, my, scroll) : -1;
        if (bs >= 0) {
            scroll = bs;
            return true;
        }
        int x = leftPos, y = topPos, w = imageWidth, sy = y + 21;
        if (button == 0 && in(mx, my, x + 8, sy, 150, 12)) {
            filter = Mth.clamp((int) ((mx - x - 8) * 3 / 150), 0, 2);
            FILTER.put(menu.pos.immutable(), filter);
            scroll = 0;
            return true;
        }
        if (button == 0 && in(mx, my, x + 162, sy, 16, 12)) {
            expandAll();
            return true;
        }
        if (button == 0 && in(mx, my, x + 180, sy, 16, 12)) {
            open.clear();
            scroll = 0;
            return true;
        }
        if (in(mx, my, x + 202, sy, w - 210, 12)) {
            search.setFocused(true);
            setFocused(search);
            if (button == 1) search.setValue("");
            search.mouseClicked(mx, my, 0);
            return true;
        }
        search.setFocused(false);
        if (button == 0) {
            for (int r = 0; r < ROWS; r++) {
                int i = r + scroll, ry = y + TOP + r * ROW_H;
                if (i < rows.size() && in(mx, my, x + 6, ry - 2, w - 12, ROW_H)) {
                    click(rows.get(i), mx, my, ry);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click(Row r, double mx, double my, int ry) {
        int xr = leftPos + imageWidth - 10, cx = leftPos + 8 + r.depth * INDENT + 8;
        BlockPos src = r.e.pos();
        switch (r.kind) {
            case SWITCH -> {
                if (in(mx, my, xr - 40, ry - 2, 40, 11)) send(new DataPayloads.Control(src, r.line.key(), r.line.value() != 0 ? 0 : 1));
            }
            case STEP -> {
                DataEntry.Line l = r.line;
                if (in(mx, my, xr - 70, ry - 2, 12, 11) && l.value() > 0) send(new DataPayloads.Control(src, l.key(), Math.max(0, l.value() - l.step())));
                if (in(mx, my, xr - 12, ry - 2, 12, 11) && l.value() < l.max()) send(new DataPayloads.Control(src, l.key(), Math.min(l.max(), l.value() + l.step())));
            }
            case ACTIONS -> {
                if (in(mx, my, cx, ry - 2, OPEN_W, 11)) send(new DataPayloads.Select(src));
                else if (in(mx, my, cx + OPEN_W + 4, ry - 2, LOCATE_W, 11)) send(new DataPayloads.Locate(src));
            }
            default -> {
                if (folder(r) && !r.id.isEmpty() && !open.remove(r.id)) {
                    open.add(r.id);
                    if (r.kind == Kind.MACHINE && open.stream().noneMatch(s -> s.startsWith(r.id + "/"))) openAll(open, r.e);
                }
            }
        }
    }

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {
        PacketDistributor.sendToServer(p);
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void expandAll() {
        for (DataEntry e : menu.entries) if (shown(e)) openAll(open, e);
    }

    private static void openAll(Set<String> open, DataEntry e) {
        String id = machineId(e);
        open.add(id);
        open.add(id + "/values");
        open.add(id + "/controls");
        if (!e.rack()) return;
        open.add(id + "/slots");
        for (int i = 0; i < e.slots().size(); i++) if (e.slots().get(i).kind() == DataEntry.DRIVE) open.add(id + "/slot/" + i);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (search.isFocused()) {
                search.setFocused(false);
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (search.isFocused()) return search.keyPressed(key, scan, mods) || search.canConsumeInput();
        if (key == GLFW.GLFW_KEY_F && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            search.setFocused(true);
            setFocused(search);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int bs = bar.drag(my);
        if (bs >= 0) {
            scroll = bs;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        bar.release();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(sy) * 2, 0, maxScroll());
        return true;
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
