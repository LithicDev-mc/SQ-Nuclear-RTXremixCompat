package com.sqc.nuclearsq.data.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.sqc.nuclearsq.data.DataConsoleMenu;
import com.sqc.nuclearsq.data.DataPayloads;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class DataConsoleScreen extends AbstractContainerScreen<DataConsoleMenu> {
    private static final int MAX_LINES = 300, LINE_H = 10;
    private static final List<Component> OUTPUT = new ArrayList<>();
    private static final List<String> HISTORY = new ArrayList<>();

    private EditBox input;
    private int scroll, historyPos = -1;
    private String watch;
    private int watchTicks;

    public DataConsoleScreen(DataConsoleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 300;
        this.imageHeight = 190;
    }

    public static void clearSession() {
        OUTPUT.clear();
        HISTORY.clear();
    }

    public static void receive(List<Component> lines) {
        OUTPUT.addAll(lines);
        while (OUTPUT.size() > MAX_LINES) OUTPUT.remove(0);
        if (Minecraft.getInstance().screen instanceof DataConsoleScreen s) s.scroll = 0;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, leftPos + 18, topPos + imageHeight - 18, imageWidth - 28, 10, Component.empty());
        input.setBordered(false);
        input.setMaxLength(200);
        input.setTextColor(TermKit.tcol(TermKit.GREEN));
        input.setFormatter((s, pos) -> FormattedCharSequence.forward(s, TermKit.mono(s).getStyle()));
        addRenderableWidget(input);
        setInitialFocus(input);
        if (OUTPUT.isEmpty()) OUTPUT.add(Component.translatable("data.nuclearsq.console.welcome"));
    }

    private void submit() {
        String line = input.getValue().trim();
        input.setValue("");
        historyPos = -1;
        if (line.isEmpty()) return;
        if (HISTORY.isEmpty() || !HISTORY.get(HISTORY.size() - 1).equals(line)) HISTORY.add(line);
        OUTPUT.add(Component.literal("> " + line));
        scroll = 0;
        watch = null;
        String[] a = line.toLowerCase(Locale.ROOT).split("\\s+");
        switch (a[0]) {
            case "clear", "cls" -> OUTPUT.clear();
            case "stop" -> OUTPUT.add(Component.translatable("data.nuclearsq.console.stopped"));
            case "watch", "beobachte" -> {
                watch = "get" + line.substring(a[0].length());
                watchTicks = 0;
                OUTPUT.add(Component.translatable("data.nuclearsq.console.watching"));
                PacketDistributor.sendToServer(new DataPayloads.Command(watch));
            }
            default -> PacketDistributor.sendToServer(new DataPayloads.Command(line));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (watch != null && ++watchTicks % 20 == 0) PacketDistributor.sendToServer(new DataPayloads.Command(watch));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && !HISTORY.isEmpty()) {
            if (historyPos < 0) historyPos = HISTORY.size();
            historyPos = Mth.clamp(historyPos + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, HISTORY.size());
            input.setValue(historyPos < HISTORY.size() ? HISTORY.get(historyPos) : "");
            return true;
        }
        if (key == GLFW.GLFW_KEY_C && (mods & GLFW.GLFW_MOD_CONTROL) != 0 && watch != null) {
            watch = null;
            OUTPUT.add(Component.translatable("data.nuclearsq.console.stopped"));
            return true;
        }
        return input.keyPressed(key, scan, mods) || input.canConsumeInput() || super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll + (int) Math.signum(sy) * 3);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8);
        TermKit.header(g, font, title, Component.translatable(watch != null ? "gui.nuclearsq.data.console.watch" : "gui.nuclearsq.data.console.help"),
                x + 8, y + 7, w - 16, watch != null ? TermKit.AMBER : TermKit.DIM);
        TermKit.rule(g, x + 8, y + 18, w - 16, TermKit.DIM);
        int top = y + 22, bottom = y + h - 24, rows = (bottom - top) / LINE_H;
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component c : OUTPUT) wrapped.addAll(font.split(TermKit.mono(c), w - 20));
        scroll = Mth.clamp(scroll, 0, Math.max(0, wrapped.size() - rows));
        int end = wrapped.size() - scroll;
        for (int r = 0; r < rows; r++) {
            int i = end - rows + r;
            if (i < 0 || i >= wrapped.size()) continue;
            g.drawString(font, wrapped.get(i), x + 10, top + r * LINE_H, TermKit.tcol(TermKit.GREEN), false);
        }
        TermKit.rule(g, x + 8, y + h - 22, w - 16, TermKit.FAINT);
        TermKit.prompt(g, font, x + 8, y + h - 18, Component.literal(">"), TermKit.GREEN);
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        TermKit.renderTip(g, font, mx, my);
    }
}
