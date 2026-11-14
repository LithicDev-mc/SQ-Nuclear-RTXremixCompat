package com.sqc.nuclearsq.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.sqc.nuclearsq.NuclearSQ;

public final class Exchange {
    public static final int MAX_ORDERS_PER_PLAYER = 20, MAX_ORDERS_TOTAL = 3000, HISTORY = 300;
    public static final long MAX_QTY = 10_000, MAX_PRICE = 1_000_000_000L, MAX_STOCK = 1_000_000_000L;
    public static final int FEE_PERCENT = 2;
    public static final long MAX_WITHDRAW = 18L * 64;

    public enum Side {
        BUY, SELL;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        @Nullable
        public static Side parse(String s) {
            for (Side v : values()) if (v.id().equals(s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT))) return v;
            return null;
        }
    }

    private static final String[] GOOD_IDS = {
            "minecraft:coal", "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:gold_ingot", "minecraft:redstone",
            "minecraft:lapis_lazuli", "minecraft:quartz", "minecraft:diamond", "minecraft:emerald", "minecraft:glowstone_dust",
            "minecraft:gunpowder", "minecraft:obsidian",
            "nuclearsq:steel_sheet_sqc", "nuclearsq:lead_shielding_sqc", "nuclearsq:zirconium_ingot_sqc", "nuclearsq:uranium_oxide_sqc",
            "nuclearsq:iron_dust_sqc", "nuclearsq:copper_dust_sqc", "nuclearsq:gold_dust_sqc", "nuclearsq:copper_coil_sqc",
    };

    private static final java.util.Set<String> NOT_TRADABLE = java.util.Set.of(
            "nuclearsq:creative_energy_source_sqc", "nuclearsq:creative_fluid_source_sqc", "nuclearsq:spacer_sqc",
            "nuclearsq:header_manufacturing_sqc", "nuclearsq:header_chemistry_sqc", "nuclearsq:header_robotics_sqc", "nuclearsq:header_computing_sqc",
            "nuclearsq:header_energy_sqc", "nuclearsq:header_reactor_sqc", "nuclearsq:header_protection_sqc", "nuclearsq:header_weapons_sqc",
            "nuclearsq:radioactive_fallout_sqc", "nuclearsq:contaminated_rubble_sqc", "nuclearsq:reactor_detonator_sqc");

    public static List<String> goods() {
        List<String> out = new ArrayList<>();
        for (String id : GOOD_IDS) if (itemOf(id) != Items.AIR) out.add(id);
        List<String> own = new ArrayList<>();
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            if (rl.getNamespace().equals(NuclearSQ.MODID) && !out.contains(rl.toString()) && !NOT_TRADABLE.contains(rl.toString())) own.add(rl.toString());
        }
        java.util.Collections.sort(own);
        out.addAll(own);
        return out;
    }

    public static boolean isGood(@Nullable String id) {
        if (id == null) return false;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || rl.toString().equals(id) == false || itemOf(id) == Items.AIR) return false;
        if (rl.getNamespace().equals(NuclearSQ.MODID)) return !NOT_TRADABLE.contains(id);
        for (String g : GOOD_IDS) if (g.equals(id)) return true;
        return false;
    }

    public static Item itemOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
    }

    public static final class Order {
        public final int id;
        public final UUID owner;
        public final Side side;
        public final String good;
        public final long price, original, seq, created;
        public long qty;
        public long escrow;

        Order(int id, UUID owner, Side side, String good, long price, long qty, long seq, long now) {
            this.id = id;
            this.owner = owner;
            this.side = side;
            this.good = good;
            this.price = price;
            this.qty = qty;
            this.original = qty;
            this.seq = seq;
            this.created = now;
        }

        Order(int id, UUID owner, Side side, String good, long price, long qty, long original, long seq, long created, long escrow) {
            this.id = id;
            this.owner = owner;
            this.side = side;
            this.good = good;
            this.price = price;
            this.qty = qty;
            this.original = original;
            this.seq = seq;
            this.created = created;
            this.escrow = escrow;
        }
    }

    public record Trade(long time, long price, long qty) {}

    public record Info(long last, long high, long low, long changePermille, long volume, long bid, long ask) {}

    public record Placement(int id, long filled, long remaining) {}

    private final EconomyData d;
    private final Map<Integer, Order> orders = new LinkedHashMap<>();
    private final Map<String, ArrayDeque<Trade>> trades = new HashMap<>();
    private final Map<String, Long> goodsInflow = new HashMap<>();
    private long seq;

    Exchange(EconomyData d) {
        this.d = d;
    }

    public long stock(UUID me, String good) {
        return d.account(me).goods.getOrDefault(good, 0L);
    }

    private long held(UUID me, String good) {
        long n = stock(me, good);
        for (Order o : orders.values()) if (o.owner.equals(me) && o.good.equals(good)) n += o.qty;
        return n;
    }

    public void depositGoods(UUID me, Map<String, Long> goods, long now) {
        EconomyData.Account a = d.account(me);
        for (Map.Entry<String, Long> e : goods.entrySet()) {
            if (!isGood(e.getKey())) throw new BankException("unknown good '" + e.getKey() + "'");
            if (e.getValue() < 1 || e.getValue() > MAX_STOCK) throw new BankException("bad quantity");
            if (held(me, e.getKey()) + e.getValue() > MAX_STOCK) throw new BankException("too much of this good in storage");
        }
        for (Map.Entry<String, Long> e : goods.entrySet()) {
            a.goods.merge(e.getKey(), e.getValue(), Long::sum);
            goodsInflow.merge(e.getKey(), e.getValue(), Long::sum);
            d.log(now, "xstock in " + a.name + " " + e.getKey() + " x" + e.getValue());
        }
        d.setDirty();
    }

    public void withdrawGoods(UUID me, String good, long qty, long now) {
        if (!isGood(good)) throw new BankException("unknown good '" + good + "'");
        if (qty < 1 || qty > MAX_WITHDRAW) throw new BankException("quantity must be 1-" + MAX_WITHDRAW);
        EconomyData.Account a = d.account(me);
        long have = a.goods.getOrDefault(good, 0L);
        if (have < qty) throw new BankException("not enough in storage (have " + have + ")");
        if (have == qty) a.goods.remove(good);
        else a.goods.put(good, have - qty);
        goodsInflow.merge(good, -qty, Long::sum);
        d.log(now, "xstock out " + a.name + " " + good + " x" + qty);
        d.setDirty();
    }

    public static long fee(long cost) {
        return Math.addExact(Math.multiplyExact(cost, (long) FEE_PERCENT), 99L) / 100;
    }

    private int openOrders(UUID me) {
        int n = 0;
        for (Order o : orders.values()) if (o.owner.equals(me)) n++;
        return n;
    }

    public static long checkLimits(String good, long price, long qty) {
        if (!isGood(good)) throw new BankException("unknown good '" + good + "'");
        if (price < 1 || price > MAX_PRICE) throw new BankException("price must be 1-" + MAX_PRICE);
        if (qty < 1 || qty > MAX_QTY) throw new BankException("quantity must be 1-" + MAX_QTY);
        long value = Math.multiplyExact(price, qty);
        if (value > EconomyData.MAX_AMOUNT) throw new BankException("order value too large");
        return value;
    }

    public Placement place(UUID me, Side side, String good, long price, long qty, long now) {
        if (side == null) throw new BankException("side must be buy or sell");
        long value = checkLimits(good, price, qty);
        EconomyData.Account acc = d.account(me);
        if (orders.size() >= MAX_ORDERS_TOTAL) throw new BankException("the order book is full, try again later");
        if (openOrders(me) >= MAX_ORDERS_PER_PLAYER) throw new BankException("too many open orders (" + MAX_ORDERS_PER_PLAYER + ")");
        for (Order o : orders.values()) {
            if (!o.owner.equals(me) || !o.good.equals(good) || o.side == side) continue;
            if (side == Side.BUY ? o.price <= price : o.price >= price) {
                throw new BankException("this order would trade with your own order #" + o.id + " (cancel it first)");
            }
        }
        if (side == Side.BUY) {
            if (acc.balance < value) throw new BankException("not enough money (balance " + acc.balance + ", needed " + value + ")");
            if (held(me, good) + qty > MAX_STOCK) throw new BankException("too much of this good in storage");
        } else {
            long have = stock(me, good);
            if (have < qty) throw new BankException("not enough in storage (have " + have + ")");
        }

        int id = d.newId();
        Order order = new Order(id, me, side, good, price, qty, ++seq, now);
        if (side == Side.BUY) {
            acc.balance -= value;
            order.escrow = value;
        } else {
            long have = acc.goods.get(good);
            if (have == qty) acc.goods.remove(good);
            else acc.goods.put(good, have - qty);
        }
        d.log(now, "xorder #" + id + " " + side.id() + " " + acc.name + " " + good + " " + qty + " @ " + price);

        List<Order> candidates = new ArrayList<>();
        for (Order o : orders.values()) {
            if (!o.good.equals(good) || o.side == side || o.owner.equals(me)) continue;
            if (side == Side.BUY ? o.price <= price : o.price >= price) candidates.add(o);
        }
        Comparator<Order> byPrice = side == Side.BUY ? Comparator.comparingLong((Order o) -> o.price) : Comparator.comparingLong((Order o) -> -o.price);
        candidates.sort(byPrice.thenComparingLong(o -> o.seq));
        for (Order rest : candidates) {
            if (order.qty == 0) break;
            long q = Math.min(order.qty, rest.qty);
            Order buy = side == Side.BUY ? order : rest, sell = side == Side.BUY ? rest : order;
            settle(buy, sell, q, rest.price, now);
            if (rest.qty == 0) orders.remove(rest.id);
        }
        long filled = qty - order.qty;
        if (order.qty > 0) orders.put(id, order);
        d.setDirty();
        return new Placement(id, filled, order.qty);
    }

    private void settle(Order buy, Order sell, long q, long tp, long now) {
        EconomyData.Account buyer = d.account(buy.owner), seller = d.account(sell.owner);
        long cost = q * tp;
        long limitCost = q * buy.price;
        long refund = limitCost - cost;
        long fee = fee(cost);
        buy.escrow -= limitCost;
        buy.qty -= q;
        sell.qty -= q;
        credit(buyer, refund);
        credit(seller, cost - fee);
        d.feesBurned += fee;
        buyer.goods.merge(buy.good, q, Long::sum);
        ArrayDeque<Trade> hist = trades.computeIfAbsent(buy.good, k -> new ArrayDeque<>());
        hist.addLast(new Trade(now, tp, q));
        while (hist.size() > HISTORY) hist.removeFirst();
        d.log(now, "xtrade " + buy.good + " " + q + " @ " + tp + " buyer " + buyer.name + " seller " + seller.name + " fee " + fee);
    }

    private static void credit(EconomyData.Account a, long amount) {
        if (amount <= 0) return;
        long room = EconomyData.MAX_AMOUNT - a.balance;
        long toBalance = Math.max(0, Math.min(room, amount));
        a.balance += toBalance;
        a.proceeds = Math.addExact(a.proceeds, amount - toBalance);
    }

    public void cancel(UUID me, int id, long now) {
        Order o = orders.get(id);
        if (o == null) throw new BankException("no open order #" + id);
        if (!o.owner.equals(me)) throw new BankException("this is not your order");
        EconomyData.Account a = d.account(me);
        if (o.side == Side.BUY) {
            credit(a, o.escrow);
        } else {
            a.goods.merge(o.good, o.qty, Long::sum);
        }
        orders.remove(id);
        d.log(now, "xcancel #" + id + " " + a.name);
        d.setDirty();
    }

    public List<Order> ordersOf(UUID me) {
        List<Order> out = new ArrayList<>();
        for (Order o : orders.values()) if (o.owner.equals(me)) out.add(o);
        return out;
    }

    public int openOrderCount() {
        return orders.size();
    }

    public List<long[]> levels(String good, Side side, int max) {
        Comparator<Long> cmp = side == Side.BUY ? Comparator.<Long>reverseOrder() : Comparator.<Long>naturalOrder();
        TreeMap<Long, Long> m = new TreeMap<>(cmp);
        for (Order o : orders.values()) if (o.good.equals(good) && o.side == side) m.merge(o.price, o.qty, Long::sum);
        List<long[]> out = new ArrayList<>();
        for (Map.Entry<Long, Long> e : m.entrySet()) {
            if (out.size() >= max) break;
            out.add(new long[]{e.getKey(), e.getValue()});
        }
        return out;
    }

    public List<Trade> recent(String good, int max) {
        ArrayDeque<Trade> h = trades.get(good);
        if (h == null) return List.of();
        List<Trade> all = new ArrayList<>(h);
        return all.size() > max ? all.subList(all.size() - max, all.size()) : all;
    }

    public Info info(String good, long now) {
        ArrayDeque<Trade> h = trades.get(good);
        long last = 0, high = 0, low = Long.MAX_VALUE, open = 0, volume = 0;
        if (h != null && !h.isEmpty()) {
            last = h.peekLast().price();
            for (Trade t : h) {
                if (now - t.time() > EconomyData.DAY) continue;
                if (open == 0) open = t.price();
                high = Math.max(high, t.price());
                low = Math.min(low, t.price());
                volume += t.qty();
            }
        }
        if (open == 0) {
            open = last;
            high = last;
            low = last;
        }
        long change = open == 0 ? 0 : Math.multiplyExact(last - open, 1000L) / open;
        long bid = 0, ask = 0;
        for (Order o : orders.values()) {
            if (!o.good.equals(good)) continue;
            if (o.side == Side.BUY) bid = Math.max(bid, o.price);
            else ask = ask == 0 ? o.price : Math.min(ask, o.price);
        }
        return new Info(last, high, low, change, volume, bid, ask);
    }

    public record Ticker(String good, Info info, List<Long> history) {}

    public List<Ticker> ticker(long now, int points) {
        List<Ticker> out = new ArrayList<>();
        for (String g : goods()) {
            List<Long> h = new ArrayList<>();
            for (Trade t : recent(g, Math.max(1, Math.min(points, HISTORY)))) h.add(t.price());
            out.add(new Ticker(g, info(g, now), h));
        }
        return out;
    }

    long escrowTotal() {
        long sum = 0;
        for (Order o : orders.values()) if (o.side == Side.BUY) sum += o.escrow;
        return sum;
    }

    public boolean invariantHolds() {
        Map<String, Long> have = new HashMap<>();
        for (EconomyData.Account a : d.allAccounts()) {
            for (Map.Entry<String, Long> e : a.goods.entrySet()) {
                if (e.getValue() < 1) return false;
                have.merge(e.getKey(), e.getValue(), Long::sum);
            }
        }
        for (Order o : orders.values()) {
            if (o.qty < 1 || o.qty > o.original) return false;
            if (o.side == Side.SELL) have.merge(o.good, o.qty, Long::sum);
            else if (o.escrow != o.qty * o.price) return false;
        }
        for (Map.Entry<String, Long> e : goodsInflow.entrySet()) {
            if (have.getOrDefault(e.getKey(), 0L).longValue() != e.getValue().longValue()) return false;
            have.remove(e.getKey());
        }
        for (long v : have.values()) if (v != 0) return false;
        return true;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("seq", seq);
        ListTag ol = new ListTag();
        for (Order o : orders.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", o.id);
            c.putUUID("owner", o.owner);
            c.putByte("side", (byte) o.side.ordinal());
            c.putString("good", o.good);
            c.putLong("price", o.price);
            c.putLong("qty", o.qty);
            c.putLong("orig", o.original);
            c.putLong("seq", o.seq);
            c.putLong("cr", o.created);
            c.putLong("esc", o.escrow);
            ol.add(c);
        }
        t.put("orders", ol);
        CompoundTag tr = new CompoundTag();
        for (Map.Entry<String, ArrayDeque<Trade>> e : trades.entrySet()) {
            long[] a = new long[e.getValue().size() * 3];
            int i = 0;
            for (Trade x : e.getValue()) {
                a[i++] = x.time();
                a[i++] = x.price();
                a[i++] = x.qty();
            }
            tr.put(e.getKey(), new LongArrayTag(a));
        }
        t.put("trades", tr);
        CompoundTag gi = new CompoundTag();
        for (Map.Entry<String, Long> e : goodsInflow.entrySet()) gi.putLong(e.getKey(), e.getValue());
        t.put("inflow", gi);
        return t;
    }

    void load(CompoundTag t) {
        seq = t.getLong("seq");
        for (Tag e : t.getList("orders", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            Side[] sides = Side.values();
            Side side = sides[Math.max(0, Math.min(sides.length - 1, c.getByte("side")))];
            Order o = new Order(c.getInt("id"), c.getUUID("owner"), side, c.getString("good"), c.getLong("price"), c.getLong("qty"),
                    c.getLong("orig"), c.getLong("seq"), c.getLong("cr"), c.getLong("esc"));
            orders.put(o.id, o);
        }
        CompoundTag tr = t.getCompound("trades");
        for (String k : tr.getAllKeys()) {
            long[] a = tr.getLongArray(k);
            ArrayDeque<Trade> h = new ArrayDeque<>();
            for (int i = 0; i + 2 < a.length; i += 3) h.addLast(new Trade(a[i], a[i + 1], a[i + 2]));
            trades.put(k, h);
        }
        CompoundTag gi = t.getCompound("inflow");
        for (String k : gi.getAllKeys()) goodsInflow.put(k, gi.getLong(k));
    }
}
