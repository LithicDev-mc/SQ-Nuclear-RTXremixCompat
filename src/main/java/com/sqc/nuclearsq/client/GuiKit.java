package com.sqc.nuclearsq.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class GuiKit {
    public static final int GREEN = 0xFF3CDC6E, RED = 0xFFFF4A3A, ORANGE = 0xFFE89A2A, YELLOW = 0xFFE8D060;
    public static final int BORDER = 0xFF1A1A1A, BG_DARK = 0xFF111111, BG_MID = 0xFF2A2A2A, BG_LIGHT = 0xFF3A3A3A,
            TEXT_DIM = 0xFF9A9A9A, TEXT_OFF = 0xFF6A6A6A, BORDER_OFF = 0xFF3A3A3A, FILL_OFF = 0xFF4A4A4A, WHITE = 0xFFFFFFFF;

    private GuiKit() {}

    public static int textOn(int bg) {
        double l = 0.2126 * lin(bg >> 16 & 255) + 0.7152 * lin(bg >> 8 & 255) + 0.0722 * lin(bg & 255);
        return l > 0.19 ? BG_DARK : WHITE;
    }

    private static double lin(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static void centered(GuiGraphics g, Font font, Component c, int cx, int y, int col) {
        g.drawString(font, c, cx - font.width(c) / 2, y, col, col != BG_DARK);
    }

    public static void frame(GuiGraphics g, Font font, int x, int y, int w, int h, Component title) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, BORDER);
        g.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        g.fill(x, y, x + w, y + 2, 0xFFFFFFFF);
        g.fill(x, y, x + 2, y + h, 0xFFFFFFFF);
        g.fill(x, y + h - 2, x + w, y + h, 0xFF555555);
        g.fill(x + w - 2, y, x + w, y + h, 0xFF555555);
        g.drawString(font, title, x + 8, y + 7, 0xFF404040, false);
    }

    public static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    public static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    public static void gauge(GuiGraphics g, Font font, int cx, int cy, int r, float value, float redFrom,
                             String num, String unit, Component caption) {
        disc(g, cx, cy, r + 2, BORDER);
        disc(g, cx, cy, r + 1, 0xFF8A8A8A);
        disc(g, cx, cy, r, 0xFF2B2B2B);
        disc(g, cx, cy, r - 3, BG_DARK);
        for (int i = 0; i <= 20; i++) {
            float t = i / 20f;
            double a = Math.toRadians(135 + 270 * t);
            boolean red = t >= redFrom;
            int len = (i % 5 == 0 ? 6 : 3) + (red ? 2 : 0);
            line(g, cx + (int) Math.round(Math.cos(a) * (r - 4)), cy + (int) Math.round(Math.sin(a) * (r - 4)),
                    cx + (int) Math.round(Math.cos(a) * (r - 4 - len)), cy + (int) Math.round(Math.sin(a) * (r - 4 - len)), red ? RED : GREEN);
        }
        float v = Mth.clamp(value, 0f, 1f);
        boolean inRed = v >= redFrom;
        double a = Math.toRadians(135 + 270 * v);
        line(g, cx, cy, cx + (int) Math.round(Math.cos(a) * (r - 7)), cy + (int) Math.round(Math.sin(a) * (r - 7)), inRed ? RED : GREEN);
        disc(g, cx, cy, 3, TEXT_DIM);
        g.drawCenteredString(font, inRed ? "! " + num : num, cx, cy + r / 2 - 2, WHITE);
        g.drawCenteredString(font, unit, cx, cy + r / 2 + 8, TEXT_DIM);
        if (caption != null) g.drawCenteredString(font, caption, cx, cy + r + 5, 0xFF404040);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float value, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
        g.fill(x, y, x + w, y + h, 0xFF5A5A5A);
        int fill = Math.round(h * Mth.clamp(value, 0f, 1f));
        g.fill(x, y + h - fill, x + w, y + h, color);
        for (int i = 1; i < 8; i++) g.fill(x, y + h * i / 8, x + (i % 4 == 0 ? w : w / 2), y + h * i / 8 + 1, 0x88000000);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
        g.fill(x, y, x + w, y + h, 0xFF0A0A0A);
    }

    public static void toggle(GuiGraphics g, Font font, int x, int y, int w, int h, boolean on, Component label, int onColor, boolean hover) {
        toggle(g, font, x, y, w, h, on, label, onColor, hover, true);
    }

    public static void toggle(GuiGraphics g, Font font, int x, int y, int w, int h, boolean on, Component label, int onColor, boolean hover, boolean enabled) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, enabled ? BORDER : BORDER_OFF);
        g.fill(x, y, x + w, y + h, hover && enabled ? 0xFF4A4A4A : BG_LIGHT);
        int tw = 22, th = 12, tx = x + 4, ty = y + (h - th) / 2;
        g.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, BG_DARK);
        g.fill(tx, ty, tx + tw, ty + th, on ? (enabled ? onColor : FILL_OFF) : 0xFF555555);
        int kx = on ? tx + tw - 10 : tx;
        g.fill(kx, ty - 2, kx + 10, ty + th + 2, 0xFFDDDDDD);
        g.fill(kx + 1, ty - 1, kx + 9, ty + th + 1, TEXT_DIM);
        g.fill(kx + 4, ty - 1, kx + 6, ty + th + 1, 0xFFDDDDDD);
        int col = enabled ? WHITE : TEXT_OFF, ly = y + (h - 8) / 2;
        g.drawString(font, label, x + tw + 8, ly, col, enabled);
        Component st = on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        int sx = x + w - 4 - font.width(st);
        if (sx > x + tw + 12 + font.width(label)) g.drawString(font, st, sx, ly, enabled ? (on ? onColor : TEXT_DIM) : TEXT_OFF, enabled);
    }

    public static void multiSwitch(GuiGraphics g, Font font, int x, int y, int w, int h, int pos, Component[] labels, int[] colors, int hoverSeg) {
        multiSwitch(g, font, x, y, w, h, pos, labels, colors, hoverSeg, true);
    }

    public static void multiSwitch(GuiGraphics g, Font font, int x, int y, int w, int h, int pos, Component[] labels, int[] colors, int hoverSeg, boolean enabled) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, enabled ? BORDER : BORDER_OFF);
        g.fill(x, y, x + w, y + h, BG_MID);
        int n = labels.length, sw = w / n;
        for (int i = 0; i < n; i++) {
            int sx = x + i * sw, bg = enabled ? colors[i] : FILL_OFF;
            if (i == pos) {
                g.fill(sx + 1, y + 1, sx + sw - 1, y + h - 1, bg);
                g.fill(sx + 1, y + 1, sx + sw - 1, y + 2, 0x66FFFFFF);
                g.fill(sx + 1, y + h - 2, sx + sw - 1, y + h - 1, 0x66000000);
            } else if (i == hoverSeg && enabled) {
                g.fill(sx + 1, y + 1, sx + sw - 1, y + h - 1, BG_LIGHT);
            }
            if (i > 0) g.fill(sx, y + 3, sx + 1, y + h - 3, BG_DARK);
            centered(g, font, labels[i], sx + sw / 2, y + (h - 8) / 2, !enabled ? TEXT_OFF : i == pos ? textOn(bg) : i == hoverSeg ? WHITE : TEXT_DIM);
        }
    }

    public static void slider(GuiGraphics g, Font font, int x, int y, int w, int h, float frac, Component label, int fill) {
        slider(g, font, x, y, w, h, frac, label, fill, null, true);
    }

    public static void slider(GuiGraphics g, Font font, int x, int y, int w, int h, float frac, Component label, int fill, String value, boolean enabled) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, enabled ? BORDER : BORDER_OFF);
        g.fill(x, y, x + w, y + h, BG_LIGHT);
        int bg = enabled ? fill : FILL_OFF;
        int fe = x + 4 + Math.round((w - 8) * frac);
        g.fill(x, y, fe, y + h, bg);
        int hx = x + Math.round((w - 8) * frac);
        g.fill(hx, y - 2, hx + 8, y + h + 2, 0xFFDDDDDD);
        g.fill(hx + 1, y - 1, hx + 7, y + h + 1, 0xFF8A8A8A);
        Component c = value == null ? label : Component.empty().append(label).append(" " + value);
        if (!enabled) { centered(g, font, c, x + w / 2, y + 3, TEXT_OFF); return; }
        g.enableScissor(x, y, fe, y + h);
        centered(g, font, c, x + w / 2, y + 3, textOn(bg));
        g.disableScissor();
        g.enableScissor(fe, y, x + w, y + h);
        centered(g, font, c, x + w / 2, y + 3, WHITE);
        g.disableScissor();
    }
}
