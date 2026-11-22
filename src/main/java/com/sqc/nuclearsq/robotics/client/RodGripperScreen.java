package com.sqc.nuclearsq.robotics.client;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.robotics.RodGripperBlockEntity;
import com.sqc.nuclearsq.robotics.RodGripperMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RodGripperScreen extends AbstractContainerScreen<RodGripperMenu> {
    private static final int W = RodGripperMenu.WIDTH, H = RodGripperMenu.HEIGHT, TOGGLE_Y = 168;

    public RodGripperScreen(RodGripperMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.rod_gripper." + key, args);
    }

    private boolean overToggle(double mx, double my) {
        return mx >= leftPos + 8 && mx < leftPos + W - 8 && my >= topPos + TOGGLE_Y && my < topPos + TOGGLE_Y + 14;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        if (button == 0 && overToggle(mx, my) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RodGripperMenu.BTN_TOGGLE);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, inner = W - 16;
        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8, 20);
        TermKit.header(g, font, title, Component.empty(), x + 8, y + 7, inner, TermKit.MID);

        boolean on = menu.data(1) != 0;
        int problem = menu.data(4);
        Component state = !on ? t("problem.off")
                : problem == 0 ? t(menu.data(3) != 0 ? "ok_holding" : "ok") : t("problem." + RodGripperBlockEntity.PROBLEMS[Math.min(problem, RodGripperBlockEntity.PROBLEMS.length - 1)]);
        int color = !on || problem == 5 ? TermKit.DIM : problem == 0 ? TermKit.GREEN : problem == 2 ? TermKit.RED : TermKit.AMBER;
        TermKit.wrap(g, font, state, x + 8, y + 19, inner, 2, color);

        int stored = menu.data(0), max = RodGripperBlockEntity.BUFFER;
        TermKit.field(g, font, x + 8, y + 41, inner, t("power"), t("energy", stored, max), TermKit.CYAN);
        TermKit.bar(g, x + 8, y + 52, inner, 4, Math.min(1f, stored / (float) max), TermKit.CYAN);
        TermKit.tip(x + 8, y + 40, inner, 17, t("power.title"), t("power.tip"));

        int depth = menu.data(2), max2 = RodGripperBlockEntity.MAX_DEPTH;
        TermKit.rule(g, x + 8, y + 62, inner, TermKit.FAINT);
        TermKit.field(g, font, x + 8, y + 66, inner, t("hook"), t("depth", depth, max2), TermKit.GREEN);
        TermKit.bar(g, x + 8, y + 77, inner, 4, depth / (float) max2, TermKit.GREEN);
        TermKit.tip(x + 8, y + 65, inner, 17, t("hook.title"), t("hook.tip"));
        boolean holding = menu.data(3) != 0, rod = menu.data(6) != 0;
        TermKit.field(g, font, x + 8, y + 87, inner, t("grip"), t(holding ? "grip_closed" : "grip_open"), holding ? TermKit.GREEN : TermKit.DIM);
        TermKit.field(g, font, x + 8, y + 98, inner, t("rod"), t(rod ? "yes" : "no"), rod ? TermKit.GREEN : TermKit.DIM);

        TermKit.rule(g, x + 8, y + 110, inner, TermKit.FAINT);
        TermKit.fit(g, font, t("inputs"), x + 8, y + 114, inner, TermKit.MID);
        for (RodGripperBlockEntity.Input in : RodGripperBlockEntity.Input.values()) {
            String n = in.name().toLowerCase(java.util.Locale.ROOT);
            boolean active = (menu.data(5) & 1 << in.ordinal()) != 0;
            int ry = y + 125 + in.ordinal() * 10;
            TermKit.field(g, font, x + 8, ry, inner, t("side_" + n), t("fn_" + n), active ? TermKit.GREEN : TermKit.DIM);
            TermKit.tip(x + 8, ry - 1, inner, 10, t("fn_" + n), t("fn_" + n + ".tip"));
        }

        TermKit.button(g, font, x + 8, y + TOGGLE_Y, inner, 14, t(on ? "turn_off" : "turn_on"), on ? TermKit.AMBER : TermKit.GREEN, true, overToggle(mx, my), false);
        TermKit.tip(x + 8, y + TOGGLE_Y, inner, 14, t("toggle"), t("toggle.tip"));
        TermKit.scanlines(g, x - 4, y - 4, W + 8, H + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (!TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
