package com.sqc.nuclearsq.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class Auctions {
    public static final int MAX_PER_PLAYER = 10, MAX_TOTAL = 500;
    public static final long MAX_PRICE = 1_000_000_000L, HOUR = 72_000L;
    public static final int[] HOURS = {1, 6, 12, 24, 48};

    public static final class Auction {
        public final int id;
        public final UUID seller;
        public final ItemStack stack;
        public final long start, buyout, end;
        public final GlobalPos origin;
        public long bid;
        @Nullable public UUID bidder;
        @Nullable public GlobalPos bidAt;
        public int bids;

        Auction(int id, UUID seller, ItemStack stack, long start, long buyout, long end, GlobalPos origin) {
            this.id = id;
            this.seller = seller;
            this.stack = stack;
            this.start = start;
            this.buyout = buyout;
            this.end = end;
            this.origin = origin;
        }

        public long minBid() {
            return bidder == null ? start : Math.min(MAX_PRICE, bid + Math.max(1, bid / 20));
        }
    }

    public record Pickup(UUID owner, GlobalPos at, ItemStack stack) {}

    public record Settled(Auction auction, @Nullable UUID winner, long price) {}

    private final EconomyData d;
    private final Map<Integer, Auction> open = new LinkedHashMap<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final Map<UUID, Integer> sold = new HashMap<>();
    private int next = 1;

    Auctions(EconomyData d) {
        this.d = d;
    }

    @Nullable
    public Auction get(int id) {
        return open.get(id);
    }

    public List<Auction> list() {
        List<Auction> out = new ArrayList<>(open.values());
        out.sort(Comparator.comparingLong((Auction a) -> a.end).thenComparingInt(a -> a.id));
        return out;
    }

    public int count() {
        return open.size();
    }

    public int soldBy(UUID id) {
        return sold.getOrDefault(id, 0);
    }

    public long escrowTotal() {
        long sum = 0;
        for (Auction a : open.values()) if (a.bidder != null) sum += a.bid;
        return sum;
    }

    private static void credit(EconomyData.Account a, long amount) {
        long nb = Math.addExact(a.balance, amount);
        if (nb > EconomyData.MAX_AMOUNT) throw new BankException("balance too large");
        a.balance = nb;
    }

    public Auction create(UUID seller, String name, ItemStack stack, long start, long buyout, int hours, GlobalPos at, long now) {
        if (stack.isEmpty()) throw new BankException("empty");
        if (start < 1 || start > MAX_PRICE) throw new BankException("price");
        if (buyout != 0 && (buyout < start || buyout > MAX_PRICE)) throw new BankException("buyout");
        boolean known = false;
        for (int h : HOURS) known |= h == hours;
        if (!known) throw new BankException("hours");
        if (open.size() >= MAX_TOTAL) throw new BankException("full");
        int mine = 0;
        for (Auction a : open.values()) if (a.seller.equals(seller)) mine++;
        if (mine >= MAX_PER_PLAYER) throw new BankException("limit");
        d.touch(seller, name);
        Auction a = new Auction(next++, seller, stack.copy(), start, buyout, now + hours * HOUR, at);
        open.put(a.id, a);
        d.log(now, "auction #" + a.id + " by " + name + " " + stack.getCount() + "x " + stack.getItem() + " start " + start + " buyout " + buyout);
        d.setDirty();
        return a;
    }

    @Nullable
    public Settled bid(UUID bidder, String name, int id, long amount, GlobalPos at, long now) {
        Auction a = open.get(id);
        if (a == null || now >= a.end) throw new BankException("gone");
        if (a.seller.equals(bidder)) throw new BankException("own");
        if (a.buyout > 0 && amount >= a.buyout) amount = a.buyout;
        if (amount < a.minBid() || amount > MAX_PRICE) throw new BankException("low");
        EconomyData.Account b = d.touch(bidder, name);
        long need = bidder.equals(a.bidder) ? amount - a.bid : amount;
        if (b.balance < need) throw new BankException("money");
        if (a.bidder != null && !bidder.equals(a.bidder)) credit(d.account(a.bidder), a.bid);
        b.balance -= need;
        a.bid = amount;
        a.bidder = bidder;
        a.bidAt = at;
        a.bids++;
        d.log(now, "auction #" + id + " bid " + name + " " + amount);
        d.setDirty();
        return a.buyout > 0 && amount >= a.buyout ? settle(a, now) : null;
    }

    public void cancel(UUID me, int id, long now) {
        Auction a = open.get(id);
        if (a == null) throw new BankException("gone");
        if (!a.seller.equals(me)) throw new BankException("not_own");
        if (a.bidder != null) throw new BankException("has_bids");
        open.remove(id);
        pickups.add(new Pickup(a.seller, a.origin, a.stack.copy()));
        d.log(now, "auction #" + id + " cancelled");
        d.setDirty();
    }

    private Settled settle(Auction a, long now) {
        open.remove(a.id);
        if (a.bidder == null) {
            pickups.add(new Pickup(a.seller, a.origin, a.stack.copy()));
            d.log(now, "auction #" + a.id + " expired");
        } else {
            long fee = Exchange.fee(a.bid);
            EconomyData.Account s = d.account(a.seller);
            long gain = Math.min(a.bid - fee, EconomyData.MAX_AMOUNT - s.balance);
            s.balance += gain;
            d.feesBurned += a.bid - gain;
            pickups.add(new Pickup(a.bidder, a.bidAt == null ? a.origin : a.bidAt, a.stack.copy()));
            sold.merge(a.seller, 1, Integer::sum);
            d.log(now, "auction #" + a.id + " sold to " + d.name(a.bidder) + " for " + a.bid + " fee " + fee);
        }
        d.setDirty();
        return new Settled(a, a.bidder, a.bid);
    }

    public List<Settled> tick(long now) {
        List<Settled> out = new ArrayList<>();
        for (Auction a : new ArrayList<>(open.values())) if (now >= a.end) out.add(settle(a, now));
        return out;
    }

    public List<ItemStack> pickupsAt(UUID me, GlobalPos at) {
        List<ItemStack> out = new ArrayList<>();
        for (Pickup p : pickups) if (p.owner.equals(me) && p.at.equals(at)) out.add(p.stack);
        return out;
    }

    public int pickupsElsewhere(UUID me, GlobalPos at) {
        int n = 0;
        for (Pickup p : pickups) if (p.owner.equals(me) && !p.at.equals(at)) n++;
        return n;
    }

    public List<ItemStack> take(UUID me, GlobalPos at, int max) {
        List<ItemStack> out = new ArrayList<>();
        for (Iterator<Pickup> it = pickups.iterator(); it.hasNext() && out.size() < max; ) {
            Pickup p = it.next();
            if (!p.owner.equals(me) || !p.at.equals(at)) continue;
            out.add(p.stack.copy());
            it.remove();
        }
        if (!out.isEmpty()) d.setDirty();
        return out;
    }

    public void putBack(UUID me, GlobalPos at, ItemStack stack) {
        if (stack.isEmpty()) return;
        pickups.add(new Pickup(me, at, stack.copy()));
        d.setDirty();
    }

    private static CompoundTag pos(GlobalPos g) {
        CompoundTag out = new CompoundTag();
        out.putString("dim", g.dimension().location().toString());
        out.putLong("p", g.pos().asLong());
        return out;
    }

    private static GlobalPos pos(CompoundTag t) {
        ResourceLocation rl = ResourceLocation.tryParse(t.getString("dim"));
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, rl == null ? ResourceLocation.withDefaultNamespace("overworld") : rl),
                BlockPos.of(t.getLong("p")));
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("next", next);
        ListTag list = new ListTag();
        for (Auction a : open.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", a.id);
            t.putUUID("seller", a.seller);
            t.put("stack", a.stack.save(registries));
            t.putLong("start", a.start);
            t.putLong("buyout", a.buyout);
            t.putLong("end", a.end);
            t.put("origin", pos(a.origin));
            t.putLong("bid", a.bid);
            t.putInt("bids", a.bids);
            if (a.bidder != null) t.putUUID("bidder", a.bidder);
            if (a.bidAt != null) t.put("bidAt", pos(a.bidAt));
            list.add(t);
        }
        tag.put("open", list);
        ListTag pick = new ListTag();
        for (Pickup p : pickups) {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", p.owner);
            t.put("at", pos(p.at));
            t.put("stack", p.stack.save(registries));
            pick.add(t);
        }
        tag.put("pickups", pick);
        CompoundTag s = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : sold.entrySet()) s.putInt(e.getKey().toString(), e.getValue());
        tag.put("sold", s);
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        next = Math.max(1, tag.getInt("next"));
        for (Tag e : tag.getList("open", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            ItemStack s = ItemStack.parse(registries, t.get("stack")).orElse(ItemStack.EMPTY);
            if (s.isEmpty()) continue;
            Auction a = new Auction(t.getInt("id"), t.getUUID("seller"), s, t.getLong("start"), t.getLong("buyout"), t.getLong("end"),
                    pos(t.getCompound("origin")));
            a.bid = t.getLong("bid");
            a.bids = t.getInt("bids");
            if (t.hasUUID("bidder")) a.bidder = t.getUUID("bidder");
            if (t.contains("bidAt")) a.bidAt = pos(t.getCompound("bidAt"));
            open.put(a.id, a);
        }
        for (Tag e : tag.getList("pickups", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            ItemStack.parse(registries, t.get("stack")).ifPresent(s -> pickups.add(new Pickup(t.getUUID("owner"), pos(t.getCompound("at")), s)));
        }
        CompoundTag s = tag.getCompound("sold");
        for (String k : s.getAllKeys()) {
            try {
                sold.put(UUID.fromString(k), s.getInt(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
