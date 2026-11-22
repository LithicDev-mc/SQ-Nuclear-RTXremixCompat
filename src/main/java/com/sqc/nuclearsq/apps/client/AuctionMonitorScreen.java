package com.sqc.nuclearsq.apps.client;

import java.util.List;

import com.sqc.nuclearsq.apps.AuctionPayloads;
import com.sqc.nuclearsq.economy.Auctions;
import com.sqc.nuclearsq.economy.BankNet;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class AuctionMonitorScreen extends Screen {
    private static final int W = 300, H = 212, BTN_H = 14, ROW_H = 18, ROWS = 6, MARKET = 0, SELL = 1, PICKUP = 2;

    private AuctionPayloads.State state;
    private int tab, scroll, selected = -1, hours = 2;
    private EditBox bidBox, startBox, buyBox;

    public AuctionMonitorScreen(AuctionPayloads.State state) {
        super(Component.translatable("gui.nuclearsq.app_auction.title"));
        this.state = state;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.app_auction." + key, args);
    }

    public BlockPos pos() {
        return state.pos();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private boolean ready() {
        return state.loggedIn() && state.link() == BankNet.Link.ONLINE.ordinal();
    }

    private EditBox box(int x, int y, int w) {
        EditBox f = new EditBox(font, x + 3, y + 3, w - 6, 10, Component.empty());
        f.setBordered(false);
        f.setMaxLength(10);
        f.setFilter(s -> s.matches("\\d{0,10}"));
        f.setTextColor(TermKit.tcol(TermKit.GREEN));
        addRenderableWidget(f);
        return f;
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        bidBox = box(x + 10, y + 152, 90);
        startBox = box(x + 10, y + 76, 120);
        buyBox = box(x + 10, y + 106, 120);
        layout();
    }

    private void layout() {
        boolean r = ready();
        bidBox.visible = r && tab == MARKET && entry() != null && !entry().mine();
        startBox.visible = r && tab == SELL;
        buyBox.visible = r && tab == SELL;
        for (EditBox b : new EditBox[]{bidBox, startBox, buyBox}) {
            b.active = b.visible;
            if (!b.visible && getFocused() == b) setFocused(null);
        }
    }

    private AuctionPayloads.Entry entry() {
        for (AuctionPayloads.Entry e : state.entries()) if (e.id() == selected) return e;
        return null;
    }

    public void update(AuctionPayloads.State s) {
        state = s;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, s.entries().size() - ROWS)));
        AuctionPayloads.Entry e = entry();
        if (e == null) selected = -1;
        if (s.ok()) {
            if (e != null) bidBox.setValue(String.valueOf(e.minBid()));
            startBox.setValue("");
            buyBox.setValue("");
        }
        layout();
    }

    private static long num(EditBox b) {
        try {
            return b.getValue().isEmpty() ? 0 : Long.parseLong(b.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void send(int op, int id, long a, long b, int c) {
        PacketDistributor.sendToServer(new AuctionPayloads.Action(state.pos(), op, id, a, b, c));
    }

    private record Btn(int x, int y, int w, Component label, int color, boolean enabled, Runnable run) {}

    private Btn[] buttons() {
        int x = left(), y = top();
        if (!ready()) return new Btn[0];
        AuctionPayloads.Entry e = entry();
        ItemStack hand = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        return switch (tab) {
            case MARKET -> e == null ? new Btn[0] : e.mine()
                    ? new Btn[]{new Btn(x + 10, y + 152, 120, t("cancel"), TermKit.RED, e.bids() == 0, () -> send(AuctionPayloads.CANCEL, e.id(), 0, 0, 0))}
                    : e.buyout() > 0
                    ? new Btn[]{new Btn(x + 106, y + 152, 64, t("bid"), TermKit.GREEN, true, () -> send(AuctionPayloads.BID, e.id(), num(bidBox), 0, 0)),
                    new Btn(x + 176, y + 152, W - 186, t("buyout", e.buyout()), TermKit.CYAN, true, () -> send(AuctionPayloads.BUYOUT, e.id(), 0, 0, 0))}
                    : new Btn[]{new Btn(x + 106, y + 152, 64, t("bid"), TermKit.GREEN, true, () -> send(AuctionPayloads.BID, e.id(), num(bidBox), 0, 0))};
            case SELL -> new Btn[]{new Btn(x + 10, y + 170, W - 20, t("sell"), TermKit.GREEN, !hand.isEmpty() && num(startBox) > 0,
                    () -> send(AuctionPayloads.CREATE, 0, num(startBox), num(buyBox), Auctions.HOURS[hours]))};
            default -> new Btn[]{new Btn(x + 10, y + 170, W - 20, t("take"), TermKit.GREEN, !state.pickups().isEmpty(),
                    () -> send(AuctionPayloads.TAKE, 0, 0, 0, 0))};
        };
    }

    private Component[] tabs() {
        return new Component[]{t("tab.market", state.entries().size()), t("tab.sell"), t("tab.pickup", state.pickups().size())};
    }

    private Component[] hourLabels() {
        Component[] out = new Component[Auctions.HOURS.length];
        for (int i = 0; i < out.length; i++) out[i] = t("hours", Auctions.HOURS[i]);
        return out;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int seg(double mx, int x, int w, int n) {
        return Math.max(0, Math.min(n - 1, (int) ((mx - x) * n / w)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left(), y = top();
        if (button == 0) {
            if (TermKit.styleClick(mx, my)) return true;
            if (over(mx, my, x + 10, y + 22, W - 20, BTN_H)) {
                tab = seg(mx, x + 10, W - 20, 3);
                layout();
                return true;
            }
            for (Btn b : buttons()) {
                if (b.enabled && over(mx, my, b.x, b.y, b.w, BTN_H)) {
                    b.run.run();
                    return true;
                }
            }
            if (ready() && tab == MARKET) {
                for (int i = 0; i < ROWS && scroll + i < state.entries().size(); i++) {
                    if (over(mx, my, x + 8, y + 40 + i * ROW_H, W - 16, ROW_H)) {
                        AuctionPayloads.Entry e = state.entries().get(scroll + i);
                        selected = e.id();
                        bidBox.setValue(String.valueOf(e.minBid()));
                        layout();
                        return true;
                    }
                }
            }
            if (ready() && tab == SELL && over(mx, my, x + 10, y + 136, W - 20, BTN_H)) {
                hours = seg(mx, x + 10, W - 20, Auctions.HOURS.length);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab == MARKET) {
            scroll = Math.max(0, Math.min(Math.max(0, state.entries().size() - ROWS), scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    private void boxFrame(GuiGraphics g, EditBox b, int x, int y, int w) {
        if (!b.visible) return;
        g.fill(x, y, x + w, y + 16, TermKit.col(getFocused() == b ? TermKit.CYAN : TermKit.DIM));
        g.fill(x + 1, y + 1, x + w - 1, y + 15, TermKit.col(TermKit.SCREEN));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int x = left(), y = top();
        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8);
        boolean online = state.link() == BankNet.Link.ONLINE.ordinal();
        TermKit.header(g, font, title, online ? t("balance", state.balance()) : Component.translatable("gui.nuclearsq.app_login.offline"),
                x + 8, y + 7, W - 16, online ? TermKit.AMBER : TermKit.RED);
        TermKit.rule(g, x + 8, y + 18, W - 16, TermKit.DIM);
        int hover = over(mx, my, x + 10, y + 22, W - 20, BTN_H) ? seg(mx, x + 10, W - 20, 3) : -1;
        TermKit.segments(g, font, x + 10, y + 22, W - 20, BTN_H, tabs(), tab, TermKit.AMBER, hover);
        ItemStack tip = ItemStack.EMPTY;
        if (!online) {
            TermKit.wrap(g, font, Component.translatable("gui.nuclearsq.app_login.link." + BankNet.Link.values()[state.link()].id()),
                    x + 10, y + 46, W - 20, 5, TermKit.RED);
        } else if (!state.loggedIn()) {
            TermKit.wrap(g, font, t("login_first"), x + 10, y + 46, W - 20, 5, TermKit.AMBER);
        } else if (tab == MARKET) {
            tip = renderMarket(g, mx, my, x, y);
        } else if (tab == SELL) {
            tip = renderSell(g, mx, my, x, y);
        } else {
            tip = renderPickup(g, mx, my, x, y);
        }
        for (Btn b : buttons()) {
            TermKit.button(g, font, b.x, b.y, b.w, BTN_H, b.label, b.color, b.enabled, over(mx, my, b.x, b.y, b.w, BTN_H), false);
        }
        TermKit.rule(g, x + 8, y + H - 20, W - 16, TermKit.FAINT);
        Component foot = state.message().isEmpty() ? t("footer") : t("msg." + state.message());
        TermKit.fit(g, font, foot, x + 10, y + H - 14, W - 20, state.message().isEmpty() ? TermKit.DIM : state.ok() ? TermKit.GREEN : TermKit.AMBER);
        super.render(g, mx, my, pt);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, H + 8);
        if (!tip.isEmpty()) g.renderTooltip(font, tip, mx, my);
    }

    private ItemStack renderMarket(GuiGraphics g, int mx, int my, int x, int y) {
        ItemStack tip = ItemStack.EMPTY;
        List<AuctionPayloads.Entry> list = state.entries();
        if (list.isEmpty()) TermKit.text(g, font, t("empty"), x + 10, y + 46, TermKit.DIM);
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            AuctionPayloads.Entry e = list.get(scroll + i);
            int ry = y + 40 + i * ROW_H;
            boolean hv = over(mx, my, x + 8, ry, W - 16, ROW_H);
            if (e.id() == selected || hv) g.fill(x + 8, ry, x + W - 8, ry + ROW_H - 1, TermKit.col(e.id() == selected ? TermKit.FAINT : TermKit.BEZEL_HI));
            g.renderItem(e.stack(), x + 10, ry + 1);
            g.renderItemDecorations(font, e.stack(), x + 10, ry + 1);
            if (hv && mx < x + 28) tip = e.stack();
            TermKit.fit(g, font, e.stack().getHoverName(), x + 30, ry + 1, 140, e.mine() ? TermKit.CYAN : TermKit.GREEN);
            TermKit.fit(g, font, t(e.leading() ? "row.leading" : "row.seller", e.seller(), e.bids()), x + 30, ry + 10, 140,
                    e.leading() ? TermKit.GREEN : TermKit.DIM);
            TermKit.right(g, font, Component.literal(String.valueOf(e.price())), x + W - 50, ry + 1, TermKit.AMBER);
            if (e.buyout() > 0) TermKit.right(g, font, t("row.buyout", e.buyout()), x + W - 50, ry + 10, TermKit.DIM);
            TermKit.right(g, font, Component.literal(AuctionMonitorRenderer.left(e.left())), x + W - 12, ry + 5, TermKit.MID);
        }
        if (list.size() > ROWS) TermKit.vbar(g, x + W - 7, y + 40, 2, ROWS * ROW_H, (float) scroll / (list.size() - ROWS), TermKit.DIM);
        AuctionPayloads.Entry e = entry();
        if (e == null) {
            if (!list.isEmpty()) TermKit.text(g, font, t("pick"), x + 10, y + 156, TermKit.DIM);
        } else {
            boxFrame(g, bidBox, x + 10, y + 152, 90);
            TermKit.fit(g, font, e.mine() ? t(e.bids() == 0 ? "mine" : "mine_bids", e.bids()) : t("min", e.minBid()), x + 10, y + 172, W - 20, TermKit.MID);
        }
        return tip;
    }

    private ItemStack renderSell(GuiGraphics g, int mx, int my, int x, int y) {
        ItemStack hand = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        if (hand.isEmpty()) {
            TermKit.text(g, font, t("hand_empty"), x + 10, y + 48, TermKit.AMBER);
        } else {
            g.renderItem(hand, x + 10, y + 44);
            g.renderItemDecorations(font, hand, x + 10, y + 44);
            TermKit.fit(g, font, hand.getHoverName(), x + 30, y + 48, W - 40, TermKit.GREEN);
        }
        TermKit.text(g, font, t("field.start"), x + 10, y + 66, TermKit.MID);
        boxFrame(g, startBox, x + 10, y + 76, 120);
        TermKit.text(g, font, t("field.buyout"), x + 10, y + 96, TermKit.MID);
        boxFrame(g, buyBox, x + 10, y + 106, 120);
        TermKit.wrap(g, font, t("sell_hint", Auctions.MAX_PER_PLAYER), x + 140, y + 70, W - 150, 5, TermKit.DIM);
        TermKit.text(g, font, t("field.hours"), x + 10, y + 126, TermKit.MID);
        int hover = over(mx, my, x + 10, y + 136, W - 20, BTN_H) ? seg(mx, x + 10, W - 20, Auctions.HOURS.length) : -1;
        TermKit.segments(g, font, x + 10, y + 136, W - 20, BTN_H, hourLabels(), hours, TermKit.AMBER, hover);
        TermKit.fit(g, font, t("fee"), x + 10, y + 156, W - 20, TermKit.DIM);
        return over(mx, my, x + 10, y + 44, 16, 16) ? hand : ItemStack.EMPTY;
    }

    private ItemStack renderPickup(GuiGraphics g, int mx, int my, int x, int y) {
        ItemStack tip = ItemStack.EMPTY;
        List<ItemStack> list = state.pickups();
        if (list.isEmpty()) TermKit.text(g, font, t("pickup_empty"), x + 10, y + 46, TermKit.DIM);
        for (int i = 0; i < list.size() && i < 81; i++) {
            int ix = x + 12 + (i % 15) * 18, iy = y + 42 + (i / 15) * 18;
            g.renderItem(list.get(i), ix, iy);
            g.renderItemDecorations(font, list.get(i), ix, iy);
            if (over(mx, my, ix, iy, 16, 16)) tip = list.get(i);
        }
        if (state.elsewhere() > 0) TermKit.fit(g, font, t("elsewhere", state.elsewhere()), x + 10, y + 156, W - 20, TermKit.AMBER);
        return tip;
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

    public static void receive(AuctionPayloads.State s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AuctionMonitorScreen as && as.pos().equals(s.pos())) as.update(s);
        else if (s.message().isEmpty()) mc.setScreen(new AuctionMonitorScreen(s));
    }
}
