package com.sqc.nuclearsq.data.client;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.sqc.nuclearsq.data.LogEntry;
import com.sqc.nuclearsq.data.LogbookMenu;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class LogbookScreen extends AbstractContainerScreen<LogbookMenu> {
    private static final int LINE_H = 10, FILTER_W = 74, FILTER_H = 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static int filter;

    private int scroll;
    private int lastRows = -1;

    private record Row(String head, FormattedCharSequence text, int severity, boolean separator) {}

    public LogbookScreen(LogbookMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 320;
        this.imageHeight = 200;
    }

    private static int color(int severity) {
        return switch (severity) {
            case LogEntry.ALARM -> TermKit.RED;
            case LogEntry.WARN -> TermKit.AMBER;
            case LogEntry.CLEAR -> TermKit.GREEN;
            default -> TermKit.MID;
        };
    }

    private static boolean visible(LogEntry e) {
        return switch (filter) {
            case 1 -> e.severity() >= LogEntry.WARN;
            case 2 -> e.severity() == LogEntry.ALARM;
            default -> true;
        };
    }

    private static String head(LogEntry e) {
        ZoneId zone = ZoneId.systemDefault();
        String real = Instant.ofEpochMilli(e.millis()).atZone(zone).format(TIME);
        return "T" + LogEntry.day(e.dayTime()) + " " + LogEntry.clock(e.dayTime()) + "  " + real;
    }

    private List<Row> rows(int textWidth) {
        List<Row> out = new ArrayList<>();
        LocalDate lastDay = null;
        for (LogEntry e : menu.entries) {
            if (!visible(e)) continue;
            LocalDate day = Instant.ofEpochMilli(e.millis()).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!day.equals(lastDay)) {
                out.add(new Row("", TermKit.mono(DATE.format(day)).getVisualOrderText(), LogEntry.INFO, true));
                lastDay = day;
            }
            List<FormattedCharSequence> lines = font.split(TermKit.mono(e.text()), textWidth);
            if (lines.isEmpty()) lines = List.of(FormattedCharSequence.EMPTY);
            for (int i = 0; i < lines.size(); i++) out.add(new Row(i == 0 ? head(e) : "", lines.get(i), e.severity(), false));
        }
        return out;
    }

    private int headWidth() {
        return font.width(TermKit.mono("T999 00:00  00:00:00")) + 8;
    }

    private int listTop() {
        return topPos + 38;
    }

    private int listRows() {
        return (topPos + imageHeight - 16 - listTop()) / LINE_H;
    }

    private Component filterLabel(int i) {
        return Component.translatable("gui.nuclearsq.logbook.filter." + i);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (TermKit.styleClick(mx, my)) return true;
            for (int i = 0; i < 3; i++) {
                int fx = leftPos + 8 + i * (FILTER_W + 4), fy = topPos + 22;
                if (mx >= fx && mx < fx + FILTER_W && my >= fy && my < fy + FILTER_H) {
                    filter = i;
                    scroll = 0;
                    lastRows = -1;
                    return true;
                }
            }
            int jx = leftPos + imageWidth - 8 - FILTER_W, jy = topPos + 22;
            if (mx >= jx && mx < jx + FILTER_W && my >= jy && my < jy + FILTER_H) {
                scroll = 0;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll + (int) Math.signum(sy) * 3);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        int page = Math.max(1, listRows() - 1);
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll++;
            case GLFW.GLFW_KEY_DOWN -> scroll = Math.max(0, scroll - 1);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll += page;
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.max(0, scroll - page);
            case GLFW.GLFW_KEY_HOME -> scroll = Integer.MAX_VALUE / 2;
            case GLFW.GLFW_KEY_END -> scroll = 0;
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8);
        int total = menu.entries.size();
        TermKit.header(g, font, title, Component.translatable("gui.nuclearsq.logbook.count", total), x + 8, y + 7, w - 16, TermKit.MID);
        TermKit.rule(g, x + 8, y + 18, w - 16, TermKit.DIM);
        for (int i = 0; i < 3; i++) {
            int fx = x + 8 + i * (FILTER_W + 4), fy = y + 22;
            boolean hover = mx >= fx && mx < fx + FILTER_W && my >= fy && my < fy + FILTER_H;
            TermKit.button(g, font, fx, fy, FILTER_W, FILTER_H, filterLabel(i), i == 2 ? TermKit.RED : i == 1 ? TermKit.AMBER : TermKit.GREEN,
                    true, hover, filter == i);
        }
        int jx = x + w - 8 - FILTER_W, jy = y + 22;
        TermKit.button(g, font, jx, jy, FILTER_W, FILTER_H, Component.translatable("gui.nuclearsq.logbook.latest"), TermKit.CYAN, scroll > 0,
                mx >= jx && mx < jx + FILTER_W && my >= jy && my < jy + FILTER_H, false);
        TermKit.rule(g, x + 8, y + 36, w - 16, TermKit.FAINT);

        int headW = headWidth();
        List<Row> rows = rows(w - 20 - headW);
        int shown = listRows();
        if (scroll > 0 && lastRows >= 0 && rows.size() > lastRows) scroll += rows.size() - lastRows;
        lastRows = rows.size();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - shown));
        int end = rows.size() - scroll;
        int top = listTop();
        if (rows.isEmpty()) {
            TermKit.text(g, font, Component.translatable(total == 0 ? "gui.nuclearsq.logbook.empty" : "gui.nuclearsq.logbook.empty_filter"),
                    x + 10, top + 2, TermKit.DIM);
        }
        for (int r = 0; r < shown; r++) {
            int i = end - shown + r;
            if (i < 0 || i >= rows.size()) continue;
            Row row = rows.get(i);
            int ry = top + r * LINE_H;
            if (row.separator) {
                TermKit.rule(g, x + 10, ry + 4, 30, TermKit.FAINT);
                g.drawString(font, row.text, x + 44, ry, TermKit.tcol(TermKit.DIM), false);
                int ex = x + 44 + font.width(row.text) + 4;
                TermKit.rule(g, ex, ry + 4, Math.max(0, x + w - 10 - ex), TermKit.FAINT);
                continue;
            }
            if (!row.head.isEmpty()) g.drawString(font, TermKit.mono(row.head), x + 10, ry, TermKit.tcol(TermKit.DIM), false);
            g.drawString(font, row.text, x + 10 + headW, ry, TermKit.tcol(color(row.severity)), false);
        }
        int by = y + h - 12;
        TermKit.rule(g, x + 8, by - 3, w - 16, TermKit.FAINT);
        TermKit.text(g, font, Component.translatable("gui.nuclearsq.logbook.hint"), x + 10, by, TermKit.DIM);
        if (scroll > 0) TermKit.right(g, font, Component.translatable("gui.nuclearsq.logbook.older", scroll), x + w - 10, by, TermKit.AMBER);
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
