package com.sqc.nuclearsq.machine.client;

import com.sqc.nuclearsq.NuclearClientConfig;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class TermKit {
    private TermKit() {}

    public static final int SCREEN = 0xFF040A06, BEZEL = 0xFF16191A, BEZEL_HI = 0xFF2A2F31, BEZEL_LO = 0xFF0B0C0D;
    public static final int GREEN = 0xFF41FF7A, MID = 0xFF22B04E, DIM = 0xFF136B30, FAINT = 0xFF0B3319;
    public static final int AMBER = 0xFFFFB23A, RED = 0xFFFF5A4E, CYAN = 0xFF5FE3FF, VIOLET = 0xFF9C8CFF;
    private static final int SCAN = 0x2A000000;

    private static final int M_BG = 0xFF202022, M_HEAD = 0xFF19191B, M_CARD = 0xFF2A2A2D, M_HI = 0xFF30323C;
    private static final int M_EDGE = 0xFF38383C, M_TRACK = 0xFF161618, M_WHITE = 0xFFFFFFFF;
    private static final int M_TEXT = 0xFFDDDDE0, M_MUTED = 0xFF8E8E96, M_SOFT = 0xFF6E6E76, M_DIM = 0xFF4A4A50;
    private static final int M_GREEN = 0xFF46C46A, M_AMBER = 0xFFE8A33D, M_RED = 0xFFE05A5A, M_CYAN = 0xFFB4C2FF;
    private static final int M_VIOLET = 0xFFA070D8, M_INK = 0xFF14171D;

    private static boolean modern = NuclearClientConfig.modern();

    public static boolean modern() {
        return modern;
    }

    public static int col(int c) {
        if (!modern) return c;
        return switch (c) {
            case SCREEN -> M_BG;
            case GREEN -> M_GREEN;
            case MID -> M_SOFT;
            case DIM -> M_EDGE;
            case FAINT -> M_HI;
            case AMBER -> M_AMBER;
            case RED -> M_RED;
            case CYAN -> M_CYAN;
            case VIOLET -> M_VIOLET;
            default -> c;
        };
    }

    public static int tcol(int c) {
        if (!modern) return c;
        return switch (c) {
            case GREEN -> M_TEXT;
            case MID -> M_MUTED;
            case DIM -> M_SOFT;
            case FAINT -> M_DIM;
            default -> col(c);
        };
    }

    public static void fill(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, col(color));
    }

    public static MutableComponent mono(Component c) {
        if (modern || Minecraft.getInstance().getWindow().getGuiScale() < 2) return c.copy();
        return c.copy().withStyle(s -> s.withFont(Minecraft.UNIFORM_FONT));
    }

    public static MutableComponent mono(String s) {
        return mono(Component.literal(s));
    }

    public static int width(Font font, Component c) {
        return font.width(mono(c));
    }

    public static void text(GuiGraphics g, Font font, Component c, int x, int y, int color) {
        g.drawString(font, mono(c), x, y, tcol(color), false);
    }

    public static void right(GuiGraphics g, Font font, Component c, int xRight, int y, int color) {
        text(g, font, c, xRight - width(font, c), y, color);
    }

    public static void center(GuiGraphics g, Font font, Component c, int cx, int y, int color) {
        text(g, font, c, cx - width(font, c) / 2, y, color);
    }

    public static void title(GuiGraphics g, Font font, Component title, int x, int y, int w) {
        if (modern && !headIcon.isEmpty()) {
            com.sqc.nuclearsq.progression.client.MachineThumbs.drawStack(g, headIcon, x, y - 4);
            x += 21;
            w -= 21;
        }
        Component c = title;
        if (width(font, title) > w) {
            String l = title.getString();
            while (l.length() > 1 && width(font, Component.literal(l + "..")) > w) l = l.substring(0, l.length() - 1);
            c = Component.literal(l + "..");
            tip(x, y - 1, w, 10, title);
        }
        if (modern) g.drawString(font, c, x, y, M_WHITE, false);
        else text(g, font, c, x, y, GREEN);
    }

    public static void caption(GuiGraphics g, Font font, Component c, int x, int y) {
        text(g, font, c, x, y, modern ? AMBER : MID);
    }

    public static void fit(GuiGraphics g, Font font, Component c, int x, int y, int w, int color) {
        if (width(font, c) <= w) {
            text(g, font, c, x, y, color);
            return;
        }
        String l = c.getString();
        while (l.length() > 1 && width(font, Component.literal(l + "..")) > w) l = l.substring(0, l.length() - 1);
        text(g, font, Component.literal(l + ".."), x, y, color);
        tip(x, y - 1, w, 10, c);
    }

    public static void wrap(GuiGraphics g, Font font, Component c, int x, int y, int w, int lines, int color) {
        List<FormattedCharSequence> parts = font.split(mono(c), w);
        for (int i = 0; i < Math.min(lines, parts.size()); i++) g.drawString(font, parts.get(i), x, y + i * 9, tcol(color), false);
        if (parts.size() > lines) tip(x, y - 1, w, lines * 9 + 1, c);
    }

    public static void header(GuiGraphics g, Font font, Component title, Component right, int x, int y, int w, int color) {
        int rw = width(font, right);
        right(g, font, right, x + w, y, color);
        title(g, font, title, x, y, w - rw - 6);
    }

    private record Tip(int x, int y, int w, int h, Component[] lines) {}

    private static final List<Tip> TIPS = new ArrayList<>();
    private static final int TAB_W = 92, TAB_H = 12;
    private static int tabX, tabY;

    public static void monitor(GuiGraphics g, int x, int y, int w, int h) {
        monitor(g, x, y, w, h, 24);
    }

    public static void monitor(GuiGraphics g, int x, int y, int w, int h, int head) {
        modern = NuclearClientConfig.modern();
        TIPS.clear();
        if (modern) {
            g.fill(x + 1, y, x + w - 1, y + h, M_EDGE);
            g.fill(x, y + 1, x + w, y + h - 1, M_EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, M_BG);
            g.fill(x + 1, y + 1, x + w - 1, y + head, M_HEAD);
            g.fill(x + 1, y + head, x + w - 1, y + head + 1, M_EDGE);
        } else {
            g.fill(x, y, x + w, y + h, BEZEL_LO);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BEZEL);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, BEZEL_HI);
            g.fill(x + 1, y + 1, x + 2, y + h - 1, BEZEL_HI);
            g.fill(x + 4, y + 4, x + w - 4, y + h - 4, BEZEL_LO);
            g.fill(x + 5, y + 5, x + w - 5, y + h - 5, SCREEN);
            for (int[] c : new int[][] {{x + 5, y + 5}, {x + w - 6, y + 5}, {x + 5, y + h - 6}, {x + w - 6, y + h - 6}}) {
                g.fill(c[0], c[1], c[0] + 1, c[1] + 1, BEZEL_LO);
            }
        }
        styleTab(g, x, y, w, h);
        recipeTab(g, x, y, w);
    }

    private static void styleTab(GuiGraphics g, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        tabX = x + w - TAB_W - 6;
        tabY = y - TAB_H >= 0 ? y - TAB_H : y + h;
        boolean top = tabY < y;
        int bg = modern ? M_HEAD : BEZEL, edge = modern ? M_EDGE : BEZEL_HI;
        g.fill(tabX, tabY, tabX + TAB_W, tabY + TAB_H, edge);
        g.fill(tabX + 1, top ? tabY + 1 : tabY, tabX + TAB_W - 1, top ? tabY + TAB_H : tabY + TAB_H - 1, bg);
        int half = TAB_W / 2;
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == modern;
            int sx = tabX + 2 + i * half, ex = tabX + (i + 1) * half - (i == 1 ? 2 : 0);
            if (on) g.fill(sx, tabY + 2, ex, tabY + TAB_H - 2, modern ? M_HI : DIM);
            if (on && modern) g.fill(sx, tabY + TAB_H - 3, ex, tabY + TAB_H - 1, M_AMBER);
            Component lbl = Component.translatable(i == 0 ? "gui.nuclearsq.term.style.crt" : "gui.nuclearsq.term.style.modern");
            int c = on ? (modern ? M_WHITE : GREEN) : (modern ? M_MUTED : MID);
            g.drawString(font, lbl, (sx + ex) / 2 - font.width(lbl) / 2, tabY + 2, c, false);
        }
        tip(tabX, tabY, TAB_W, TAB_H, Component.translatable("gui.nuclearsq.term.style.tip"),
                Component.translatable("gui.nuclearsq.term.style.tip2"));
    }

    private static final int REC_W = 24, REC_H = 34;
    private static ItemStack recipeNext = ItemStack.EMPTY, recipeShown = ItemStack.EMPTY, headIcon = ItemStack.EMPTY;
    private static int recX, recY;

    public static void recipes(ItemStack machine) {
        recipeNext = machine;
    }

    private static void recipeTab(GuiGraphics g, int x, int y, int w) {
        recipeShown = headIcon = recipeNext;
        recipeNext = ItemStack.EMPTY;
        if (recipeShown.isEmpty() || !com.sqc.nuclearsq.client.RecipeLink.available(recipeShown)) {
            recipeShown = ItemStack.EMPTY;
            return;
        }
        recX = x + w - 1;
        recY = y + 14;
        int bg = modern ? M_HEAD : BEZEL, edge = modern ? M_EDGE : BEZEL_HI, line = modern ? M_SOFT : MID;
        g.fill(recX, recY, recX + REC_W, recY + REC_H, edge);
        g.fill(recX, recY + 1, recX + REC_W - 1, recY + REC_H - 1, bg);
        g.renderItem(recipeShown, recX + 4, recY + 4);
        for (int i = 0; i < 3; i++) g.fill(recX + 6, recY + 23 + i * 3, recX + REC_W - 6, recY + 24 + i * 3, line);
        tip(recX, recY, REC_W, REC_H, Component.translatable("gui.nuclearsq.term.recipes"),
                Component.translatable("gui.nuclearsq.term.recipes.tip"));
    }

    private static boolean recipeClick(double mx, double my) {
        if (recipeShown.isEmpty() || mx < recX || mx >= recX + REC_W || my < recY || my >= recY + REC_H) return false;
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
        com.sqc.nuclearsq.client.RecipeLink.show(recipeShown);
        return true;
    }

    public static boolean styleClick(double mx, double my) {
        if (recipeClick(mx, my)) return true;
        if (mx < tabX || mx >= tabX + TAB_W || my < tabY || my >= tabY + TAB_H) return false;
        boolean want = mx >= tabX + TAB_W / 2;
        if (want != modern) {
            NuclearClientConfig.setModern(want);
            modern = want;
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
        }
        return true;
    }

    public static void tip(int x, int y, int w, int h, Component... lines) {
        if (lines.length > 0) TIPS.add(new Tip(x, y, w, h, lines));
    }

    public static boolean renderTip(GuiGraphics g, Font font, int mx, int my) {
        for (int i = TIPS.size() - 1; i >= 0; i--) {
            Tip t = TIPS.get(i);
            if (mx < t.x || mx >= t.x + t.w || my < t.y || my >= t.y + t.h) continue;
            List<FormattedCharSequence> out = new ArrayList<>();
            for (int k = 0; k < t.lines.length; k++) {
                Component c = t.lines[k].copy().withStyle(k == 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY);
                out.addAll(font.split(c, 230));
            }
            g.renderTooltip(font, out, mx, my);
            return true;
        }
        return false;
    }

    public static void scanlines(GuiGraphics g, int x, int y, int w, int h) {
        if (modern) return;
        for (int yy = y + 5; yy < y + h - 5; yy += 2) g.fill(x + 5, yy, x + w - 5, yy + 1, SCAN);
    }

    public static void frame(GuiGraphics g, Font font, int x, int y, int w, int h, Component title, int color) {
        if (modern) {
            boolean accent = color != DIM && color != FAINT && color != MID;
            int e = title == null && accent ? col(color) : M_EDGE;
            g.fill(x + 1, y, x + w - 1, y + 1, e);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, e);
            g.fill(x, y + 1, x + 1, y + h - 1, e);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, e);
            if (title != null) {
                if (accent) g.fill(x + 1, y + 1, x + 3, y + h - 1, col(color));
                int tw = width(font, title);
                g.fill(x + 5, y - 1, x + 9 + tw, y + 2, M_BG);
                text(g, font, title, x + 7, y - 4, accent ? color : MID);
            }
            return;
        }
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
        if (title != null) {
            Component tt = Component.literal("[ ").append(title).append(" ]");
            int tw = width(font, tt);
            g.fill(x + 5, y - 1, x + 9 + tw, y + 2, SCREEN);
            text(g, font, tt, x + 7, y - 4, color == FAINT ? MID : color);
        }
    }

    public static void rule(GuiGraphics g, int x, int y, int w, int color) {
        if (modern) {
            g.fill(x, y, x + w, y + 1, M_EDGE);
            return;
        }
        for (int xx = x; xx < x + w; xx += 3) g.fill(xx, y, Math.min(x + w, xx + 2), y + 1, color);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        float f = Mth.clamp(frac, 0f, 1f);
        if (modern) {
            g.fill(x, y, x + w, y + h, M_TRACK);
            int fw = Math.round(w * f);
            if (fw > 0) {
                g.fill(x, y, x + fw, y + h, col(color));
                g.fill(x, y, x + fw, y + 1, 0x40FFFFFF);
            }
            return;
        }
        g.fill(x, y, x + 1, y + h, DIM);
        g.fill(x + w - 1, y, x + w, y + h, DIM);
        int cells = Math.max(1, (w - 4) / 5), lit = Math.round(cells * f);
        for (int i = 0; i < cells; i++) {
            int cx = x + 3 + i * 5;
            g.fill(cx, y + 1, cx + 4, y + h - 1, i < lit ? color : FAINT);
        }
    }

    public static void vbar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        float f = Mth.clamp(frac, 0f, 1f);
        if (modern) {
            g.fill(x, y, x + w, y + h, M_EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, M_TRACK);
            int fh = Math.round((h - 2) * f);
            if (fh > 0) g.fill(x + 1, y + h - 1 - fh, x + w - 1, y + h - 1, col(color));
            return;
        }
        g.fill(x, y, x + w, y + 1, DIM);
        g.fill(x, y + h - 1, x + w, y + h, DIM);
        int cells = Math.max(1, (h - 4) / 4), lit = Math.round(cells * f);
        for (int i = 0; i < cells; i++) {
            int cy = y + h - 3 - i * 4;
            g.fill(x + 1, cy - 2, x + w - 1, cy + 1, i < lit ? color : FAINT);
        }
    }

    private static Component clip(Font font, Component c, int w) {
        if (width(font, c) <= w) return c;
        String l = c.getString();
        while (l.length() > 1 && width(font, Component.literal(l + "..")) > w) l = l.substring(0, l.length() - 1);
        return Component.literal(l + "..").withStyle(c.getStyle());
    }

    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, int color,
                              boolean enabled, boolean hover, boolean active) {
        label = clip(font, label, w - 4);
        if (modern) {
            int cc = col(color);
            boolean on = enabled && active;
            g.fill(x, y, x + w, y + h, enabled && (hover || active) ? cc : M_EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? cc : enabled && hover ? M_HI : M_CARD);
            if (enabled && !on && !hover) g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, (cc & 0x00FFFFFF) | 0x80000000);
            int tw = font.width(label);
            g.drawString(font, label, x + w / 2 - tw / 2, y + (h - 8) / 2, !enabled ? M_DIM : on ? M_INK : hover ? M_WHITE : M_TEXT, false);
            return;
        }
        int c = enabled ? color : DIM;
        boolean inv = enabled && (hover || active);
        if (inv) g.fill(x, y, x + w, y + h, active && !hover ? (c & 0x00FFFFFF) | 0xB0000000 : c);
        else frame(g, font, x, y, w, h, null, c);
        Component t = Component.literal("[ ").append(label).append(" ]");
        if (width(font, t) > w - 4) t = label;
        center(g, font, t, x + w / 2, y + (h - 8) / 2, inv ? SCREEN : c);
    }

    public static void segments(GuiGraphics g, Font font, int x, int y, int w, int h, Component[] labels, int pos,
                                int color, int hover) {
        int n = labels.length;
        if (modern) {
            g.fill(x, y, x + w, y + h, M_EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, M_TRACK);
            for (int i = 0; i < n; i++) {
                int sx = x + i * w / n, ex = x + (i + 1) * w / n;
                boolean on = i == pos, hv = i == hover;
                if (on || hv) g.fill(sx + 1, y + 1, ex - 1, y + h - 1, on ? M_HI : M_CARD);
                if (on) g.fill(sx + 1, y + h - 3, ex - 1, y + h - 1, col(color));
                if (i > 0) g.fill(sx, y + 4, sx + 1, y + h - 4, M_EDGE);
                Component lb = clip(font, labels[i], ex - sx - 4);
                int tw = font.width(lb);
                g.drawString(font, lb, (sx + ex) / 2 - tw / 2, y + (h - 8) / 2 - (on ? 1 : 0), on ? M_WHITE : hv ? M_TEXT : M_MUTED, false);
            }
            return;
        }
        frame(g, font, x, y, w, h, null, DIM);
        for (int i = 0; i < n; i++) {
            int sx = x + i * w / n, ex = x + (i + 1) * w / n;
            boolean on = i == pos, hv = i == hover;
            if (on) g.fill(sx + 1, y + 1, ex - 1, y + h - 1, color);
            else if (hv) g.fill(sx + 1, y + 1, ex - 1, y + h - 1, FAINT);
            if (i > 0) g.fill(sx, y + 3, sx + 1, y + h - 3, DIM);
            center(g, font, clip(font, labels[i], ex - sx - 4), (sx + ex) / 2, y + (h - 8) / 2, on ? SCREEN : hv ? GREEN : MID);
        }
    }

    public static void field(GuiGraphics g, Font font, int x, int y, int w, Component label, Component value, int color) {
        int vw = width(font, value);
        if (width(font, label) + vw + 6 > w) {
            String l = label.getString();
            while (l.length() > 1 && width(font, Component.literal(l + "..")) + vw + 6 > w) l = l.substring(0, l.length() - 1);
            label = Component.literal(l + "..");
        }
        text(g, font, label, x, y, MID);
        int lw = width(font, label);
        if (!modern) for (int xx = x + lw + 3; xx < x + w - vw - 3; xx += 3) g.fill(xx, y + 6, xx + 1, y + 7, DIM);
        text(g, font, value, x + w - vw, y, color);
    }

    public static void tile(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, Component value,
                            int color, float frac) {
        frame(g, font, x, y, w, h, null, DIM);
        if (modern) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, M_CARD);
        text(g, font, label, x + 4, y + 4, MID);
        text(g, font, value, x + 4, y + 15, color);
        if (frac >= 0) bar(g, x + 4, y + h - 8, w - 8, 4, frac, color);
    }

    public static int badgeWidth(Font font, Component label) {
        return modern ? font.width(label) + 16 : width(font, Component.literal("[ ").append(label).append(" ]"));
    }

    public static void headBadge(GuiGraphics g, Font font, Component title, Component label, int x, int y, int w, int color) {
        badge(g, font, label, x + w, y, color);
        title(g, font, title, x, y, w - badgeWidth(font, label) - 6);
    }

    public static void badge(GuiGraphics g, Font font, Component label, int xRight, int y, int color) {
        if (!modern) {
            right(g, font, Component.literal("[ ").append(label).append(" ]"), xRight, y, color);
            return;
        }
        int tw = font.width(label), pw = tw + 16, px = xRight - pw;
        g.fill(px, y - 3, px + pw, y + 10, col(color));
        g.fill(px + 1, y - 2, px + pw - 1, y + 9, M_BG);
        g.fill(px + 4, y + 2, px + 7, y + 5, col(color));
        g.drawString(font, label, px + 11, y, M_TEXT, false);
    }

    public static void prompt(GuiGraphics g, Font font, int x, int y, Component c, int color) {
        if (modern) {
            text(g, font, c, x, y, color);
            return;
        }
        Component p = Component.literal("> ").append(c);
        text(g, font, p, x, y, color);
        int cx = x + width(font, p) + 2;
        g.fill(cx, y, cx + 5, y + 8, color);
    }

    public static void slider(GuiGraphics g, Font font, int x, int y, int w, int h, int ticks, float frac, Component label) {
        float f = Mth.clamp(frac, 0f, 1f);
        int lit = (int) (f * ticks + 1e-4f);
        int hx = x + Math.round((w - 10) * f);
        int lw = width(font, label), lx = x + w / 2 - lw / 2;
        if (modern) {
            g.fill(x, y, x + w, y + h, M_EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, M_TRACK);
            g.fill(x + 1, y + 1, hx + 5, y + h - 1, 0xFF5A4520);
            for (int i = 0; i <= ticks; i++) {
                int tx = x + 5 + Math.round((w - 10) * i / (float) ticks);
                g.fill(tx, y + h - 4, tx + 1, y + h - 1, M_EDGE);
            }
            g.drawString(font, label, lx, y + (h - 8) / 2, M_TEXT, false);
            g.fill(hx, y - 2, hx + 10, y + h + 2, M_TEXT);
            g.fill(hx + 1, y - 1, hx + 9, y + h + 1, M_AMBER);
            return;
        }
        frame(g, font, x, y, w, h, null, DIM);
        for (int i = 0; i <= ticks; i++) {
            int tx = x + 5 + Math.round((w - 10) * i / (float) ticks);
            g.fill(tx, y + 2, tx + 1, y + h - 2, i <= lit ? MID : FAINT);
        }
        g.fill(hx, y - 2, hx + 10, y + h + 2, GREEN);
        g.fill(hx + 2, y, hx + 8, y + h, SCREEN);
        g.fill(lx - 3, y + 2, lx + lw + 3, y + h - 2, SCREEN);
        text(g, font, label, lx, y + 3, GREEN);
    }

    public static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int c = col(color);
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, c);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    public static void gauge(GuiGraphics g, Font font, int x, int y, int w, Component label, float value, float redFrom,
                             String num, String unit) {
        frame(g, font, x, y, w, 66, label, DIM);
        int cx = x + w / 2, cy = y + 34, r = 22;
        float v = Mth.clamp(value, 0f, 1f);
        for (int i = 0; i <= 20; i++) {
            float f = i / 20f;
            double a = Math.toRadians(135 + 270 * f);
            int len = i % 5 == 0 ? 5 : 2;
            line(g, cx + (int) Math.round(Math.cos(a) * r), cy + (int) Math.round(Math.sin(a) * r),
                    cx + (int) Math.round(Math.cos(a) * (r - len)), cy + (int) Math.round(Math.sin(a) * (r - len)),
                    f >= redFrom ? RED : f <= v ? GREEN : FAINT);
        }
        double a = Math.toRadians(135 + 270 * v);
        line(g, cx, cy, cx + (int) Math.round(Math.cos(a) * (r - 4)), cy + (int) Math.round(Math.sin(a) * (r - 4)),
                v >= redFrom ? RED : modern ? 0xFFE6E9ED : AMBER);
        fill(g, cx - 1, cy - 1, cx + 2, cy + 2, MID);
        center(g, font, Component.literal(num), cx, cy + 8, v >= redFrom ? RED : GREEN);
        center(g, font, Component.literal(unit), cx, cy + 18, DIM);
    }
}
