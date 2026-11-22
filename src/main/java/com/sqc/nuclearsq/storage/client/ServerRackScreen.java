package com.sqc.nuclearsq.storage.client;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.storage.ServerRack;
import com.sqc.nuclearsq.storage.ServerRackMenu;
import com.sqc.nuclearsq.storage.StorageFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class ServerRackScreen extends AbstractContainerScreen<ServerRackMenu> {
    private static final int BTN = 12;

    public ServerRackScreen(ServerRackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = ServerRackMenu.WIDTH;
        imageHeight = menu.height();
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.storage.rack." + key, args);
    }

    private int btnX(int i) {
        return imageWidth - 8 - (2 - i) * (BTN + 2);
    }

    private int rowY() {
        return spaceY() + 18;
    }

    private int spaceY() {
        return 20 + menu.rows * 18 + 6;
    }

    private void tip(int x, int y, int w, int h, Component... lines) {
        TermKit.tip(leftPos + x, topPos + y, w, h, lines);
    }

    private boolean over(int i, double mx, double my) {
        int x = leftPos + btnX(i), y = topPos + rowY() - 2;
        return mx >= x && mx < x + BTN && my >= y && my < y + BTN;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        if (button == 0 && mx >= leftPos + 8 && mx < leftPos + 52 && my >= topPos + rowY() + 12 && my < topPos + rowY() + 23) {
            com.sqc.nuclearsq.machine.client.AccessScreen.request(menu.pos);
            return true;
        }
        for (int i = 0; i < 2 && !menu.cards; i++) {
            if (button == 0 && over(i, mx, my) && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8, 20);
        TermKit.header(g, font, title, t(menu.cards ? "cards" : "drives", menu.driveCount(), menu.driveSlots), x + 8, y + 7, w - 16, TermKit.MID);
        for (int i = 0; i < menu.driveSlots; i++) {
            Slot s = menu.slots.get(i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.MID);
        }
        int ry = y + (menu.cards ? spaceY() : rowY());
        if (!menu.cards) renderSpace(g, x, y, w);
        Component state = menu.online() ? t("online", menu.powerUse()) : t("offline");
        TermKit.text(g, font, state, x + 8, ry, menu.online() ? TermKit.GREEN : TermKit.RED);
        if (menu.cards) {
            tip(8, spaceY() - 1, 110, 10, state, t(menu.online() ? "cards.online.tip" : "cards.offline.tip", menu.powerUse()));
            TermKit.rule(g, x + 8, y + menu.invY() - 6, w - 16, TermKit.FAINT);
            for (int i = menu.driveSlots; i < menu.slots.size(); i++) {
                Slot s = menu.slots.get(i);
                TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.FAINT);
            }
            rightsButton(g, x, y, mx, my);
            TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
            return;
        }
        ServerRack.Compute cpu = menu.compute();
        if (cpu.modules() > 0) {
            tip(8, rowY() - 1, 110, 10, state, t(menu.online() ? "online.tip" : "offline.tip", menu.powerUse()), t("compute", cpu.cpu(), com.sqc.nuclearsq.storage.StorageValues.gb(cpu.ram())));
        } else {
            tip(8, rowY() - 1, 110, 10, state, t(menu.online() ? "online.tip" : "offline.tip", menu.powerUse()));
        }
        Component prio = t("priority", menu.priority());
        TermKit.right(g, font, prio, x + btnX(0) - 4, ry, TermKit.CYAN);
        tip(btnX(0) - 4 - font.width(prio), rowY() - 1, font.width(prio), 10, prio, t("priority.tip"));
        for (int i = 0; i < 2; i++) {
            boolean enabled = i == 0 ? menu.priority() > 0 : menu.priority() < ServerRack.MAX_PRIORITY;
            TermKit.button(g, font, x + btnX(i), ry - 2, BTN, BTN, Component.literal(i == 0 ? "-" : "+"), TermKit.CYAN,
                    enabled, over(i, mx, my), false);
        }
        TermKit.rule(g, x + 8, y + menu.invY() - 6, w - 16, TermKit.FAINT);
        for (int i = menu.driveSlots; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.FAINT);
        }
        rightsButton(g, x, y, mx, my);
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
    }

    private void rightsButton(GuiGraphics g, int x, int y, int mx, int my) {
        boolean hover = mx >= x + 8 && mx < x + 52 && my >= y + rowY() + 12 && my < y + rowY() + 23;
        TermKit.button(g, font, x + 8, y + rowY() + 12, 44, 11, Component.translatable("gui.nuclearsq.access.button"), TermKit.CYAN, true, hover, false);
        tip(8, rowY() + 12, 44, 11, Component.translatable("gui.nuclearsq.access.button"), Component.translatable("gui.nuclearsq.access.button.tip"));
    }

    private void renderSpace(GuiGraphics g, int x, int y, int w) {
        ServerRack.Space sp = menu.space();
        int sy = spaceY();
        ServerRack.Compute cpu = menu.compute();
        if (sp.drives() == 0 && cpu.modules() > 0) {
            Component line = t("compute", cpu.cpu(), com.sqc.nuclearsq.storage.StorageValues.gb(cpu.ram()));
            TermKit.text(g, font, line, x + 8, y + sy, TermKit.CYAN);
            tip(8, sy - 1, w - 16, 16, line, t("compute.tip", cpu.modules()));
            return;
        }
        if (sp.drives() == 0) {
            TermKit.text(g, font, t("space.none"), x + 8, y + sy, TermKit.MID);
            tip(8, sy - 1, w - 16, 16, t("space.none"), t("space.none.tip"));
            return;
        }
        float f = sp.fraction();
        boolean full = f >= 0.98f || sp.types() >= sp.typeCapacity();
        int color = full ? TermKit.RED : f >= 0.8f ? TermKit.AMBER : TermKit.GREEN;
        Component used = t("space", StorageFormat.count(sp.used()), StorageFormat.count(sp.capacity()));
        TermKit.text(g, font, used, x + 8, y + sy, color);
        TermKit.right(g, font, t("space.free", StorageFormat.count(sp.free())), x + w - 8, y + sy, color);
        TermKit.bar(g, x + 8, y + sy + 10, w - 16, 3, f, color);
        tip(8, sy - 1, w - 16, 16, used,
                t("space.tip", sp.used(), sp.capacity(), sp.free()),
                t("space.types", sp.types(), sp.typeCapacity()));
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
