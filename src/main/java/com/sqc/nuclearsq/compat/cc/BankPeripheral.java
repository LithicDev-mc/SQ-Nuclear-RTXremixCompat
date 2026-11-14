package com.sqc.nuclearsq.compat.cc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.sqc.nuclearsq.economy.BankException;
import com.sqc.nuclearsq.economy.BankTerminalBlockEntity;
import com.sqc.nuclearsq.economy.ChatData;
import com.sqc.nuclearsq.economy.Coins;
import com.sqc.nuclearsq.economy.EconomyData;
import com.sqc.nuclearsq.economy.Exchange;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BankPeripheral implements IPeripheral {
    private final Level level;
    private final BlockPos pos;

    public BankPeripheral(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos;
    }

    @Override
    public String getType() {
        return "sqc_bank";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof BankPeripheral p && p.level == level && p.pos.equals(pos);
    }

    @Override
    public Object getTarget() {
        return level.getBlockEntity(pos);
    }

    private BankTerminalBlockEntity terminal() throws LuaException {
        if (level.getBlockEntity(pos) instanceof BankTerminalBlockEntity t) return t;
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(pos.relative(d)) instanceof BankTerminalBlockEntity t) return t;
        }
        throw new LuaException("no bank terminal next to this modem");
    }

    private <T> T run(Supplier<T> f) throws LuaException {
        try {
            return f.get();
        } catch (BankException e) {
            throw new LuaException(e.getMessage());
        }
    }

    private static Component act(String what, Object... args) {
        return Component.translatable("message.nuclearsq.bank.act." + what, args);
    }

    private static void positive(long amount) {
        if (amount < 1) throw new BankException("amount must be at least 1");
    }

    private static long now(BankTerminalBlockEntity t) {
        return t.getLevel().getGameTime();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> status() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        Map<String, Object> m = new HashMap<>();
        com.sqc.nuclearsq.economy.BankNet.Link link = com.sqc.nuclearsq.economy.BankNet.link(t.getLevel(), t.getBlockPos());
        boolean online = link == com.sqc.nuclearsq.economy.BankNet.Link.ONLINE;
        ServerPlayer p = t.currentUser();
        m.put("online", online);
        m.put("link", link.id());
        m.put("loggedIn", p != null);
        m.put("feePercent", EconomyData.FEE_PERCENT);
        m.put("slots", BankTerminalBlockEntity.SLOTS);
        if (p != null) {
            m.put("user", EconomyData.clean(p.getGameProfile().getName(), 32));
            if (online) {
                try {
                    EconomyData.Account a = t.data().account(p.getUUID());
                    m.put("balance", a.balance);
                    m.put("proceeds", a.proceeds);
                    m.put("waiting", a.pickup.size());
                } catch (BankException ignored) {
                }
            }
        }
        return m;
    }

    @LuaFunction(mainThread = true)
    public final long balance() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> t.data().account(t.requireUser().getUUID()).balance);
    }

    @LuaFunction(mainThread = true)
    public final void logout() throws LuaException {
        terminal().logout();
    }

    @LuaFunction(mainThread = true)
    public final List<String> accounts(IArguments args) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        String prefix = args.optString(0, "");
        return run(() -> {
            t.requireUser();
            return new ArrayList<>(t.data().names(prefix, 50));
        });
    }

    @LuaFunction(mainThread = true)
    public final int chatSend(IArguments args) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        String to = args.optString(0, "");
        String text = args.getString(1);
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            UUID target = null;
            if (!to.isBlank()) {
                EconomyData.Account b = d.byName(to);
                if (b == null) throw new BankException("no account named '" + EconomyData.clean(to, 32) + "' (the player must have used a bank terminal once)");
                if (b.id.equals(me)) throw new BankException("cannot chat with yourself");
                target = b.id;
            }
            return ChatData.get(t.getLevel().getServer()).send(me, target, text, now(t));
        });
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> chatFetch(IArguments args) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        int after = args.optInt(0, 0);
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            List<Map<String, Object>> out = new ArrayList<>();
            for (ChatData.Msg m : ChatData.get(t.getLevel().getServer()).since(me, after)) {
                Map<String, Object> o = new HashMap<>();
                o.put("id", m.id());
                o.put("from", nameOf(d, m.from()));
                o.put("to", m.to() == null ? "" : nameOf(d, m.to()));
                o.put("text", m.text());
                o.put("time", m.time());
                out.add(o);
            }
            return out;
        });
    }

    private static String nameOf(EconomyData d, UUID id) {
        try {
            return d.account(id).name;
        } catch (BankException e) {
            return "?";
        }
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> inventory() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(t::requireUser);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < BankTerminalBlockEntity.SLOTS; i++) {
            ItemStack s = t.items().get(i);
            if (s.isEmpty()) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("slot", i + 1);
            m.put("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            m.put("name", EconomyData.clean(s.getHoverName().getString(), 64));
            m.put("count", s.getCount());
            m.put("coin", Coins.value(s));
            out.add(m);
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public final long deposit() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(t::deposit);
    }

    @LuaFunction(mainThread = true)
    public final void withdraw(long amount) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            positive(amount);
            t.requireConfirmation("withdraw|" + amount, act("withdraw", amount));
            t.withdraw(amount);
            return null;
        });
    }

    @LuaFunction(mainThread = true)
    public final void transfer(String name, long amount) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            positive(amount);
            t.requireUser();
            t.requireConfirmation("transfer|" + name.trim().toLowerCase() + "|" + amount, act("transfer", amount, name.trim()));
            t.data().transfer(t.requireUser().getUUID(), name, amount, now(t));
            return null;
        });
    }

    @LuaFunction(mainThread = true)
    public final int requestMoney(String name, long amount, Optional<String> noteArg) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        String note = noteArg.orElse("");
        return run(() -> t.data().requestMoney(t.requireUser().getUUID(), name, amount, note, now(t)).id);
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> requests() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            List<Map<String, Object>> in = new ArrayList<>(), out = new ArrayList<>();
            for (EconomyData.Request r : d.requestsOf(me)) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.id);
                m.put("from", d.name(r.from));
                m.put("to", d.name(r.to));
                m.put("amount", r.amount);
                m.put("note", r.note);
                m.put("status", r.status.name().toLowerCase());
                (r.to.equals(me) ? in : out).add(m);
            }
            Map<String, Object> res = new HashMap<>();
            res.put("incoming", in);
            res.put("outgoing", out);
            return res;
        });
    }

    @LuaFunction(mainThread = true)
    public final void answerRequest(int id, boolean accept) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            EconomyData d = t.data();
            UUID me = t.requireUser().getUUID();
            EconomyData.Request r = d.request(id);
            if (accept && r != null && r.to.equals(me) && r.status == EconomyData.Status.OPEN) {
                t.requireConfirmation("accept_request|" + id + "|" + r.amount, act("accept_request", r.amount, d.name(r.from)));
            }
            d.answerRequest(me, id, accept, now(t));
            return null;
        });
    }

    @LuaFunction(mainThread = true)
    public final void cancelRequest(int id) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            t.data().cancelRequest(t.requireUser().getUUID(), id, now(t));
            return null;
        });
    }

    @LuaFunction(mainThread = true)
    public final int offerLoan(String name, long amount, int interest, int days) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            positive(amount);
            t.requireUser();
            t.requireConfirmation("offer_loan|" + name.trim().toLowerCase() + "|" + amount + "|" + interest + "|" + days,
                    act("offer_loan", amount, name.trim(), interest, days));
            return t.data().offerLoan(t.requireUser().getUUID(), name, amount, interest, days, now(t)).id;
        });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> loans() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            long now = now(t);
            List<Map<String, Object>> lent = new ArrayList<>(), borrowed = new ArrayList<>();
            for (EconomyData.Loan l : d.loansOf(me)) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", l.id);
                m.put("lender", d.name(l.lender));
                m.put("borrower", d.name(l.borrower));
                m.put("amount", l.amount);
                m.put("interest", l.interest);
                m.put("days", l.days);
                m.put("repay", l.repay());
                m.put("status", l.status.name().toLowerCase());
                m.put("overdue", l.overdue(now));
                if (l.status == EconomyData.Status.ACTIVE) m.put("daysLeft", Math.floorDiv(l.due - now, EconomyData.DAY));
                (l.lender.equals(me) ? lent : borrowed).add(m);
            }
            Map<String, Object> res = new HashMap<>();
            res.put("lent", lent);
            res.put("borrowed", borrowed);
            return res;
        });
    }

    @LuaFunction(mainThread = true)
    public final void answerLoan(int id, boolean accept) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            EconomyData d = t.data();
            UUID me = t.requireUser().getUUID();
            EconomyData.Loan l = d.loan(id);
            if (accept && l != null && l.borrower.equals(me) && l.status == EconomyData.Status.OPEN) {
                t.requireConfirmation("accept_loan|" + id + "|" + l.amount + "|" + l.repay(), act("accept_loan", l.amount, l.repay()));
            }
            d.answerLoan(me, id, accept, now(t));
            return null;
        });
    }

    @LuaFunction(mainThread = true)
    public final void cancelLoan(int id) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            t.data().cancelLoan(t.requireUser().getUUID(), id, now(t));
            return null;
        });
    }

    @LuaFunction(mainThread = true)
    public final void repayLoan(int id) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            EconomyData d = t.data();
            UUID me = t.requireUser().getUUID();
            EconomyData.Loan l = d.loan(id);
            if (l != null && l.borrower.equals(me) && l.status == EconomyData.Status.ACTIVE) {
                t.requireConfirmation("repay|" + id + "|" + l.repay(), act("repay", l.repay(), d.name(l.lender)));
            }
            d.repayLoan(me, id, now(t));
            return null;
        });
    }

    private static Map<String, Object> offerMap(EconomyData d, EconomyData.Offer o, UUID me) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.id);
        m.put("seller", EconomyData.clean(d.name(o.seller), 32));
        m.put("item", BuiltInRegistries.ITEM.getKey(o.stack.getItem()).toString());
        m.put("name", EconomyData.clean(o.stack.getHoverName().getString(), 64));
        m.put("count", o.stack.getCount());
        m.put("price", o.price);
        m.put("fee", o.fee());
        m.put("own", o.seller.equals(me));
        return m;
    }

    @LuaFunction(mainThread = true)
    public final int listItem(int slot, long price) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            positive(price);
            t.requireUser();
            if (slot >= 1 && slot <= BankTerminalBlockEntity.SLOTS && !t.items().get(slot - 1).isEmpty()) {
                ItemStack s = t.items().get(slot - 1);
                t.requireConfirmation("list|" + slot + "|" + price + "|" + BuiltInRegistries.ITEM.getKey(s.getItem()) + "|" + s.getCount(),
                        act("list", s.getCount(), EconomyData.clean(s.getHoverName().getString(), 40), price));
            }
            return t.listSlot(slot, price);
        });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> search(IArguments args) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        String q = args.optString(0, "");
        int page = Math.max(1, args.optInt(1, 1));
        int size = Math.max(1, Math.min(50, args.optInt(2, 10)));
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            List<EconomyData.Offer> all = d.search(q);
            int pages = Math.max(1, (all.size() + size - 1) / size);
            int p = Math.min(page, pages);
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = (p - 1) * size; i < Math.min(all.size(), p * size); i++) out.add(offerMap(d, all.get(i), me));
            Map<String, Object> res = new HashMap<>();
            res.put("offers", out);
            res.put("page", p);
            res.put("pages", pages);
            res.put("total", all.size());
            return res;
        });
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> myOffers() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            List<Map<String, Object>> out = new ArrayList<>();
            for (EconomyData.Offer o : d.offersOf(me)) out.add(offerMap(d, o, me));
            return out;
        });
    }

    @LuaFunction(mainThread = true)
    public final void buy(int id) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            EconomyData d = t.data();
            UUID me = t.requireUser().getUUID();
            EconomyData.Offer o = d.offer(id);
            if (o != null && o.status == EconomyData.Status.OPEN && !o.seller.equals(me)) {
                t.requireConfirmation("buy|" + id + "|" + o.price, act("buy", o.stack.getCount(),
                        EconomyData.clean(o.stack.getHoverName().getString(), 40), o.price));
            }
            d.buy(me, id, now(t));
            return null;
        });
    }

    @LuaFunction(mainThread = true)
    public final void cancelOffer(int id) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            t.data().cancelOffer(t.requireUser().getUUID(), id, now(t));
            return null;
        });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> pending() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            List<Map<String, Object>> items = new ArrayList<>();
            for (ItemStack s : d.pickup(me)) {
                Map<String, Object> m = new HashMap<>();
                m.put("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                m.put("name", EconomyData.clean(s.getHoverName().getString(), 64));
                m.put("count", s.getCount());
                items.add(m);
            }
            Map<String, Object> res = new HashMap<>();
            res.put("proceeds", d.account(me).proceeds);
            res.put("items", items);
            return res;
        });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> claim() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            int waiting = 0;
            for (ItemStack s : t.data().pickup(t.requireUser().getUUID())) waiting += s.getCount();
            if (waiting > 0) t.requireConfirmation("claim|" + waiting, act("claim", waiting));
            BankTerminalBlockEntity.Claimed c = t.claim();
            Map<String, Object> m = new HashMap<>();
            m.put("money", c.money());
            m.put("moved", c.moved());
            m.put("waiting", c.waiting());
            return m;
        });
    }

    private static String goodName(String good) {
        return EconomyData.clean(new ItemStack(Exchange.itemOf(good)).getHoverName().getString(), 40);
    }

    private static Map<String, Object> marketMap(EconomyData d, UUID me, String good, long now) {
        Exchange.Info i = d.exchange().info(good, now);
        Map<String, Object> m = new HashMap<>();
        m.put("item", good);
        m.put("name", goodName(good));
        m.put("last", i.last());
        m.put("high", i.high());
        m.put("low", i.low());
        m.put("changePermille", i.changePermille());
        m.put("volume", i.volume());
        m.put("bid", i.bid());
        m.put("ask", i.ask());
        m.put("stock", d.exchange().stock(me, good));
        return m;
    }

    private static List<Map<String, Object>> levelList(List<long[]> levels) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (long[] l : levels) {
            Map<String, Object> m = new HashMap<>();
            m.put("price", l[0]);
            m.put("qty", l[1]);
            out.add(m);
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> exchangeGoods() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            List<Map<String, Object>> out = new ArrayList<>();
            for (String g : Exchange.goods()) out.add(marketMap(d, me, g, now(t)));
            return out;
        });
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> exchangeTicker() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            EconomyData d = t.tickerData();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Exchange.Ticker row : d.exchange().ticker(now(t), 40)) {
                Exchange.Info i = row.info();
                Map<String, Object> m = new HashMap<>();
                m.put("item", row.good());
                m.put("name", goodName(row.good()));
                m.put("last", i.last());
                m.put("high", i.high());
                m.put("low", i.low());
                m.put("changePermille", i.changePermille());
                m.put("volume", i.volume());
                m.put("bid", i.bid());
                m.put("ask", i.ask());
                m.put("history", new ArrayList<>(row.history()));
                out.add(m);
            }
            return out;
        });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> exchangeBook(String item) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            if (!Exchange.isGood(item)) throw new BankException("unknown good '" + EconomyData.clean(item, 40) + "'");
            Map<String, Object> m = marketMap(d, me, item, now(t));
            m.put("asks", levelList(d.exchange().levels(item, Exchange.Side.SELL, 8)));
            m.put("bids", levelList(d.exchange().levels(item, Exchange.Side.BUY, 8)));
            List<Long> history = new ArrayList<>();
            for (Exchange.Trade tr : d.exchange().recent(item, 120)) history.add(tr.price());
            m.put("history", history);
            return m;
        });
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> exchangeOrders() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Exchange.Order o : t.data().exchange().ordersOf(me)) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", o.id);
                m.put("side", o.side.id());
                m.put("item", o.good);
                m.put("name", goodName(o.good));
                m.put("price", o.price);
                m.put("qty", o.qty);
                m.put("original", o.original);
                out.add(m);
            }
            return out;
        });
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> exchangeStock() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            List<Map<String, Object>> out = new ArrayList<>();
            for (String g : Exchange.goods()) {
                long n = d.exchange().stock(me, g);
                if (n < 1) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("item", g);
                m.put("name", goodName(g));
                m.put("count", n);
                out.add(m);
            }
            return out;
        });
    }

    @LuaFunction(mainThread = true)
    public final long exchangeDeposit() throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(t::exchangeDeposit);
    }

    @LuaFunction(mainThread = true)
    public final void exchangeWithdraw(String item, long qty) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            if (!Exchange.isGood(item)) throw new BankException("unknown good '" + EconomyData.clean(item, 40) + "'");
            t.requireConfirmation("xwithdraw|" + item + "|" + qty, act("xwithdraw", qty, goodName(item)));
            t.exchangeWithdraw(item, qty);
            return null;
        });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> exchangePlace(String sideName, String item, long price, long qty) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        return run(() -> {
            Exchange.Side side = Exchange.Side.parse(sideName);
            if (side == null) throw new BankException("side must be buy or sell");
            long value = Exchange.checkLimits(item, price, qty);
            UUID me = t.requireUser().getUUID();
            EconomyData d = t.data();
            t.requireConfirmation("xorder|" + side.id() + "|" + item + "|" + price + "|" + qty,
                    side == Exchange.Side.BUY ? act("order_buy", qty, goodName(item), price, value) : act("order_sell", qty, goodName(item), price));
            Exchange.Placement p = d.exchange().place(me, side, item, price, qty, now(t));
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.id());
            m.put("filled", p.filled());
            m.put("remaining", p.remaining());
            return m;
        });
    }

    @LuaFunction(mainThread = true)
    public final void exchangeCancel(int id) throws LuaException {
        BankTerminalBlockEntity t = terminal();
        run(() -> {
            t.data().exchange().cancel(t.requireUser().getUUID(), id, now(t));
            return null;
        });
    }
}
