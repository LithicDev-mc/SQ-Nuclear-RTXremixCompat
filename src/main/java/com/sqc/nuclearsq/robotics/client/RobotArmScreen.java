package com.sqc.nuclearsq.robotics.client;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.robotics.RobotArmBlockEntity;
import com.sqc.nuclearsq.robotics.RobotArmMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class RobotArmScreen extends AbstractContainerScreen<RobotArmMenu> {
    private static final int W = RobotArmMenu.WIDTH, H = RobotArmMenu.HEIGHT;
    private static final int ROW_SOURCE = 66, ROW_TARGET = 77, TOGGLE_Y = 182;
    private static final int[][] BUTTONS = {
            {8, TOGGLE_Y, W - 16, 14},
            {W - 20, ROW_SOURCE - 2, 12, 11},
            {W - 20, ROW_TARGET - 2, 12, 11},
            {104, RobotArmMenu.FILTER_Y + 3, 64, 12}};
    private static final String[] TIPS = {"toggle", "clear_source", "clear_target", "clear_filter"};

    public RobotArmScreen(RobotArmMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.robot_arm." + key, args);
    }

    private void tip(int x, int y, int w, int h, Component... lines) {
        TermKit.tip(leftPos + x, topPos + y, w, h, lines);
    }

    private boolean over(int i, double mx, double my) {
        int x = leftPos + BUTTONS[i][0], y = topPos + BUTTONS[i][1];
        return mx >= x && mx < x + BUTTONS[i][2] && my >= y && my < y + BUTTONS[i][3];
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        for (int i = 0; i < BUTTONS.length; i++) {
            if (button == 0 && over(i, mx, my) && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static String pos(int x, int y, int z) {
        return x + " " + y + " " + z;
    }

    private Component link(boolean set, String where, int side) {
        return set ? Component.literal(where + " (" + Direction.from3DDataValue(side).getName() + ")")
                : Component.translatable("message.nuclearsq.robot_arm.none");
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, inner = W - 16;
        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8);
        TermKit.header(g, font, title, t("tier", menu.data(12)), x + 8, y + 7, inner, TermKit.MID);

        int problem = menu.data(3);
        boolean on = menu.data(1) != 0;
        Component state = !on ? Component.translatable("message.nuclearsq.robot_arm.problem.off")
                : problem == 0 ? Component.translatable("message.nuclearsq.robot_arm.ok", menu.data(2))
                : Component.translatable("message.nuclearsq.robot_arm.problem." + RobotArmBlockEntity.PROBLEMS[Math.min(problem, RobotArmBlockEntity.PROBLEMS.length - 1)]);
        int color = !on ? TermKit.DIM : problem == 0 ? TermKit.GREEN : problem == 6 || problem == 1 ? TermKit.MID : problem == 5 ? TermKit.RED : TermKit.AMBER;
        TermKit.wrap(g, font, state, x + 8, y + 19, inner, 2, color);

        int stored = menu.data(0), max = RobotArmBlockEntity.BUFFER;
        TermKit.field(g, font, x + 8, y + 41, inner, t("power"), t("energy", stored, max), TermKit.CYAN);
        TermKit.bar(g, x + 8, y + 52, inner, 4, Math.min(1f, stored / (float) max), TermKit.CYAN);
        tip(8, 40, inner, 17, t("power.title"), t("power.tip"));

        TermKit.rule(g, x + 8, y + 62, inner, TermKit.FAINT);
        boolean srcSet = menu.data(4) != 0, dstSet = menu.data(8) != 0;
        TermKit.field(g, font, x + 8, y + ROW_SOURCE, inner - 24, t("source_label"),
                link(srcSet, pos(menu.data(5), menu.data(6), menu.data(7)), menu.data(13)), srcSet ? TermKit.GREEN : TermKit.DIM);
        TermKit.field(g, font, x + 8, y + ROW_TARGET, inner - 24, t("target_label"),
                link(dstSet, pos(menu.data(9), menu.data(10), menu.data(11)) + (menu.data(8) > 1 ? " +" + (menu.data(8) - 1) : ""), menu.data(14)), dstSet ? TermKit.GREEN : TermKit.DIM);
        TermKit.wrap(g, font, t("hint"), x + 8, y + 91, inner, 2, TermKit.DIM);

        TermKit.rule(g, x + 8, y + 113, inner, TermKit.FAINT);
        TermKit.fit(g, font, t("filter"), x + 8, y + 117, inner, TermKit.MID);
        for (int i = 0; i < RobotArmBlockEntity.FILTER_SLOTS; i++) {
            Slot s = menu.slots.get(i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, s.hasItem() ? TermKit.GREEN : TermKit.MID);
        }
        tip(RobotArmMenu.FILTER_X - 1, RobotArmMenu.FILTER_Y - 1, RobotArmBlockEntity.FILTER_SLOTS * 18, 18, t("filter.title"), t("filter.tip"));

        int uy = RobotArmMenu.UPGRADE_Y;
        TermKit.fit(g, font, t("upgrades"), x + 8, y + uy - 11, inner, TermKit.MID);
        for (int i = 0; i < RobotArmBlockEntity.UPGRADE_SLOTS; i++) {
            Slot s = menu.slots.get(RobotArmBlockEntity.FILTER_SLOTS + i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, s.hasItem() ? TermKit.GREEN : TermKit.MID);
        }
        tip(RobotArmMenu.UPGRADE_X - 1, uy - 1, RobotArmBlockEntity.UPGRADE_SLOTS * 18, 18, t("upgrades.title"), t("upgrades.tip"));
        int ax = RobotArmMenu.UPGRADE_X + RobotArmBlockEntity.UPGRADE_SLOTS * 18 + 6;
        TermKit.fit(g, font, t("amount", menu.data(15)), x + ax, y + uy + 5, W - 8 - ax, TermKit.GREEN);

        for (int i = 0; i < BUTTONS.length; i++) {
            Component l = i == 0 ? t(on ? "turn_off" : "turn_on") : i < 3 ? Component.literal("×") : t("clear_short");
            TermKit.button(g, font, x + BUTTONS[i][0], y + BUTTONS[i][1], BUTTONS[i][2], BUTTONS[i][3], l,
                    i == 0 ? (on ? TermKit.AMBER : TermKit.GREEN) : TermKit.CYAN, true, over(i, mx, my), false);
            tip(BUTTONS[i][0], BUTTONS[i][1], BUTTONS[i][2], BUTTONS[i][3], i == 0 ? l : t(TIPS[i]), t(TIPS[i] + ".tip"));
        }

        TermKit.rule(g, x + 8, y + RobotArmMenu.INV_Y - 5, inner, TermKit.FAINT);
        for (int i = RobotArmBlockEntity.FILTER_SLOTS + RobotArmBlockEntity.UPGRADE_SLOTS; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.FAINT);
        }
        TermKit.scanlines(g, x - 4, y - 4, W + 8, H + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if ((hoveredSlot != null && hoveredSlot.hasItem()) || !TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
