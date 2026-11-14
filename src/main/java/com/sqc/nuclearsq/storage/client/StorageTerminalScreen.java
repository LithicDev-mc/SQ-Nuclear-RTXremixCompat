package com.sqc.nuclearsq.storage.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.storage.StorageFormat;
import com.sqc.nuclearsq.storage.StoragePayloads;
import com.sqc.nuclearsq.storage.StoragePayloads.Entry;
import com.sqc.nuclearsq.storage.StorageTerminalMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class StorageTerminalScreen<M extends StorageTerminalMenu> extends AbstractContainerScreen<M> {
    private static final int COLS = StorageTerminalMenu.COLS, ROWS = StorageTerminalMenu.ROWS;
    private static final int GX = StorageTerminalMenu.GRID_X, GY = StorageTerminalMenu.GRID_Y;
    private static final int BAR_X = GX + COLS * 18 + 2, SORT_W = 46;
    private static boolean byName;
    private static String lastSearch = "";

    private EditBox search;
    private List<Entry> shown = List.of();
    private int seenVersion = -1, scroll;
    private String filtered = null;
    private boolean dragging;
    private java.util.Set<com.sqc.nuclearsq.storage.ItemKey> craftKeys = java.util.Set.of();
    private ItemStack ordering = ItemStack.EMPTY;
    private EditBox amount;
    private int seenMessage = -1;
    private long messageUntil;
    private static final int OX = GX - 1, OY = GY - 1, OW = COLS * 18, OH = ROWS * 18;
    private static final int[][] ORDER_BTNS = {
            {OX + 8, OY + 36, 22, 12, -64}, {OX + 32, OY + 36, 18, 12, -1}, {OX + 112, OY + 36, 18, 12, 1},
            {OX + 132, OY + 36, 22, 12, 64}, {OX + 8, OY + 64, 70, 14, 0}, {OX + 84, OY + 64, 70, 14, -9999}};

    public StorageTerminalScreen(M menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = menu.width();
        imageHeight = menu.height();
    }

    private static net.minecraft.network.chat.MutableComponent t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.storage.terminal." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + GX + 2, topPos + 20, COLS * 18 - SORT_W - 8, 9, Component.empty());
        search.setBordered(false);
        search.setMaxLength(50);
        search.setTextColor(TermKit.tcol(TermKit.GREEN));
        search.setValue(lastSearch);
        search.setHint(t("search").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(search);
        amount = new EditBox(font, leftPos + OX + 58, topPos + OY + 38, 46, 9, Component.empty()) {
            @Override
            public void renderWidget(GuiGraphics g, int mx, int my, float pt) {}
        };
        amount.setBordered(false);
        amount.setMaxLength(4);
        amount.setFilter(v -> v.isEmpty() || v.chars().allMatch(Character::isDigit));
        amount.setTextColor(TermKit.tcol(TermKit.CYAN));
        amount.setValue("1");
        amount.setVisible(!ordering.isEmpty());
        addRenderableWidget(amount);
    }

    private boolean craftable(ItemStack s) {
        return craftKeys.contains(com.sqc.nuclearsq.storage.ItemKey.of(s));
    }

    private void openOrder(ItemStack proto) {
        ordering = proto.copyWithCount(1);
        amount.setValue("1");
        amount.setVisible(true);
        setFocused(amount);
        amount.setFocused(true);
        search.setFocused(false);
    }

    private void closeOrder() {
        ordering = ItemStack.EMPTY;
        amount.setVisible(false);
        amount.setFocused(false);
    }

    private int amountValue() {
        try {
            return Mth.clamp(Integer.parseInt(amount.getValue()), 1, StorageTerminalMenu.MAX_ORDER);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void sendOrder() {
        PacketDistributor.sendToServer(new StoragePayloads.Order(menu.containerId, ordering, amountValue()));
        closeOrder();
    }

    protected void tip(int x, int y, int w, int h, Component... lines) {
        TermKit.tip(leftPos + x, topPos + y, w, h, lines);
    }

    private boolean overOrder(int i, double mx, double my) {
        int[] b = ORDER_BTNS[i];
        return mx >= leftPos + b[0] && mx < leftPos + b[0] + b[2] && my >= topPos + b[1] && my < topPos + b[1] + b[3];
    }

    private void refresh() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (seenVersion == menu.version() && q.equals(filtered)) return;
        seenVersion = menu.version();
        filtered = q;
        lastSearch = search.getValue();
        List<Entry> out = new ArrayList<>();
        java.util.Set<com.sqc.nuclearsq.storage.ItemKey> stored = new java.util.HashSet<>(), craft = new java.util.HashSet<>();
        for (Entry e : menu.entries()) {
            stored.add(com.sqc.nuclearsq.storage.ItemKey.of(e.stack()));
            if (matches(e.stack(), q)) out.add(e);
        }
        for (ItemStack c : menu.craftables()) {
            var key = com.sqc.nuclearsq.storage.ItemKey.of(c);
            if (key == null || !craft.add(key) || stored.contains(key)) continue;
            if (matches(c, q)) out.add(new Entry(c, 0));
        }
        craftKeys = craft;
        Comparator<Entry> name = Comparator.comparing(e -> e.stack().getHoverName().getString().toLowerCase(Locale.ROOT));
        out.sort(byName ? name : Comparator.comparingLong((Entry e) -> -e.count()).thenComparing(name));
        shown = out;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private static boolean matches(ItemStack s, String q) {
        if (q.isEmpty()) return true;
        if (q.startsWith("@")) return BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().startsWith(q.substring(1));
        return s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q);
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLS - 1) / COLS - ROWS);
    }

    private int rawCell(double mx, double my) {
        if (mx < leftPos + GX - 1 || my < topPos + GY - 1) return -1;
        int cx = (int) ((mx - leftPos - GX + 1) / 18), cy = (int) ((my - topPos - GY + 1) / 18);
        return cx >= COLS || cy >= ROWS ? -1 : (scroll + cy) * COLS + cx;
    }

    private int cellAt(double mx, double my) {
        int i = rawCell(mx, my);
        return i < 0 ? -1 : i < shown.size() ? i : -2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        refresh();
        if (!ordering.isEmpty()) {
            for (int i = 0; i < ORDER_BTNS.length && button == 0; i++) {
                if (!overOrder(i, mx, my)) continue;
                int d = ORDER_BTNS[i][4];
                if (d == 0) sendOrder();
                else if (d == -9999) closeOrder();
                else amount.setValue(String.valueOf(Mth.clamp(amountValue() + d, 1, StorageTerminalMenu.MAX_ORDER)));
                return true;
            }
            if (amount.mouseClicked(mx, my, button)) return true;
            if (mx >= leftPos + OX && mx < leftPos + OX + OW && my >= topPos + 16 && my < topPos + OY + OH) return true;
            return super.mouseClicked(mx, my, button);
        }
        int sx = leftPos + GX + COLS * 18 - SORT_W - 1, sy = topPos + 18;
        if (button == 0 && mx >= sx && mx < sx + SORT_W && my >= sy && my < sy + 12) {
            byName = !byName;
            seenVersion = -1;
            return true;
        }
        if (mx >= leftPos + BAR_X && mx < leftPos + BAR_X + 6 && my >= topPos + GY - 1 && my < topPos + GY - 1 + ROWS * 18) {
            dragging = true;
            dragTo(my);
            return true;
        }
        int i = cellAt(mx, my);
        if (i != -1 && (button == 0 || button == 1)) {
            if (!menu.getCarried().isEmpty()) {
                send(ItemStack.EMPTY, button == 0 ? StorageTerminalMenu.INSERT : StorageTerminalMenu.INSERT_ONE);
            } else if (i >= 0 && button == 0 && craftable(shown.get(i).stack())
                    && (shown.get(i).count() == 0 || Screen.hasControlDown())) {
                openOrder(shown.get(i).stack());
            } else if (i >= 0) {
                int mode = button == 1 ? StorageTerminalMenu.TAKE_HALF
                        : Screen.hasShiftDown() ? StorageTerminalMenu.TAKE_SHIFT : StorageTerminalMenu.TAKE;
                send(shown.get(i).stack(), mode);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void send(ItemStack proto, int mode) {
        PacketDistributor.sendToServer(new StoragePayloads.Action(menu.containerId, proto, mode));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private void dragTo(double my) {
        float f = (float) ((my - topPos - GY) / (ROWS * 18 - 15));
        scroll = Mth.clamp(Math.round(f * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!ordering.isEmpty()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closeOrder();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) sendOrder();
            else return amount.keyPressed(key, scan, mods) || amount.canConsumeInput() || super.keyPressed(key, scan, mods);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
        if (search.isFocused()) return search.keyPressed(key, scan, mods) || search.canConsumeInput();
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        refresh();
        int x = leftPos, y = topPos;
        TermKit.monitor(g, x - 4, y - 4, imageWidth + 8, imageHeight + 8);
        TermKit.text(g, font, title, x + 8, y + 7, TermKit.GREEN);
        TermKit.frame(g, font, x + GX - 1, y + 18, COLS * 18 - SORT_W - 3, 12, null, search.isFocused() ? TermKit.MID : TermKit.FAINT);
        int sx = x + GX + COLS * 18 - SORT_W - 1, sy = y + 18;
        boolean hs = mx >= sx && mx < sx + SORT_W && my >= sy && my < sy + 12;
        TermKit.button(g, font, sx, sy, SORT_W, 12, t(byName ? "sort.name" : "sort.count"), TermKit.CYAN, true, hs, false);
        tip(sx - x, sy - y, SORT_W, 12, t("sort"), t("sort.tip"));

        int hover = rawCell(mx, my);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int cx = x + GX + c * 18, cy = y + GY + r * 18, i = (scroll + r) * COLS + c;
                boolean hov = hover == i;
                TermKit.frame(g, font, cx - 1, cy - 1, 18, 18, null, hov ? TermKit.GREEN : TermKit.FAINT);
                if (i >= shown.size()) continue;
                Entry e = shown.get(i);
                g.renderItem(e.stack(), cx, cy);
                boolean craft = craftable(e.stack());
                if (craft && e.count() > 0) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(cx, cy, cx + 2, cy + 2, TermKit.col(TermKit.CYAN));
                    g.pose().popPose();
                }
                String n = e.count() == 0 && craft ? "+" : StorageFormat.count(e.count());
                boolean held = e.reserved() > 0;
                if (held && e.reserved() >= e.count()) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 150);
                    g.fill(cx, cy, cx + 16, cy + 16, 0x99303030);
                    g.pose().popPose();
                }
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.pose().scale(0.6f, 0.6f, 1);
                g.drawString(font, n, (int) ((cx + 16) / 0.6f) - font.width(n), (int) ((cy + 11) / 0.6f), held ? 0xFF9A9A9A : 0xFFFFFFFF, true);
                g.pose().popPose();
            }
        }
        int bh = ROWS * 18, by = y + GY - 1;
        g.fill(x + BAR_X, by, x + BAR_X + 6, by + bh, TermKit.col(TermKit.FAINT));
        int max = maxScroll(), knob = 15;
        int ky = by + (max == 0 ? 0 : Math.round((bh - knob) * scroll / (float) max));
        g.fill(x + BAR_X + 1, ky + 1, x + BAR_X + 5, ky + knob - 1, TermKit.col(max == 0 ? TermKit.DIM : TermKit.MID));

        if (menu.jobs() > 0) TermKit.right(g, font, t("jobs", menu.jobs()), x + imageWidth - 8, y + 7, TermKit.CYAN);
        if (!ordering.isEmpty()) renderOrder(g, x, y, mx, my);

        int sy2 = y + GY + ROWS * 18 + 4;
        if (menu.messageId() != seenMessage) {
            if (seenMessage != -1 || menu.messageId() > 0) messageUntil = net.minecraft.Util.getMillis() + 6000;
            seenMessage = menu.messageId();
        }
        if (net.minecraft.Util.getMillis() < messageUntil) {
            Component m = menu.message();
            TermKit.text(g, font, font.width(m) > COLS * 18 + 8 ? Component.literal(font.plainSubstrByWidth(m.getString(), COLS * 18)) : m,
                    x + GX, sy2, TermKit.CYAN);
            tip(GX, sy2 - y - 1, COLS * 18, 10, t("order"), m);
        } else if (menu.racks() == 0) {
            TermKit.text(g, font, t("no_racks"), x + GX, sy2, TermKit.AMBER);
            tip(GX, sy2 - y - 1, COLS * 18, 10, t("no_racks"), t("no_racks.tip"));
        } else if (menu.online() == 0) {
            TermKit.text(g, font, t("offline", menu.racks()), x + GX, sy2, TermKit.RED);
            tip(GX, sy2 - y - 1, COLS * 18, 10, t("offline", menu.racks()), t("offline.tip"));
        } else {
            float f = menu.capacity() <= 0 ? 0 : menu.used() / (float) menu.capacity();
            Component used = t("used", StorageFormat.count(menu.used()), StorageFormat.count(menu.capacity()));
            TermKit.text(g, font, used, x + GX, sy2, f >= 0.98f ? TermKit.RED : TermKit.GREEN);
            TermKit.right(g, font, t("racks", menu.online(), menu.racks()), x + GX + COLS * 18 + 6, sy2,
                    menu.online() < menu.racks() ? TermKit.AMBER : TermKit.MID);
            TermKit.bar(g, x + GX - 1, sy2 + 10, COLS * 18 + 8, 4, f, f >= 0.98f ? TermKit.RED : TermKit.GREEN);
            tip(GX, sy2 - y - 1, COLS * 18, 16, used, t("used.tip", menu.used(), menu.capacity(), shown.size()),
                    t("racks.tip", menu.online(), menu.racks()));
        }
        for (Slot s : menu.slots) TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.FAINT);
        renderExtras(g, x, y, mx, my);
        TermKit.scanlines(g, x - 4, y - 4, imageWidth + 8, imageHeight + 8);
    }

    protected void renderExtras(GuiGraphics g, int x, int y, int mx, int my) {}

    private void renderOrder(GuiGraphics g, int x, int y, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.fill(x + OX, y + OY, x + OX + OW, y + OY + OH, TermKit.col(TermKit.SCREEN) | 0xFF000000);
        TermKit.frame(g, font, x + OX, y + OY, OW, OH, t("order"), TermKit.CYAN);
        TermKit.frame(g, font, x + OX + 7, y + OY + 9, 18, 18, null, TermKit.GREEN);
        g.renderItem(ordering, x + OX + 8, y + OY + 10);
        TermKit.text(g, font, ordering.getHoverName(), x + OX + 30, y + OY + 14, TermKit.GREEN);
        TermKit.frame(g, font, x + OX + 54, y + OY + 32, 54, 20, null, TermKit.CYAN);
        String num = amount.getValue().isEmpty() ? "0" : amount.getValue();
        boolean caret = amount.isFocused() && (net.minecraft.Util.getMillis() / 500) % 2 == 0;
        float sc = 2f;
        int nw = (int) (font.width(num) * sc);
        g.pose().pushPose();
        g.pose().translate(x + OX + 81 - nw / 2f, y + OY + 36, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, num, 0, 0, TermKit.tcol(TermKit.CYAN), false);
        g.pose().popPose();
        if (caret) g.fill(x + OX + 81 + nw / 2 + 1, y + OY + 36, x + OX + 81 + nw / 2 + 3, y + OY + 36 + 14, TermKit.tcol(TermKit.CYAN));
        TermKit.bar(g, x + OX + 8, y + OY + 56, OW - 16, 3, amountValue() / (float) StorageTerminalMenu.MAX_ORDER, TermKit.CYAN);
        TermKit.right(g, font, Component.literal(String.valueOf(StorageTerminalMenu.MAX_ORDER)), x + OX + OW - 8, y + OY + 28, TermKit.DIM);
        for (int i = 0; i < ORDER_BTNS.length; i++) {
            int[] b = ORDER_BTNS[i];
            Component label = b[4] == 0 ? t("order.send") : b[4] == -9999 ? t("order.cancel")
                    : Component.literal(b[4] > 0 ? "+" + b[4] : String.valueOf(b[4]));
            TermKit.button(g, font, x + b[0], y + b[1], b[2], b[3], label, b[4] == -9999 ? TermKit.AMBER : TermKit.CYAN, true,
                    overOrder(i, mx, my), false);
        }
        TermKit.text(g, font, t("order.hint"), x + OX + 8, y + OY + 88, TermKit.DIM);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int i = ordering.isEmpty() ? cellAt(mx, my) : -1;
        if (i >= 0 && menu.getCarried().isEmpty()) {
            Entry e = shown.get(i);
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(e.stack()));
            lines.add(t("stored", String.format(Locale.GERMAN, "%,d", e.count())).withStyle(ChatFormatting.GREEN));
            if (e.reserved() > 0) lines.add(t("reserved", String.format(Locale.GERMAN, "%,d", e.reserved())).withStyle(ChatFormatting.GRAY));
            if (craftable(e.stack())) lines.add(t(e.count() == 0 ? "craft.click" : "craft.ctrl").withStyle(ChatFormatting.AQUA));
            g.renderComponentTooltip(font, lines, mx, my);
        } else if ((hoveredSlot != null && hoveredSlot.hasItem()) || !TermKit.renderTip(g, font, mx, my)) {
            renderTooltip(g, mx, my);
        }
    }
}
