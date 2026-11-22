package com.sqc.nuclearsq.apps.client;

import org.lwjgl.glfw.GLFW;

import com.sqc.nuclearsq.apps.AppAccounts;
import com.sqc.nuclearsq.apps.AppPayloads;
import com.sqc.nuclearsq.economy.BankNet;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public class LoginMonitorScreen extends Screen {
    private static final int W = 232, H = 168, BTN_H = 14;

    private enum Mode { OFFLINE, CREATE, LOGIN, IN, CHANGE }

    private AppPayloads.State state;
    private boolean changing;
    private final EditBox[] fields = new EditBox[3];

    public LoginMonitorScreen(AppPayloads.State state) {
        super(Component.translatable("gui.nuclearsq.app_login.title"));
        this.state = state;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.app_login." + key, args);
    }

    public BlockPos pos() {
        return state.pos();
    }

    public void update(AppPayloads.State s) {
        state = s;
        if (!s.message().isEmpty()) {
            if (s.ok()) changing = false;
            clearFields();
        }
        layout();
    }

    private Mode mode() {
        if (state.loggedIn()) return changing ? Mode.CHANGE : Mode.IN;
        if (state.link() != BankNet.Link.ONLINE.ordinal()) return Mode.OFFLINE;
        return state.account() ? Mode.LOGIN : Mode.CREATE;
    }

    private int fieldCount() {
        return switch (mode()) {
            case CREATE -> 2;
            case LOGIN -> 1;
            case CHANGE -> 3;
            default -> 0;
        };
    }

    private String[] fieldLabels() {
        return switch (mode()) {
            case CREATE -> new String[]{"password", "repeat"};
            case LOGIN -> new String[]{"password"};
            case CHANGE -> new String[]{"old", "new", "repeat"};
            default -> new String[0];
        };
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int fieldY(int i) {
        return top() + 52 + i * 26;
    }

    @Override
    protected void init() {
        for (int i = 0; i < fields.length; i++) {
            EditBox f = new EditBox(font, left() + 14, fieldY(i) + 12, W - 28, 10, Component.empty());
            f.setBordered(false);
            f.setMaxLength(AppAccounts.MAX_LEN);
            f.setTextColor(TermKit.tcol(TermKit.GREEN));
            f.setFormatter((s, pos) -> FormattedCharSequence.forward("*".repeat(s.length()), TermKit.mono("").getStyle()));
            fields[i] = f;
            addRenderableWidget(f);
        }
        layout();
    }

    private void layout() {
        int n = fieldCount();
        for (int i = 0; i < fields.length; i++) {
            fields[i].visible = i < n;
            fields[i].active = i < n;
            fields[i].setX(left() + 14);
            fields[i].setY(fieldY(i) + 12);
        }
        if (n > 0 && getFocused() != fields[0] && getFocused() != fields[Math.min(n - 1, 1)] && getFocused() != fields[Math.min(n - 1, 2)]) {
            setFocused(fields[0]);
        }
    }

    private void clearFields() {
        for (EditBox f : fields) f.setValue("");
    }

    private void send(int op) {
        PacketDistributor.sendToServer(new AppPayloads.Action(state.pos(), op, fields[0].getValue(), fields[1].getValue(), fields[2].getValue()));
    }

    private void submit() {
        switch (mode()) {
            case CREATE -> send(AppPayloads.CREATE);
            case LOGIN -> send(AppPayloads.LOGIN);
            case CHANGE -> send(AppPayloads.CHANGE);
            default -> { }
        }
    }

    private int buttonY() {
        return top() + H - 40;
    }

    private record Btn(int x, int w, Component label, int color, Runnable run) {}

    private Btn[] buttons() {
        int x = left() + 10, y2 = (W - 24) / 2;
        return switch (mode()) {
            case CREATE -> new Btn[]{new Btn(x, W - 20, t("create"), TermKit.GREEN, this::submit)};
            case LOGIN -> new Btn[]{new Btn(x, W - 20, t("login"), TermKit.GREEN, this::submit)};
            case IN -> new Btn[]{
                    new Btn(x, y2, t("logout"), TermKit.RED, () -> send(AppPayloads.LOGOUT)),
                    new Btn(x + y2 + 4, y2, t("change"), TermKit.CYAN, () -> {
                        changing = true;
                        clearFields();
                        layout();
                    })};
            case CHANGE -> new Btn[]{
                    new Btn(x, y2, t("save"), TermKit.GREEN, this::submit),
                    new Btn(x + y2 + 4, y2, t("cancel"), TermKit.AMBER, () -> {
                        changing = false;
                        clearFields();
                        layout();
                    })};
            default -> new Btn[0];
        };
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (TermKit.styleClick(mx, my)) return true;
            for (Btn b : buttons()) {
                if (over(mx, my, b.x, buttonY(), b.w, BTN_H)) {
                    b.run.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            int n = fieldCount();
            for (int i = 0; i < n - 1; i++) {
                if (getFocused() == fields[i]) {
                    setFocused(fields[i + 1]);
                    return true;
                }
            }
            if (n > 0) {
                submit();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int x = left(), y = top();
        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8);
        boolean online = state.link() == BankNet.Link.ONLINE.ordinal();
        TermKit.header(g, font, title, online ? t("online") : t("offline"), x + 8, y + 7, W - 16, online ? TermKit.GREEN : TermKit.RED);
        TermKit.rule(g, x + 8, y + 18, W - 16, TermKit.DIM);
        Mode mode = mode();
        Component name = Component.literal(state.name());
        switch (mode) {
            case OFFLINE -> {
                TermKit.text(g, font, t("head.offline"), x + 10, y + 26, TermKit.RED);
                TermKit.wrap(g, font, Component.translatable("gui.nuclearsq.app_login.link." + BankNet.Link.values()[state.link()].id()),
                        x + 10, y + 40, W - 20, 5, TermKit.MID);
            }
            case CREATE -> {
                TermKit.text(g, font, t("head.create", name), x + 10, y + 26, TermKit.CYAN);
                TermKit.wrap(g, font, t("hint.create", AppAccounts.MIN_LEN), x + 10, y + 37, W - 20, 1, TermKit.DIM);
            }
            case LOGIN -> {
                TermKit.text(g, font, t("head.login", name), x + 10, y + 26, TermKit.CYAN);
                TermKit.wrap(g, font, state.lockSeconds() > 0 ? t("locked", state.lockSeconds()) : t("hint.login"), x + 10, y + 37, W - 20, 1,
                        state.lockSeconds() > 0 ? TermKit.RED : TermKit.DIM);
            }
            case IN -> {
                TermKit.text(g, font, t("head.in", name), x + 10, y + 26, TermKit.GREEN);
                TermKit.wrap(g, font, t("hint.in", Math.max(1, state.minutesLeft())), x + 10, y + 40, W - 20, 5, TermKit.MID);
            }
            case CHANGE -> TermKit.text(g, font, t("head.change"), x + 10, y + 26, TermKit.CYAN);
        }
        String[] labels = fieldLabels();
        for (int i = 0; i < labels.length; i++) {
            int fy = fieldY(i);
            TermKit.text(g, font, t("field." + labels[i]), x + 10, fy, TermKit.MID);
            boolean focus = getFocused() == fields[i];
            g.fill(x + 10, fy + 9, x + W - 10, fy + 23, TermKit.col(focus ? TermKit.CYAN : TermKit.DIM));
            g.fill(x + 11, fy + 10, x + W - 11, fy + 22, TermKit.col(TermKit.SCREEN));
        }
        for (Btn b : buttons()) {
            TermKit.button(g, font, b.x, buttonY(), b.w, BTN_H, b.label, b.color, true, over(mx, my, b.x, buttonY(), b.w, BTN_H), false);
        }
        TermKit.rule(g, x + 8, y + H - 20, W - 16, TermKit.FAINT);
        if (!state.message().isEmpty()) {
            TermKit.fit(g, font, t("msg." + state.message(), AppAccounts.MIN_LEN, AppAccounts.MAX_LEN, state.lockSeconds()), x + 10, y + H - 14, W - 20,
                    state.ok() ? TermKit.GREEN : TermKit.AMBER);
        } else {
            TermKit.fit(g, font, t("footer"), x + 10, y + H - 14, W - 20, TermKit.DIM);
        }
        super.render(g, mx, my, pt);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, H + 8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.distanceToSqr(state.pos().getX() + 0.5, state.pos().getY() + 0.5, state.pos().getZ() + 0.5) > 8.5 * 8.5) {
            onClose();
        }
    }

    public static void receive(AppPayloads.State s) {
        ClientAppSession.receive(s);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LoginMonitorScreen ls && ls.pos().equals(s.pos())) ls.update(s);
        else if (s.message().isEmpty()) mc.setScreen(new LoginMonitorScreen(s));
    }
}
