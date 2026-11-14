package com.sqc.nuclearsq.storage.client;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.storage.CraftingPayloads;
import com.sqc.nuclearsq.storage.CraftingTerminalMenu;
import com.sqc.nuclearsq.storage.StorageFormat;
import com.sqc.nuclearsq.storage.WifiTerminalItem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class CraftingTerminalScreen extends StorageTerminalScreen<CraftingTerminalMenu> {
    private static final int PX = 200, PY = 26, PW = 108, PH = 104;
    private static final int BTN_X = 206, BTN_Y = 96, BTN_W = 54, BTN_H = 12;

    public CraftingTerminalScreen(CraftingTerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    private static net.minecraft.network.chat.MutableComponent c(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.storage.crafting." + key, args);
    }

    private boolean overClear(double mx, double my) {
        return mx >= leftPos + BTN_X && mx < leftPos + BTN_X + BTN_W && my >= topPos + BTN_Y && my < topPos + BTN_Y + BTN_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overClear(mx, my)) {
            PacketDistributor.sendToServer(new CraftingPayloads.Clear(menu.containerId));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderExtras(GuiGraphics g, int x, int y, int mx, int my) {
        TermKit.frame(g, font, x + PX, y + PY, PW, PH, c("grid"), TermKit.FAINT);
        TermKit.text(g, font, Component.literal(">>"), x + 262, y + 58, TermKit.MID);
        boolean hover = overClear(mx, my);
        TermKit.button(g, font, x + BTN_X, y + BTN_Y, BTN_W, BTN_H, c("clear"), TermKit.CYAN, true, hover, false);
        tip(BTN_X, BTN_Y, BTN_W, BTN_H, c("clear"), c("clear.tip"));
        tip(CraftingTerminalMenu.RESULT_X - 1, CraftingTerminalMenu.RESULT_Y - 1, 18, 18, c("result"), c("result.tip"));
        if (menu.wireless()) {
            int cap = WifiTerminalItem.capacity(), e = menu.energy();
            float f = cap <= 0 ? 0 : Math.min(1f, e / (float) cap);
            int col = e < 200 ? TermKit.RED : f < 0.2f ? TermKit.AMBER : TermKit.CYAN;
            Component text = c("energy", StorageFormat.count(e));
            TermKit.text(g, font, text, x + BTN_X, y + 112, col);
            TermKit.bar(g, x + BTN_X, y + 122, PW - 12, 4, f, col);
            tip(BTN_X, 110, PW - 12, 18, c("energy.title"), c("energy.tip", e, cap));
        } else {
            TermKit.text(g, font, c("block"), x + BTN_X, y + 112, TermKit.DIM);
        }
    }
}
