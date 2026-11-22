package com.sqc.nuclearsq.storage.client;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.storage.ComponentAssembler;
import com.sqc.nuclearsq.storage.ComponentAssemblerMenu;
import com.sqc.nuclearsq.storage.PatternRecipes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ComponentAssemblerScreen extends AbstractContainerScreen<ComponentAssemblerMenu> {
    private static final int W = ComponentAssemblerMenu.WIDTH, H = ComponentAssemblerMenu.HEIGHT;
    private static final int BTN_X = 134, BTN_W = 54, BTN_H = 12;
    private static final int[][] BUTTONS = {
            {BTN_X, ComponentAssemblerMenu.BLANK_Y + 3, BTN_W},
            {BTN_X, ComponentAssemblerMenu.CARD_Y + 3, BTN_W},
            {W - 8 - 2 * 40 - 4, ComponentAssemblerMenu.JOB_Y + 18, 40},
            {W - 8 - 40, ComponentAssemblerMenu.JOB_Y + 18, 40}};

    public ComponentAssemblerScreen(ComponentAssemblerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
    }

    private static int tempoMax() {
        return com.sqc.nuclearsq.storage.StorageValues.assembler().tempoMax();
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.component_assembler." + key, args);
    }

    private void tip(int x, int y, int w, int h, Component... lines) {
        TermKit.tip(leftPos + x, topPos + y, w, h, lines);
    }

    private boolean over(int i, double mx, double my) {
        int x = leftPos + BUTTONS[i][0], y = topPos + BUTTONS[i][1];
        return mx >= x && mx < x + BUTTONS[i][2] && my >= y && my < y + BTN_H;
    }

    private boolean enabled(int i) {
        return switch (i) {
            case ComponentAssemblerMenu.BTN_WRITE -> menu.previewKind() != PatternRecipes.NONE
                    && menu.slots.get(ComponentAssemblerMenu.BLANK).hasItem() && !menu.slots.get(ComponentAssemblerMenu.WRITTEN).hasItem();
            case ComponentAssemblerMenu.BTN_CLEAR -> {
                boolean any = false;
                for (int k = 0; k < ComponentAssembler.GRID; k++) any |= menu.slots.get(k).hasItem();
                yield any;
            }
            default -> menu.jobs() > 0;
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        if (button == 0 && mx >= leftPos + W - 52 && mx < leftPos + W - 8 && my >= topPos + 17 && my < topPos + 28) {
            com.sqc.nuclearsq.machine.client.AccessScreen.request(menu.pos);
            return true;
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            if (button == 0 && over(i, mx, my) && enabled(i) && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static final String[] KIND = {"none", "crafting", "small_parts", "assembler"};
    private static final String[] STATUS = {"idle", "working", "waiting", "no_power", "off", "no_storage", "blocked", "chunk"};

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        TermKit.recipes(com.sqc.nuclearsq.client.RecipeLink.machine(com.sqc.nuclearsq.machine.big.BigMachine.COMPONENT_ASSEMBLER));
        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8, 34);
        TermKit.title(g, font, title, x + 8, y + 7, W - 16);
        TermKit.text(g, font, t("power", menu.powerUse()), x + 8, y + 18, TermKit.MID);
        TermKit.button(g, font, x + W - 52, y + 17, 44, 11, Component.translatable("gui.nuclearsq.access.button"), TermKit.CYAN, true,
                mx >= x + W - 52 && mx < x + W - 8 && my >= y + 17 && my < y + 28, false);
        tip(W - 52, 17, 44, 11, Component.translatable("gui.nuclearsq.access.button"), Component.translatable("gui.nuclearsq.access.button.tip"));

        for (int i = 0; i < ComponentAssembler.GRID; i++) {
            Slot s = menu.slots.get(i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.CYAN);
        }
        tip(ComponentAssemblerMenu.GRID_X - 1, ComponentAssemblerMenu.GRID_Y - 1, 54, 54, t("template"), t("template.tip"));
        int ax = x + ComponentAssemblerMenu.GRID_X + 56, ay = y + ComponentAssemblerMenu.PREVIEW_Y + 4;
        TermKit.text(g, font, Component.literal(">"), ax, ay, TermKit.MID);
        Slot pv = menu.slots.get(ComponentAssemblerMenu.PREVIEW);
        int kind = Math.max(0, Math.min(KIND.length - 1, menu.previewKind()));
        TermKit.frame(g, font, x + pv.x - 1, y + pv.y - 1, 18, 18, null, kind == 0 ? TermKit.FAINT : TermKit.GREEN);
        Component kc = t("kind." + KIND[kind]);
        int kw = Math.min(TermKit.width(font, kc), 44);
        TermKit.fit(g, font, kc, x + pv.x + 8 - kw / 2, y + pv.y + 20, 44, kind == 0 ? TermKit.DIM : TermKit.GREEN);
        tip(pv.x - 1, pv.y - 1, 18, 30, kc, t("kind.tip"));

        Slot us = menu.slots.get(ComponentAssemblerMenu.UPG);
        TermKit.frame(g, font, x + us.x - 1, y + us.y - 1, 18, 18, null, us.hasItem() ? TermKit.GREEN : TermKit.MID);
        TermKit.text(g, font, t("tempo", menu.tempo(), tempoMax()), x + us.x + 21, y + us.y + 5, menu.tempo() > 0 ? TermKit.GREEN : TermKit.DIM);
        tip(us.x - 1, us.y - 1, 54, 18, t("tempo.title"), t("tempo.tip", tempoMax()));

        for (int idx : new int[] {ComponentAssemblerMenu.BLANK, ComponentAssemblerMenu.WRITTEN}) {
            Slot s = menu.slots.get(idx);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.MID);
        }
        tip(ComponentAssemblerMenu.BLANK_X - 1, ComponentAssemblerMenu.BLANK_Y - 1, 18, 18, t("blank"), t("blank.tip"));
        String[] labels = {"write", "clear", "cancel", "cancel_all"};
        for (int i = 0; i < BUTTONS.length; i++) {
            Component l = t(labels[i]);
            TermKit.button(g, font, x + BUTTONS[i][0], y + BUTTONS[i][1], BUTTONS[i][2], BTN_H, l,
                    i >= 2 ? TermKit.AMBER : TermKit.CYAN, enabled(i), over(i, mx, my), false);
            tip(BUTTONS[i][0], BUTTONS[i][1], BUTTONS[i][2], BTN_H, l, t(labels[i] + ".tip"));
        }

        TermKit.rule(g, x + 8, y + ComponentAssemblerMenu.CARDS_Y - 16, W - 16, TermKit.FAINT);
        TermKit.text(g, font, t("cards"), x + 8, y + ComponentAssemblerMenu.CARDS_Y - 11, TermKit.DIM);
        for (int i = 0; i < ComponentAssembler.CARDS; i++) {
            Slot s = menu.slots.get(ComponentAssemblerMenu.CARDS + i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, s.hasItem() ? TermKit.GREEN : TermKit.MID);
        }
        Slot c0 = menu.slots.get(ComponentAssemblerMenu.CARDS);
        tip(c0.x - 1, c0.y - 1, ComponentAssembler.CARDS * 18, 18, t("cards"), t("cards.tip"));

        int jy = ComponentAssemblerMenu.JOB_Y;
        Slot js = menu.slots.get(ComponentAssemblerMenu.JOB);
        TermKit.frame(g, font, x + js.x - 1, y + js.y - 1, 18, 18, null, menu.jobs() > 0 ? TermKit.GREEN : TermKit.FAINT);
        int st = menu.formed() ? Math.max(0, Math.min(STATUS.length - 1, menu.status())) : 4;
        int sc = switch (st) {
            case ComponentAssembler.WORKING -> TermKit.GREEN;
            case ComponentAssembler.IDLE -> TermKit.MID;
            case ComponentAssembler.WAITING, ComponentAssembler.BLOCKED, ComponentAssembler.CHUNK -> TermKit.AMBER;
            default -> TermKit.RED;
        };
        Component stc = t("status." + STATUS[st]);
        Component jc = t("jobs", menu.jobs());
        TermKit.fit(g, font, stc, x + 32, y + jy, W - 46 - TermKit.width(font, jc), sc);
        tip(30, jy - 1, 100, 10, stc, t("status." + STATUS[st] + ".tip"));
        TermKit.right(g, font, jc, x + W - 8, y + jy, menu.jobs() > 0 ? TermKit.CYAN : TermKit.DIM);
        if (menu.jobs() > 0) {
            ItemStack target = js.getItem();
            Component line = t("job", menu.jobAmount(), target.getHoverName(), menu.step(), menu.steps(), menu.leftInStep());
            TermKit.text(g, font, line, x + 32, y + jy + 10, TermKit.MID);
        }
        TermKit.bar(g, x + 32, y + jy + 20, BUTTONS[2][0] - 36, 4, menu.progress() / 100f, sc);
        tip(30, jy + 18, BUTTONS[2][0] - 32, 8, t("progress", menu.progress()), t("energy", menu.energyPercent()));
        boolean stalled = menu.stalledMinutes() > 0;
        int cy = y + jy + 32;
        if (stalled) {
            TermKit.text(g, font, t("stalled", menu.stalledMinutes()), x + 8, cy, TermKit.RED);
            tip(8, jy + 31, W - 16, 10, t("stalled", menu.stalledMinutes()), t("stalled.tip"));
        } else {
            computeTiles(g, x + 8, cy - 2, mx, my);
        }

        TermKit.rule(g, x + 8, y + ComponentAssemblerMenu.INV_Y - 6, W - 16, TermKit.FAINT);
        for (int i = ComponentAssemblerMenu.INV; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.FAINT);
        }
        TermKit.scanlines(g, x - 4, y - 4, W + 8, H + 8);
    }

    private void computeTiles(GuiGraphics g, int x, int y, int mx, int my) {
        int w = (W - 16 - 8) / 3, h = 26;
        boolean inf = menu.sizeAllowed() >= Short.MAX_VALUE;
        int busy = menu.lanesBusy(), cores = Math.max(1, menu.lanesAllowed());
        Component[] labels = {t("tile.cores"), t("tile.size"), t("tile.peers")};
        Component[] values = {Component.literal(busy + " / " + menu.lanesAllowed()),
                Component.literal(inf ? menu.sizeUsed() + " / ∞" : menu.sizeUsed() + " / " + menu.sizeAllowed()),
                Component.literal(Integer.toString(menu.peers()))};
        float[] frac = {busy / (float) cores, inf || menu.sizeAllowed() <= 0 ? 0f : menu.sizeUsed() / (float) menu.sizeAllowed(), -1f};
        int[] color = {busy >= cores ? TermKit.AMBER : TermKit.CYAN, TermKit.CYAN, TermKit.GREEN};
        for (int i = 0; i < 3; i++) {
            int tx = x + i * (w + 4);
            TermKit.frame(g, font, tx, y, w, h, null, TermKit.DIM);
            TermKit.text(g, font, labels[i], tx + 4, y + 3, TermKit.MID);
            TermKit.text(g, font, values[i], tx + 4, y + 12, color[i]);
            if (frac[i] >= 0) TermKit.bar(g, tx + 4, y + h - 6, w - 8, 3, Math.min(1f, frac[i]), color[i]);
            tip(tx - leftPos, y - topPos, w, h, t("compute.title"), t("tile." + new String[] {"cores", "size", "peers"}[i] + ".tip"));
        }
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
