package com.sqc.nuclearsq.nanobot.client;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.nanobot.GadgetKeyPayload;
import com.sqc.nuclearsq.nanobot.NanoCanisterItem;
import com.sqc.nuclearsq.nanobot.NanoGadget;
import com.sqc.nuclearsq.nanobot.NanoTask;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class NanoWheelScreen extends Screen {
    private static final int RX = 150, RY = 104, BW = 120, BH = 14, DEAD = 24;
    private static final int TOGGLE = -1, GADGET = -2, SETTINGS = -3, BACK = -4;
    private boolean gadget;

    private NanoWheelScreen() {
        super(Component.translatable("gui.nuclearsq.nanobots.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new NanoWheelScreen());
    }

    private int[] items() {
        List<Integer> l = new ArrayList<>();
        if (gadget) {
            for (int i = NanoTask.autoCount(); i < NanoTask.values().length; i++) l.add(i);
            l.add(BACK);
        } else {
            l.add(TOGGLE);
            for (int i = 0; i < NanoTask.autoCount(); i++) l.add(i);
            l.add(GADGET);
            l.add(SETTINGS);
        }
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Component label(int v) {
        return v >= 0 ? NanoTask.byOrdinal(v).label() : Component.translatable("gui.nuclearsq.nanobots.wheel." + switch (v) {
            case TOGGLE -> "toggle";
            case GADGET -> "gadget";
            case SETTINGS -> "settings";
            default -> "back";
        });
    }

    private int hovered(double mx, double my, int n) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < DEAD * DEAD) return -1;
        return Math.floorMod((int) Math.round(Math.atan2(dx, -dy) / (2 * Math.PI) * n), n);
    }

    private ItemStack canister() {
        var p = Minecraft.getInstance().player;
        if (p == null) return ItemStack.EMPTY;
        return p.getMainHandItem().getItem() instanceof NanoCanisterItem ? p.getMainHandItem() : p.getOffhandItem();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int[] it = items();
        int n = it.length, cx = width / 2, cy = height / 2, hov = hovered(mx, my, n);
        ItemStack can = canister();
        int cur = can.getItem() instanceof NanoCanisterItem ? NanoCanisterItem.task(can).ordinal() : -99;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            int x = cx + (int) Math.round(Math.sin(a) * RX), y = cy - (int) Math.round(Math.cos(a) * RY);
            if (i == hov) TermKit.line(g, cx, cy, x, y, TermKit.GREEN);
            int color = it[i] == TOGGLE ? TermKit.AMBER : it[i] < 0 ? TermKit.CYAN : TermKit.GREEN;
            int bw = Math.min(BW, Math.max(60, font.width(label(it[i])) + 12));
            TermKit.button(g, font, x - bw / 2, y - BH / 2, bw, BH, label(it[i]), color, true, i == hov, it[i] == cur);
        }
        int tw = font.width(title);
        g.fill(cx - tw / 2 - 5, cy - 7, cx + tw / 2 + 5, cy + 7, 0xC0000000);
        TermKit.center(g, font, title, cx, cy - 4, TermKit.MID);
    }

    private boolean select(double mx, double my) {
        int[] it = items();
        int h = hovered(mx, my, it.length);
        if (h < 0) return false;
        int v = it[h];
        if (v == GADGET || v == BACK) {
            gadget = v == GADGET;
            return true;
        }
        PacketDistributor.sendToServer(new GadgetKeyPayload(v >= 0 ? NanoGadget.KEY_TASK : v == TOGGLE ? NanoGadget.KEY_TOGGLE : NanoGadget.KEY_MENU, v));
        if (v != SETTINGS) onClose();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return button == 0 ? select(mx, my) : super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return button == 1 ? select(mx, my) : super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
