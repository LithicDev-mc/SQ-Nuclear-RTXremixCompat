package com.sqc.nuclearsq.door.client;

import com.sqc.nuclearsq.door.AirlockBlockEntity;
import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.network.DeviceControlPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class AirlockScreen extends AbstractContainerScreen<DeviceMenu> {
    private static final int W = 220, H = 120, BTN_X = 10, BTN_Y = 82, BTN_W = 62, BTN_H = 14, BTN_GAP = 7;
    private static final String[] BUTTONS = {"door1", "door2", "close"};

    public AirlockScreen(DeviceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelY = -100;
        this.inventoryLabelY = -100;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.airlock." + key, args);
    }

    private int button(double mx, double my) {
        for (int i = 0; i < 3; i++) {
            int bx = leftPos + BTN_X + i * (BTN_W + BTN_GAP);
            if (mx >= bx && mx < bx + BTN_W && my >= topPos + BTN_Y && my < topPos + BTN_Y + BTN_H) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        int b = button == 0 ? button(mx, my) : -1;
        if (b >= 0) {
            PacketDistributor.sendToServer(new DeviceControlPayload(DeviceControlPayload.AIRLOCK_DOOR, b == 2 ? 0 : b + 1));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void doorLine(GuiGraphics g, int y, int n, boolean linked, boolean open) {
        TermKit.text(g, font, t(!linked ? "door.none" : open ? "door.open" : "door.closed", n), leftPos + 10, y,
                !linked ? TermKit.RED : open ? TermKit.CYAN : TermKit.GREEN);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, flags = menu.u(0), purge = menu.u(1), want = menu.u(2);
        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8);
        TermKit.text(g, font, title, x + 8, y + 8, TermKit.GREEN);
        TermKit.rule(g, x + 8, y + 20, W - 16, TermKit.DIM);
        doorLine(g, y + 28, 1, (flags & AirlockBlockEntity.FLAG_LINK1) != 0, (flags & AirlockBlockEntity.FLAG_OPEN1) != 0);
        doorLine(g, y + 40, 2, (flags & AirlockBlockEntity.FLAG_LINK2) != 0, (flags & AirlockBlockEntity.FLAG_OPEN2) != 0);
        TermKit.text(g, font, want == 0 ? t("ready") : purge > 0 ? t("purging", want) : t("cycling", want), x + 10, y + 56, want == 0 ? TermKit.MID : TermKit.AMBER);
        TermKit.bar(g, x + 10, y + 68, W - 20, 6, want == 0 ? 0 : 1 - purge / (float) AirlockBlockEntity.PURGE, TermKit.AMBER);
        int hover = button(mx, my);
        for (int i = 0; i < 3; i++) {
            TermKit.button(g, font, x + BTN_X + i * (BTN_W + BTN_GAP), y + BTN_Y, BTN_W, BTN_H, t(BUTTONS[i]), i == 2 ? TermKit.AMBER : TermKit.GREEN,
                    true, hover == i, want == i + 1);
        }
        TermKit.prompt(g, font, x + 8, y + 104, t("hint"), TermKit.DIM);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, H + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (!TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
