package com.sqc.nuclearsq.robotics.drone.client;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.robotics.drone.DroneRegistry;
import com.sqc.nuclearsq.robotics.drone.SurveyMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public class SurveyMapScreen extends Screen {
    private static final int W = 200, H = 240, MAP = 176, BTN_W = 120, BTN_H = 14;
    private static final int[] STOPS = {0xFF1E8A3C, 0xFFE8D03A, 0xFFF08A2A, 0xFFE8332E, 0xFFB04CE8};
    private final InteractionHand hand;

    private SurveyMapScreen(InteractionHand hand) {
        super(Component.translatable("gui.nuclearsq.survey_tablet.title"));
        this.hand = hand;
    }

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new SurveyMapScreen(hand));
    }

    static int color(int b) {
        float t = Math.max(0, Math.min(1, (b - 41) / 184f)) * (STOPS.length - 1);
        int i = Math.min(STOPS.length - 2, (int) t);
        float f = t - i;
        int a = STOPS[i], c = STOPS[i + 1], r = 0xFF000000;
        for (int s = 0; s <= 16; s += 8) r |= Math.round(((a >> s) & 255) * (1 - f) + ((c >> s) & 255) * f) << s;
        return r;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int x = (width - W) / 2, y = (height - H) / 2;
        TermKit.monitor(g, x, y, W, H);
        Player p = Minecraft.getInstance().player;
        SurveyMap m = p == null ? null : p.getItemInHand(hand).get(DroneRegistry.MAP.get());
        int top = 0;
        if (m != null) for (byte b : m.cells()) top = Math.max(top, b & 255);
        TermKit.header(g, font, title, m == null ? Component.empty() : Component.translatable("gui.nuclearsq.survey_tablet.max",
                RadiationManager.fmtRate(SurveyMap.decode(top))), x + 10, y + 10, W - 20, TermKit.MID);
        TermKit.rule(g, x + 10, y + 24, W - 20, TermKit.DIM);
        int ax = x + (W - MAP) / 2, ay = y + 28;
        if (m == null || m.w() == 0) {
            TermKit.wrap(g, font, Component.translatable("gui.nuclearsq.survey_tablet.empty"), ax, ay + 60, MAP, 6, TermKit.DIM);
        } else {
            int c = Math.max(1, Math.min(MAP / m.w(), MAP / m.h()));
            int ox = ax + (MAP - c * m.w()) / 2, oy = ay + (MAP - c * m.h()) / 2;
            TermKit.fill(g, ox - 1, oy - 1, ox + c * m.w() + 1, oy + c * m.h() + 1, TermKit.FAINT);
            for (int j = 0; j < m.h(); j++) for (int i = 0; i < m.w(); i++) {
                int b = m.at(i, j);
                g.fill(ox + i * c, oy + j * c, ox + (i + 1) * c, oy + (j + 1) * c, b == 0 ? 0xFF0A0C0E : color(b));
            }
            int pi = (int) Math.floor((p.getX() - m.x0()) / SurveyMap.CELL), pj = (int) Math.floor((p.getZ() - m.z0()) / SurveyMap.CELL);
            if (pi >= 0 && pj >= 0 && pi < m.w() && pj < m.h()) {
                int px = ox + pi * c + c / 2, py = oy + pj * c + c / 2;
                g.fill(px - 2, py, px + 3, py + 1, 0xFFFFFFFF);
                g.fill(px, py - 2, px + 1, py + 3, 0xFFFFFFFF);
            }
            TermKit.text(g, font, Component.literal("N"), ox + c * m.w() / 2 - 2, oy - 10, TermKit.MID);
            int hi = (mx - ox) / c, hj = (my - oy) / c;
            if (mx >= ox && my >= oy && hi < m.w() && hj < m.h()) {
                int b = m.at(hi, hj);
                TermKit.tip(ox + hi * c, oy + hj * c, c, c, Component.literal("X " + (m.x0() + hi * SurveyMap.CELL) + "  Z " + (m.z0() + hj * SurveyMap.CELL)),
                        b == 0 ? Component.translatable("gui.nuclearsq.survey_tablet.unmeasured") : Component.literal(RadiationManager.fmtRate(SurveyMap.decode(b))));
            }
        }
        int ly = y + H - 44;
        for (int k = 0; k < MAP; k++) g.fill(ax + k, ly, ax + k + 1, ly + 5, color(41 + k * 184 / MAP));
        TermKit.text(g, font, Component.literal(RadiationManager.fmtRate(SurveyMap.decode(41))), ax, ly + 8, TermKit.DIM);
        TermKit.right(g, font, Component.literal(RadiationManager.fmtRate(SurveyMap.decode(225))), ax + MAP, ly + 8, TermKit.DIM);
        int bx = x + (W - BTN_W) / 2, by = y + H - 22;
        TermKit.button(g, font, bx, by, BTN_W, BTN_H, Component.translatable("gui.nuclearsq.survey_tablet.fly"), TermKit.GREEN, true,
                mx >= bx && my >= by && mx < bx + BTN_W && my < by + BTN_H, false);
        TermKit.renderTip(g, font, mx, my);
        TermKit.scanlines(g, x, y, W, H);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int bx = (width - W) / 2 + (W - BTN_W) / 2, by = (height - H) / 2 + H - 22;
        if (button == 0 && mx >= bx && my >= by && mx < bx + BTN_W && my < by + BTN_H) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.sqc.nuclearsq.robotics.drone.DronePayloads.Fly(hand == InteractionHand.MAIN_HAND));
            onClose();
            return true;
        }
        return button == 0 && TermKit.styleClick(mx, my) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
