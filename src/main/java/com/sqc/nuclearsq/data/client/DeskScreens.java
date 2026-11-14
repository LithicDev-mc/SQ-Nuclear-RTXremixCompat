package com.sqc.nuclearsq.data.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.data.ControlDesk;
import com.sqc.nuclearsq.data.ControlWallBlockEntity;
import com.sqc.nuclearsq.data.DataEntry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class DeskScreens {
    private static final int GLASS = 0xFF040A06, GREEN = 0xFF41FF7A, MID = 0xFF22B04E, DIM = 0xFF136B30, SEL = 0xFF0B3319;
    private static final int AMBER = 0xFFFFB23A, RED = 0xFFFF5A4E, FACE = 0xFF07100A, SCALE = 0xFFB9C4C9, OFF = 0xFF1A1F22;
    private static final int PAGE_TICKS = 100;
    private static final Map<Long, float[]> SHOWN = new HashMap<>();

    private DeskScreens() {}

    public static void render(BigMachineBlockEntity be, int rot, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        ControlDesk desk = be.desk();
        if (desk == null || be.getLevel() == null || !be.isRunning()) return;
        long time = be.getLevel().getGameTime();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-90f * rot));
        pose.translate(-0.5, 0, -0.5);
        List<ControlWallBlockEntity.Tile> tiles = desk.tiles();
        ControlWallBlockEntity.Tile sel = desk.selectedTile();

        PanelDraw d = open(pose, buffers, ControlDesk.SCREEN_MAIN);
        main(d, w(ControlDesk.SCREEN_MAIN), h(ControlDesk.SCREEN_MAIN), sel, tiles.size(), time);
        d.end();
        d = open(pose, buffers, ControlDesk.SCREEN_LEFT);
        overview(d, w(ControlDesk.SCREEN_LEFT), h(ControlDesk.SCREEN_LEFT), tiles, desk.selectedIndex(), desk.rows(), time);
        d.end();
        d = open(pose, buffers, ControlDesk.SCREEN_RIGHT);
        alarms(d, w(ControlDesk.SCREEN_RIGHT), h(ControlDesk.SCREEN_RIGHT), desk.alarms(), tiles.size(), desk.rows(), time);
        d.end();

        List<ControlWallBlockEntity.Line> scaled = sel == null ? List.of()
                : sel.lines().stream().filter(ControlWallBlockEntity.Line::scaled).toList();
        float[] shown = SHOWN.computeIfAbsent(be.getBlockPos().asLong(), k -> new float[ControlDesk.GAUGES.length]);
        for (int i = 0; i < ControlDesk.GAUGES.length; i++) {
            ControlWallBlockEntity.Line ln = i < scaled.size() ? scaled.get(i) : null;
            float target = ln == null ? 0 : ln.frac();
            shown[i] += (target - shown[i]) * Mth.clamp(0.08f * (1 + partialTick), 0, 1);
            d = open(pose, buffers, ControlDesk.GAUGES[i]);
            gauge(d, w(ControlDesk.GAUGES[i]), h(ControlDesk.GAUGES[i]), ln, shown[i]);
            d.end();
        }
        d = open(pose, buffers, ControlDesk.SEGMENT);
        segment(d, w(ControlDesk.SEGMENT), h(ControlDesk.SEGMENT), sel);
        d.end();
        boolean blink = (time / 10) % 2 == 0;
        boolean ok = tiles.stream().anyMatch(t -> t.state() == DataSource.OK);
        boolean warn = tiles.stream().anyMatch(t -> t.state() == DataSource.WARN);
        boolean alarm = tiles.stream().anyMatch(t -> t.state() == DataSource.ALARM);
        int[] lamp = {GREEN, ok ? GREEN : OFF, warn ? AMBER : OFF, alarm && blink ? RED : OFF};
        for (int i = 0; i < ControlDesk.LAMPS.length; i++) {
            d = open(pose, buffers, ControlDesk.LAMPS[i]);
            d.fill(0, 0, w(ControlDesk.LAMPS[i]), h(ControlDesk.LAMPS[i]), lamp[i]);
            d.end();
        }
        pose.popPose();
    }

    private static PanelDraw open(PoseStack pose, MultiBufferSource buffers, double[] m) {
        return PanelDraw.at(pose, buffers, m[3], m[4], m[2] - 0.004, 180);
    }

    private static float w(double[] m) {
        return (float) ((m[3] - m[0]) * 64);
    }

    private static float h(double[] m) {
        return (float) ((m[4] - m[1]) * 64);
    }

    private static int lineColor(float frac) {
        return frac >= 0.9f ? RED : frac >= 0.75f ? AMBER : MID;
    }

    private static void main(PanelDraw d, float w, float h, ControlWallBlockEntity.Tile t, int count, long time) {
        d.fill(0, 0, w, h, GLASS);
        d.layer();
        float x = 1.5f, r = w - 1.5f;
        if (t == null) {
            d.textFit(Component.translatable("data.nuclearsq.screen.no_signal"), x, 2, 0.2f, r - x, AMBER);
        } else {
            int sc = DataEntry.color(t.state());
            d.fill(x, 1.6f, x + 1.4f, 3f, sc);
            d.textFit(t.name(), x + 2.2f, 1.2f, 0.2f, r - x - 9, GREEN);
            d.right(Component.literal(count > 1 ? "<" + count + ">" : ""), r, 1.4f, 0.14f, DIM);
            d.textFit(t.stateText(), x + 2.2f, 3.4f, 0.15f, r - x - 2.2f, sc == 0xFF8792A0 ? MID : sc);
            d.fill(x, 5.4f, r, 5.6f, DIM);
            float y = 6.4f;
            for (ControlWallBlockEntity.Line ln : t.lines()) {
                d.textFit(ln.label(), x, y, 0.15f, (r - x) * 0.55f, MID);
                d.right(Component.literal(ln.text()), r, y, 0.15f, GREEN);
                if (ln.scaled()) {
                    d.fill(x, y + 1.5f, r, y + 2f, SEL);
                    d.layer();
                    d.fill(x, y + 1.5f, x + (r - x) * ln.frac(), y + 2f, lineColor(ln.frac()));
                }
                y += 3.1f;
            }
        }
        d.fill(x, h - 3.6f, r, h - 3.4f, DIM);
        d.text(Component.literal("> "), x, h - 2.8f, 0.17f, GREEN);
        if ((time / 10) % 2 == 0) d.fill(x + 2.2f, h - 2.7f, x + 3.2f, h - 1.3f, GREEN);
        d.right(Component.translatable("data.nuclearsq.screen.desk_console"), r, h - 2.6f, 0.13f, DIM);
    }

    private static void overview(PanelDraw d, float w, float h, List<ControlWallBlockEntity.Tile> tiles, int selected, int rows, long time) {
        d.fill(0, 0, w, h, GLASS);
        d.layer();
        float x = 1.2f, r = w - 1.2f;
        int pages = Math.max(1, (tiles.size() + rows - 1) / rows), page = (int) (time / PAGE_TICKS % pages);
        d.textFit(Component.translatable("data.nuclearsq.screen.desk_plants", tiles.size()), x, 1, 0.15f, r - x - 6, MID);
        if (pages > 1) d.right(Component.literal((page + 1) + "/" + pages), r, 1, 0.13f, DIM);
        d.fill(x, 2.8f, r, 3f, DIM);
        if (tiles.isEmpty()) {
            d.textFit(Component.translatable("data.nuclearsq.screen.no_signal"), x, 4.5f, 0.17f, r - x, AMBER);
            return;
        }
        float top = 3.6f, rh = (h - top - 0.6f) / rows, s = Math.min(0.2f, rh * 0.62f / 8);
        for (int k = 0; k < rows; k++) {
            int i = page * rows + k;
            if (i >= tiles.size()) break;
            ControlWallBlockEntity.Tile t = tiles.get(i);
            float y = top + k * rh;
            if (i == selected) d.fill(x - 0.4f, y, r + 0.4f, y + rh - 0.2f, SEL);
            int sc = DataEntry.color(t.state());
            float ty = y + (rh - 8 * s) / 2;
            d.layer();
            if (t.state() != DataSource.ALARM || (time / 10) % 2 == 0) d.fill(x, ty + 0.2f, x + 8 * s * 0.8f, ty + 8 * s * 0.8f + 0.2f, sc);
            float nx = x + 8 * s + 0.6f, split = nx + (r - nx) * 0.58f;
            d.textFit(t.name(), nx, ty, s, split - nx - 0.5f, i == selected ? GREEN : MID);
            float sw = d.width(t.stateText()) * s * 0.85f;
            d.textFit(t.stateText(), Math.max(split, r - sw), ty + 8 * s * 0.1f, s * 0.85f, r - split, sc == 0xFF8792A0 ? DIM : sc);
        }
    }

    private static void alarms(PanelDraw d, float w, float h, List<ControlWallBlockEntity.Tile> alarms, int count, int rows, long time) {
        d.fill(0, 0, w, h, GLASS);
        d.layer();
        float x = 1.2f, r = w - 1.2f;
        boolean red = alarms.stream().anyMatch(t -> t.state() == DataSource.ALARM);
        d.textFit(Component.translatable("data.nuclearsq.screen.desk_alarms", alarms.size()), x, 1, 0.15f, r - x, red ? RED : alarms.isEmpty() ? MID : AMBER);
        d.fill(x, 2.8f, r, 3f, red && (time / 10) % 2 == 0 ? RED : DIM);
        if (alarms.isEmpty()) {
            d.center(Component.translatable("data.nuclearsq.screen.desk_no_alarm"), w / 2, h / 2 - 2.5f, 0.2f, r - x, GREEN);
            d.center(Component.translatable("data.nuclearsq.screen.desk_all_ok", count), w / 2, h / 2 + 0.5f, 0.14f, r - x, DIM);
            return;
        }
        int pages = Math.max(1, (alarms.size() + rows - 1) / rows), page = (int) (time / PAGE_TICKS % pages);
        float top = 3.6f, rh = (h - top - 0.6f) / rows, s = Math.min(0.2f, rh * 0.42f / 8);
        for (int k = 0; k < rows; k++) {
            int i = page * rows + k;
            if (i >= alarms.size()) break;
            ControlWallBlockEntity.Tile t = alarms.get(i);
            float y = top + k * rh;
            int sc = DataEntry.color(t.state());
            if (t.state() == DataSource.ALARM && (time / 10) % 2 == 0) d.fill(x - 0.4f, y, x, y + rh - 0.3f, RED);
            d.textFit(t.name(), x + 0.6f, y + 0.2f, s, r - x - 0.6f, sc);
            d.textFit(t.stateText(), x + 0.6f, y + 0.4f + 8 * s, s * 0.9f, r - x - 0.6f, sc == RED ? 0xFFC0453B : 0xFFC08A2E);
        }
    }

    private static void gauge(PanelDraw d, float w, float h, ControlWallBlockEntity.Line ln, float frac) {
        d.fill(0, 0, w, h, FACE);
        d.layer();
        float cx = w / 2, cy = h * 0.58f, r = Math.min(w, h) * 0.45f;
        for (int i = 0; i <= 10; i++) d.tick(cx, cy, -120 + i * 24, i % 5 == 0 ? r - 2.2f : r - 1.3f, r, 0.25f, i >= 8 ? RED : SCALE);
        d.layer();
        d.needle(cx, cy, -120 + Mth.clamp(frac, 0, 1) * 240, r - 0.8f, 0.3f, 0xFFFF8040);
        d.layer();
        d.fill(cx - 0.8f, cy - 0.8f, cx + 0.8f, cy + 0.8f, SCALE);
        if (ln == null) return;
        d.center(Component.literal(ln.text()), cx, h - 3.6f, 0.12f, w - 1, GREEN);
        d.center(ln.label(), cx, h - 1.9f, 0.1f, w - 1, 0xFF5F6B70);
    }

    private static void segment(PanelDraw d, float w, float h, ControlWallBlockEntity.Tile t) {
        d.fill(0, 0, w, h, 0xFF020604);
        d.layer();
        if (t == null || t.lines().isEmpty()) {
            d.center(Component.literal("----"), w / 2, h / 2 - 2.5f, 0.6f, w - 2, 0xFF5A3A10);
            return;
        }
        ControlWallBlockEntity.Line ln = t.lines().get(0);
        float s = Math.min(0.75f, (h - 2) / 8);
        d.center(Component.literal(ln.text()), w / 2, (h - 8 * s) / 2, s, w - 2, AMBER);
    }
}
