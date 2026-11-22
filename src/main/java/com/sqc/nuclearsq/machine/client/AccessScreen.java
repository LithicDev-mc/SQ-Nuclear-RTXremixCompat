package com.sqc.nuclearsq.machine.client;

import com.sqc.nuclearsq.machine.AccessPayloads;
import com.sqc.nuclearsq.machine.MachineAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class AccessScreen extends Screen {
    private static final int W = 240, ROW = 12, MAX_ROWS = 8;
    private static final int[] LEVELS = {MachineAccess.USE, MachineAccess.ADMIN};

    private AccessPayloads.State state;
    private EditBox name;
    private int kind, level;

    public AccessScreen(AccessPayloads.State state) {
        super(Component.translatable("gui.nuclearsq.access.title"));
        this.state = state;
    }

    public void update(AccessPayloads.State s) {
        state = s;
    }

    public net.minecraft.core.BlockPos pos() {
        return state.pos();
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.access." + key, args);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - 150) / 2; }

    private void send(int op, int k, String n, int lv) {
        PacketDistributor.sendToServer(new AccessPayloads.Action(state.pos(), op, k, n, lv));
    }

    @Override
    protected void init() {
        name = new EditBox(font, left() + 11, top() + 121, 104, 10, t("name"));
        name.setBordered(false);
        name.setMaxLength(32);
        name.setTextColor(TermKit.tcol(TermKit.GREEN));
        addRenderableWidget(name);
    }

    @Override
    protected void setInitialFocus() {
        if (state.admin()) setInitialFocus(name);
    }

    private boolean add() {
        if (name.getValue().isBlank()) return false;
        send(AccessPayloads.SET, kind, name.getValue(), LEVELS[level]);
        name.setValue("");
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && state.admin() && add()) return true;
        return super.keyPressed(key, scan, mods);
    }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        int x = left(), y = top();
        if (button == 0 && state.admin()) {
            for (int i = 0; i < state.keys().size() && i < MAX_ROWS; i++) {
                if (over(mx, my, x + W - 20, y + 36 + i * ROW, 12, 11)) {
                    String key = state.keys().get(i);
                    send(AccessPayloads.REMOVE, key.startsWith("g:") ? 1 : 0, key.substring(2), 0);
                    return true;
                }
            }
            if (over(mx, my, x + 122, y + 118, 36, 14)) { kind = 1 - kind; return true; }
            if (over(mx, my, x + 160, y + 118, 36, 14)) { level = (level + 1) % LEVELS.length; return true; }
            if (over(mx, my, x + 198, y + 118, 34, 14) && add()) return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        int x = left(), y = top();
        TermKit.monitor(g, x - 4, y - 4, W + 8, 158);
        TermKit.header(g, font, title, t("owner", state.owner()), x + 8, y + 8, W - 16, TermKit.MID);
        TermKit.rule(g, x + 8, y + 22, W - 16, TermKit.DIM);
        TermKit.text(g, font, t("rights"), x + 8, y + 26, TermKit.MID);
        if (state.keys().isEmpty()) TermKit.text(g, font, t("none"), x + 8, y + 38, TermKit.DIM);
        for (int i = 0; i < state.keys().size() && i < MAX_ROWS; i++) {
            String key = state.keys().get(i);
            int ry = y + 36 + i * ROW;
            Component who = Component.literal((key.startsWith("g:") ? "[" + t("team").getString() + "] " : "") + key.substring(2));
            TermKit.text(g, font, who, x + 8, ry + 2, TermKit.GREEN);
            TermKit.right(g, font, t("level." + state.levels().get(i)), x + W - 24, ry + 2, TermKit.CYAN);
            if (state.admin()) TermKit.button(g, font, x + W - 20, ry, 12, 11, Component.literal("x"), TermKit.RED, true, over(mx, my, x + W - 20, ry, 12, 11), false);
        }
        if (state.admin()) {
            TermKit.rule(g, x + 8, y + 112, W - 16, TermKit.FAINT);
            TermKit.frame(g, font, x + 8, y + 118, 110, 14, null, name.isFocused() ? TermKit.MID : TermKit.FAINT);
            TermKit.button(g, font, x + 122, y + 118, 36, 14, t("kind." + kind), TermKit.AMBER, true, over(mx, my, x + 122, y + 118, 36, 14), false);
            TermKit.button(g, font, x + 160, y + 118, 36, 14, t("level." + LEVELS[level]), TermKit.CYAN, true, over(mx, my, x + 160, y + 118, 36, 14), false);
            TermKit.button(g, font, x + 198, y + 118, 34, 14, t("add"), TermKit.GREEN, !name.getValue().isBlank(), over(mx, my, x + 198, y + 118, 34, 14), false);
            TermKit.text(g, font, t("hint"), x + 8, y + 138, TermKit.DIM);
        } else {
            name.visible = false;
            TermKit.text(g, font, t("readonly"), x + 8, y + 138, TermKit.DIM);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int x = left(), y = top();
        super.render(g, mx, my, pt);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, 158);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void request(net.minecraft.core.BlockPos pos) {
        PacketDistributor.sendToServer(new AccessPayloads.Action(pos, AccessPayloads.OPEN, 0, "", 0));
    }

    public static void receive(AccessPayloads.State s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessScreen as && as.pos().equals(s.pos())) as.update(s);
        else mc.setScreen(new AccessScreen(s));
    }
}
