package com.sqc.nuclearsq.nanobot.client;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.nanobot.GadgetData;
import com.sqc.nuclearsq.nanobot.NanoCanisterItem;
import com.sqc.nuclearsq.nanobot.NanoCanisterMenu;
import com.sqc.nuclearsq.nanobot.NanoControlPayload;
import com.sqc.nuclearsq.nanobot.NanoGadget;
import com.sqc.nuclearsq.nanobot.NanoPort;
import com.sqc.nuclearsq.nanobot.NanoSettings;
import com.sqc.nuclearsq.nanobot.NanoTask;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class NanoCanisterScreen extends AbstractContainerScreen<NanoCanisterMenu> {
    private static final int W = 324;
    private static final int TASK_X = 10, TASK_Y = 45, TASK_W = 140, TASK_H = 11, TASK_STEP = 12;
    private static final int TAB_X = 6, TAB_Y = 24, TAB_W = 148, TAB_H = 12, G_X = 10, G_Y = 46, G_W = 140, G_H2 = 69, GO_Y = 143;
    private static final int FRAME_Y = 40, FRAME_H = 102;
    private boolean gadgetTab;
    private static final int START_X = 160, START_Y = 142, START_W = 158, START_H = 14;
    private static final int RADIUS_MINUS_X = 276, RADIUS_PLUS_X = 296, RADIUS_Y = 128, RADIUS_BTN_W = 16, RADIUS_BTN_H = 12;
    private static final int[] TASK_COLORS = {TermKit.GREEN, TermKit.CYAN, TermKit.AMBER, TermKit.GREEN, TermKit.AMBER, TermKit.CYAN, TermKit.VIOLET};
    private static final int[] PHASE_COLORS = {TermKit.MID, TermKit.CYAN, TermKit.CYAN, TermKit.GREEN, TermKit.AMBER, TermKit.AMBER};

    public NanoCanisterScreen(NanoCanisterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = 250;
        this.titleLabelY = 10000;
        this.inventoryLabelY = 10000;
    }

    private boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    private static Component k(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.nanobots." + key, args);
    }

    private void tip(int rx, int ry, int rw, int rh, Component head, String key, Object... args) {
        TermKit.tip(leftPos + rx, topPos + ry, rw, rh, head, k("tip." + key, args));
    }

    private int seg(double mx, double my, int rx, int ry, int rw, int rh, int n) {
        return in(mx, my, rx, ry, rw, rh) ? Mth.clamp((int) (mx - leftPos - rx) * n / rw, 0, n - 1) : -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if ((hoveredSlot != null && hoveredSlot.hasItem()) || !TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        NanoTask[] tasks = NanoTask.values();
        int current = menu.data(NanoCanisterMenu.D_TASK) % tasks.length;
        NanoTask cur = tasks[current];
        boolean running = menu.data(NanoCanisterMenu.D_RUNNING) == 1;
        int phase = menu.data(NanoCanisterMenu.D_PHASE);
        int progress = menu.data(NanoCanisterMenu.D_PROGRESS) - 1;
        ItemStack can = menu.canister();
        GadgetData gd = NanoGadget.data(can);

        TermKit.monitor(g, x - 4, y - 4, W + 8, imageHeight + 8);
        TermKit.headBadge(g, font, Component.translatable("gui.nuclearsq.nanobots.title"), k("phase." + Mth.clamp(phase, 0, 5)),
                x + 8, y + 8, W - 16, PHASE_COLORS[Mth.clamp(phase, 0, 5)]);
        tip(W - 110, 4, 102, 14, k("status.phase"), "phase");
        TermKit.rule(g, x + 8, y + 20, W - 16, TermKit.DIM);

        TermKit.segments(g, font, x + TAB_X, y + TAB_Y, TAB_W, TAB_H, new Component[] {k("tab.tasks"), k("tab.gadget")}, gadgetTab ? 1 : 0, gadgetTab ? TermKit.AMBER : TermKit.GREEN,
                seg(mx, my, TAB_X, TAB_Y, TAB_W, TAB_H, 2));
        tip(TAB_X, TAB_Y, TAB_W / 2, TAB_H, k("tab.tasks"), "tab.tasks");
        tip(TAB_X + TAB_W / 2, TAB_Y, TAB_W / 2, TAB_H, k("tab.gadget"), "tab.gadget");

        if (!gadgetTab) {
            TermKit.frame(g, font, x + 6, y + FRAME_Y, 148, FRAME_H, k("tasks"), TermKit.MID);
            for (int i = 0; i < NanoTask.autoCount(); i++) {
                int by = TASK_Y + i * TASK_STEP;
                TermKit.button(g, font, x + TASK_X, y + by, TASK_W, TASK_H, tasks[i].label(), TASK_COLORS[i % TASK_COLORS.length], true,
                        in(mx, my, TASK_X, by, TASK_W, TASK_H), i == current);
                tip(TASK_X, by, TASK_W, TASK_H, tasks[i].label(), "task");
            }
            TermKit.text(g, font, k("hint1"), x + 8, y + 142, TermKit.DIM);
            TermKit.text(g, font, k("hint2"), x + 8, y + 150, TermKit.DIM);
            TermKit.text(g, font, k("inventory"), x + 8, y + 159, TermKit.MID);
        } else {
            gadgetPanel(g, mx, my, x, y, current, cur, gd, can);
        }

        TermKit.field(g, font, x + 160, y + 26, 158, k("status.task"), cur.label(), running ? TermKit.GREEN : TermKit.MID);
        int tw = 50;
        TermKit.tile(g, font, x + 160, y + 38, tw, 32, k("status.phase"), k("phase." + Mth.clamp(phase, 0, 5)), running ? TermKit.CYAN : TermKit.MID, -1);
        TermKit.tile(g, font, x + 214, y + 38, tw, 32, k("status.bots"),
                Component.literal(menu.data(NanoCanisterMenu.D_BOTS) + " x" + String.format("%.1f", menu.data(NanoCanisterMenu.D_SPEED) / 10.0)), TermKit.GREEN, -1);
        int cargo = menu.data(NanoCanisterMenu.D_CARGO);
        TermKit.tile(g, font, x + 268, y + 38, tw, 32, k("status.cargo"), Component.literal(String.valueOf(cargo)), cargo > 0 ? TermKit.AMBER : TermKit.MID, -1);
        tip(160, 38, tw, 32, k("status.phase"), "phase");
        tip(214, 38, tw, 32, k("status.bots"), "bots");
        tip(268, 38, tw, 32, k("status.cargo"), "cargo");
        TermKit.bar(g, x + 160, y + 73, 158, 5, progress < 0 ? 0f : progress / 100f, progress > 0 ? TermKit.AMBER : TermKit.DIM);
        tip(160, 72, 158, 7, k("progress"), "progress");

        TermKit.frame(g, font, x + 160, y + 82, 158, 42, k("upgrades"), TermKit.VIOLET);
        for (Slot s : menu.slots) {
            boolean own = s.index < NanoCanisterMenu.UP_SLOTS;
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, own ? TermKit.VIOLET : TermKit.FAINT);
        }
        TermKit.text(g, font, k("effects", String.format("%.1f", menu.data(NanoCanisterMenu.D_SPEED) / 10.0), menu.data(NanoCanisterMenu.D_BOTS), menu.data(NanoCanisterMenu.D_MAXRADIUS)),
                x + 166, y + 116, TermKit.VIOLET);
        tip(160, 82, 158, 42, k("upgrades"), "upgrades");

        int radius = menu.data(NanoCanisterMenu.D_RADIUS), max = menu.data(NanoCanisterMenu.D_MAXRADIUS);
        TermKit.field(g, font, x + 160, y + 130, 110, k("radius"), Component.literal(radius + " / " + max), TermKit.GREEN);
        TermKit.button(g, font, x + RADIUS_MINUS_X, y + RADIUS_Y, RADIUS_BTN_W, RADIUS_BTN_H, Component.literal("-"), TermKit.GREEN, radius > NanoSettings.MIN_RADIUS,
                in(mx, my, RADIUS_MINUS_X, RADIUS_Y, RADIUS_BTN_W, RADIUS_BTN_H), false);
        TermKit.button(g, font, x + RADIUS_PLUS_X, y + RADIUS_Y, RADIUS_BTN_W, RADIUS_BTN_H, Component.literal("+"), TermKit.GREEN, radius < max,
                in(mx, my, RADIUS_PLUS_X, RADIUS_Y, RADIUS_BTN_W, RADIUS_BTN_H), false);
        tip(160, 128, 160, 12, k("radius"), "radius");

        TermKit.button(g, font, x + START_X, y + START_Y, START_W, START_H, k(running ? "recall" : "start"), running ? TermKit.AMBER : TermKit.GREEN, true,
                in(mx, my, START_X, START_Y, START_W, START_H), false);
        tip(START_X, START_Y, START_W, START_H, k(running ? "recall" : "start"), running ? "recall" : "start");
        TermKit.scanlines(g, x - 4, y - 4, W + 8, imageHeight + 8);
    }

    private void gadgetPanel(GuiGraphics g, int mx, int my, int x, int y, int current, NanoTask cur, GadgetData gd, ItemStack can) {
        boolean pasteMode = cur == NanoTask.GADGET_PASTE;
        TermKit.frame(g, font, x + 6, y + FRAME_Y, 148, FRAME_H, k("gadget"), TermKit.AMBER);
        int base = NanoTask.autoCount();
        TermKit.segments(g, font, x + G_X, y + G_Y, G_W, 12, new Component[] {k("fn.0"), k("fn.1"), k("fn.2")}, current - base >= 0 && current - base < 3 ? current - base : -1, TermKit.AMBER,
                seg(mx, my, G_X, G_Y, G_W, 12, 3));
        TermKit.segments(g, font, x + G_X, y + G_Y + 14, G_W, 12, new Component[] {k("fn.3"), k("fn.4")}, current - base >= 3 ? current - base - 3 : -1, current == base + 3 ? TermKit.CYAN : TermKit.GREEN,
                seg(mx, my, G_X, G_Y + 14, G_W, 12, 2));
        for (int i = 0; i < 3; i++) tip(G_X + i * G_W / 3, G_Y, G_W / 3, 12, k("fn." + i), "fn." + i);
        for (int i = 0; i < 2; i++) tip(G_X + i * G_W / 2, G_Y + 14, G_W / 2, 12, k("fn." + (3 + i)), "fn." + (3 + i));
        int y0 = G_Y + 28;
        if (pasteMode) {
            GadgetData.Paste ps = gd.paste();
            TermKit.button(g, font, x + G_X, y + y0, G_H2, 12, k("rot", ps.rot() * 90), TermKit.CYAN, true, in(mx, my, G_X, y0, G_H2, 12), false);
            TermKit.button(g, font, x + G_X + G_H2 + 2, y + y0, G_H2, 12, k("mirror." + (ps.mirror() ? 1 : 0)), TermKit.CYAN, true, in(mx, my, G_X + G_H2 + 2, y0, G_H2, 12), ps.mirror());
            TermKit.button(g, font, x + G_X, y + y0 + 14, G_H2, 12, k("paste.mode." + (ps.replace() ? 1 : 0)), TermKit.AMBER, true, in(mx, my, G_X, y0 + 14, G_H2, 12), ps.replace());
            TermKit.button(g, font, x + G_X + G_H2 + 2, y + y0 + 14, G_H2, 12, k("source." + gd.source()), TermKit.GREEN, true, in(mx, my, G_X + G_H2 + 2, y0 + 14, G_H2, 12), gd.source() == 1);
            for (int i = 0; i < 3; i++) {
                int bx = G_X + i * 47, off = ps.offset(i);
                TermKit.button(g, font, x + bx, y + y0 + 28, 45, 12, Component.literal("XYZ".charAt(i) + " " + (off > 0 ? "+" : "") + off), TermKit.CYAN, true, in(mx, my, bx, y0 + 28, 45, 12), off != 0);
                tip(bx, y0 + 28, 45, 12, Component.literal("XYZ".charAt(i) + " " + off), "offset");
            }
            int tn = menu.data(NanoCanisterMenu.D_TEMPLATE);
            TermKit.text(g, font, k(tn > 0 ? "template" : "template.none", tn), x + G_X, y + y0 + 46, tn > 0 ? TermKit.GREEN : TermKit.DIM);
            tip(G_X, y0, G_H2, 12, k("rot", ps.rot() * 90), "rot");
            tip(G_X + G_H2 + 2, y0, G_H2, 12, k("mirror." + (ps.mirror() ? 1 : 0)), "mirror");
            tip(G_X, y0 + 14, G_H2, 12, k("paste.mode." + (ps.replace() ? 1 : 0)), "pmode");
            tip(G_X + G_H2 + 2, y0 + 14, G_H2, 12, k("source." + gd.source()), "source");
        } else {
            TermKit.button(g, font, x + G_X, y + y0, G_W, 12, k("form", k("shape." + gd.shape())), TermKit.CYAN, true, in(mx, my, G_X, y0, G_W, 12), false);
            tip(G_X, y0, G_W, 12, k("form", k("shape." + gd.shape())), "form");
            TermKit.button(g, font, x + G_X, y + y0 + 14, G_H2, 12, k("hollow." + (gd.hollow() ? 1 : 0)), TermKit.CYAN, true, in(mx, my, G_X, y0 + 14, G_H2, 12), gd.hollow());
            TermKit.button(g, font, x + G_X + G_H2 + 2, y + y0 + 14, G_H2, 12, k("source." + gd.source()), TermKit.GREEN, true, in(mx, my, G_X + G_H2 + 2, y0 + 14, G_H2, 12), gd.source() == 1);
            tip(G_X, y0 + 14, G_H2, 12, k("hollow." + (gd.hollow() ? 1 : 0)), "hollow");
            tip(G_X + G_H2 + 2, y0 + 14, G_H2, 12, k("source." + gd.source()), "source");
            boolean round = GadgetData.isRound(gd.shape());
            TermKit.button(g, font, x + G_X, y + y0 + 28, 14, 12, Component.literal("-"), TermKit.CYAN, round, in(mx, my, G_X, y0 + 28, 14, 12), false);
            TermKit.text(g, font, k("radius.short", gd.radius()), x + G_X + 18, y + y0 + 30, round ? TermKit.CYAN : TermKit.DIM);
            TermKit.button(g, font, x + G_X + 74, y + y0 + 28, 14, 12, Component.literal("+"), TermKit.CYAN, round, in(mx, my, G_X + 74, y0 + 28, 14, 12), false);
            boolean axisOn = gd.shape() == GadgetData.CIRCLE || gd.shape() == GadgetData.CYLINDER;
            TermKit.button(g, font, x + G_X + 92, y + y0 + 28, 48, 12, k("axis." + gd.axis()), TermKit.CYAN, axisOn, in(mx, my, G_X + 92, y0 + 28, 48, 12), false);
            boolean tall = gd.shape() == GadgetData.CYLINDER;
            TermKit.button(g, font, x + G_X, y + y0 + 42, 14, 12, Component.literal("-"), TermKit.CYAN, tall, in(mx, my, G_X, y0 + 42, 14, 12), false);
            TermKit.text(g, font, k("height.short", gd.height()), x + G_X + 18, y + y0 + 44, tall ? TermKit.CYAN : TermKit.DIM);
            TermKit.button(g, font, x + G_X + 74, y + y0 + 42, 14, 12, Component.literal("+"), TermKit.CYAN, tall, in(mx, my, G_X + 74, y0 + 42, 14, 12), false);
            tip(G_X, y0 + 28, 88, 12, k("radius.short", gd.radius()), "gradius");
            tip(G_X + 92, y0 + 28, 48, 12, k("axis." + gd.axis()), "axis");
            tip(G_X, y0 + 42, 88, 12, k("height.short", gd.height()), "gheight");
        }
        boolean port = NanoPort.coupled(can) != null;
        TermKit.text(g, font, k(port ? "port.yes" : "port.no"), x + G_X, y + y0 + 56, port ? TermKit.GREEN : TermKit.DIM);
        tip(G_X, y0 + 54, G_W, 10, k(port ? "port.yes" : "port.no"), "port");
        boolean ready = gd.ready(cur);
        TermKit.button(g, font, x + 10, y + GO_Y, 34, 12, k("gadget.confirm"), TermKit.GREEN, ready, in(mx, my, 10, GO_Y, 34, 12), false);
        TermKit.button(g, font, x + 46, y + GO_Y, 40, 12, k("gadget.cancel"), TermKit.AMBER, gd.points() > 0, in(mx, my, 46, GO_Y, 40, 12), false);
        TermKit.button(g, font, x + 88, y + GO_Y, 30, 12, k("gadget.undo"), TermKit.VIOLET, menu.data(NanoCanisterMenu.D_UNDO) == 1, in(mx, my, 88, GO_Y, 30, 12), false);
        TermKit.button(g, font, x + 120, y + GO_Y, 30, 12, k("gadget.redo"), TermKit.VIOLET, menu.data(NanoCanisterMenu.D_REDO) == 1, in(mx, my, 120, GO_Y, 30, 12), false);
        tip(10, GO_Y, 34, 12, k("gadget.confirm"), "confirm");
        tip(46, GO_Y, 40, 12, k("gadget.cancel"), "cancel");
        tip(88, GO_Y, 30, 12, k("gadget.undo"), "undo");
        tip(120, GO_Y, 30, 12, k("gadget.redo"), "redo");
        int n = pasteMode ? menu.data(NanoCanisterMenu.D_TEMPLATE) : ready ? NanoGadget.cells(gd, NanoGadget.PREVIEW_MAX).size() : 0;
        TermKit.text(g, font, k("gadget.points", gd.points(), gd.needed(cur), n), x + 8, y + 159, TermKit.MID);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        if (button == 0) {
            if (in(mx, my, TAB_X, TAB_Y, TAB_W, TAB_H)) {
                gadgetTab = mx - leftPos - TAB_X >= TAB_W / 2;
                return true;
            }
            if (!gadgetTab) {
                for (int i = 0; i < NanoTask.autoCount(); i++) {
                    if (in(mx, my, TASK_X, TASK_Y + i * TASK_STEP, TASK_W, TASK_H)) return send(NanoCanisterMenu.ACT_TASK, i);
                }
            } else {
                int seg0 = seg(mx, my, G_X, G_Y, G_W, 12, 3), seg1 = seg(mx, my, G_X, G_Y + 14, G_W, 12, 2);
                if (seg0 >= 0) return send(NanoCanisterMenu.ACT_TASK, NanoTask.autoCount() + seg0);
                if (seg1 >= 0) return send(NanoCanisterMenu.ACT_TASK, NanoTask.autoCount() + 3 + seg1);
                GadgetData gd = NanoGadget.data(menu.canister());
                int y0 = G_Y + 28;
                boolean pasteMode = NanoCanisterItem.task(menu.canister()) == NanoTask.GADGET_PASTE;
                if (pasteMode) {
                    GadgetData.Paste ps = gd.paste();
                    if (in(mx, my, G_X, y0, G_H2, 12)) return send(NanoCanisterMenu.ACT_ROT, ps.rot() + 1);
                    if (in(mx, my, G_X + G_H2 + 2, y0, G_H2, 12)) return send(NanoCanisterMenu.ACT_MIRROR, ps.mirror() ? 0 : 1);
                    if (in(mx, my, G_X, y0 + 14, G_H2, 12)) return send(NanoCanisterMenu.ACT_PMODE, ps.replace() ? 0 : 1);
                    if (in(mx, my, G_X + G_H2 + 2, y0 + 14, G_H2, 12)) return send(NanoCanisterMenu.ACT_SOURCE, 1 - gd.source());
                    for (int i = 0; i < 3; i++) {
                        if (in(mx, my, G_X + i * 47, y0 + 28, 45, 12)) return send(NanoCanisterMenu.ACT_OFFX + i, ps.offset(i) + (hasShiftDown() ? 4 : 1));
                    }
                } else {
                    if (in(mx, my, G_X, y0, G_W, 12)) return send(NanoCanisterMenu.ACT_SHAPE, gd.shape() + 1);
                    if (in(mx, my, G_X, y0 + 14, G_H2, 12)) return send(NanoCanisterMenu.ACT_HOLLOW, gd.hollow() ? 0 : 1);
                    if (in(mx, my, G_X + G_H2 + 2, y0 + 14, G_H2, 12)) return send(NanoCanisterMenu.ACT_SOURCE, 1 - gd.source());
                    boolean round = GadgetData.isRound(gd.shape());
                    int step = hasShiftDown() ? 5 : 1;
                    if (round && in(mx, my, G_X, y0 + 28, 14, 12)) return send(NanoCanisterMenu.ACT_GRADIUS, gd.radius() - step);
                    if (round && in(mx, my, G_X + 74, y0 + 28, 14, 12)) return send(NanoCanisterMenu.ACT_GRADIUS, gd.radius() + step);
                    if ((gd.shape() == GadgetData.CIRCLE || gd.shape() == GadgetData.CYLINDER) && in(mx, my, G_X + 92, y0 + 28, 48, 12)) return send(NanoCanisterMenu.ACT_AXIS, gd.axis() + 1);
                    if (gd.shape() == GadgetData.CYLINDER && in(mx, my, G_X, y0 + 42, 14, 12)) return send(NanoCanisterMenu.ACT_GHEIGHT, gd.height() - step);
                    if (gd.shape() == GadgetData.CYLINDER && in(mx, my, G_X + 74, y0 + 42, 14, 12)) return send(NanoCanisterMenu.ACT_GHEIGHT, gd.height() + step);
                }
                if (in(mx, my, G_X, y0 + 54, G_W, 10) && NanoPort.coupled(menu.canister()) != null) return send(NanoCanisterMenu.ACT_UNPORT, 0);
                if (in(mx, my, 10, GO_Y, 34, 12)) return send(NanoCanisterMenu.ACT_CONFIRM, 0);
                if (in(mx, my, 46, GO_Y, 40, 12)) return send(NanoCanisterMenu.ACT_CANCEL, 0);
                if (in(mx, my, 88, GO_Y, 30, 12)) return send(NanoCanisterMenu.ACT_UNDO, 0);
                if (in(mx, my, 120, GO_Y, 30, 12)) return send(NanoCanisterMenu.ACT_REDO, 0);
            }
            if (in(mx, my, START_X, START_Y, START_W, START_H)) return send(NanoCanisterMenu.ACT_TOGGLE, 0);
            int radius = menu.data(NanoCanisterMenu.D_RADIUS), max = menu.data(NanoCanisterMenu.D_MAXRADIUS);
            int step = hasShiftDown() ? 8 : 2;
            if (in(mx, my, RADIUS_MINUS_X, RADIUS_Y, RADIUS_BTN_W, RADIUS_BTN_H) && radius > NanoSettings.MIN_RADIUS) return send(NanoCanisterMenu.ACT_RADIUS, Math.max(NanoSettings.MIN_RADIUS, radius - step));
            if (in(mx, my, RADIUS_PLUS_X, RADIUS_Y, RADIUS_BTN_W, RADIUS_BTN_H) && radius < max) return send(NanoCanisterMenu.ACT_RADIUS, Math.min(max, radius + step));
        }
        if (gadgetTab && (button == 1 || button == 2)) {
            GadgetData gd = NanoGadget.data(menu.canister());
            int y0 = G_Y + 28;
            if (NanoCanisterItem.task(menu.canister()) == NanoTask.GADGET_PASTE) {
                GadgetData.Paste ps = gd.paste();
                if (button == 1 && in(mx, my, G_X, y0, G_H2, 12)) return send(NanoCanisterMenu.ACT_ROT, ps.rot() - 1);
                for (int i = 0; i < 3; i++) {
                    if (in(mx, my, G_X + i * 47, y0 + 28, 45, 12)) return send(NanoCanisterMenu.ACT_OFFX + i, button == 2 ? 0 : ps.offset(i) - (hasShiftDown() ? 4 : 1));
                }
            } else if (button == 1 && in(mx, my, G_X, y0, G_W, 12)) {
                send(NanoCanisterMenu.ACT_SHAPE, gd.shape() - 1);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean send(int action, int value) {
        PacketDistributor.sendToServer(new NanoControlPayload(action, value));
        return true;
    }
}
