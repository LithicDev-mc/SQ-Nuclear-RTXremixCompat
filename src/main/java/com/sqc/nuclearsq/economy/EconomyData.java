package com.sqc.nuclearsq.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

public final class EconomyData extends SavedData {
    private static final String KEY = "nuclearsq_economy";
    private static final SavedData.Factory<EconomyData> FACTORY = new SavedData.Factory<>(EconomyData::new, EconomyData::load, null);

    public static final long MAX_AMOUNT = 1_000_000_000_000L;
    public static final int FEE_PERCENT = 5;
    public static final int MAX_INTEREST = 100, MAX_DAYS = 60;
    public static final long DAY = 24000L;
    private static final long KEEP = 7 * DAY;
    private static final int MAX_OPEN_PER_PLAYER = 50;

    public enum Status { OPEN, ACTIVE, DONE, DECLINED, CANCELLED }

    public static final class Account {
        public final UUID id;
        public String name;
        public long balance;
        public long proceeds;
        public final List<ItemStack> pickup = new ArrayList<>();
        public final Map<String, Long> goods = new java.util.HashMap<>();

        Account(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Request {
        public final int id;
        public final UUID from, to;
        public final long amount;
        public final String note;
        public Status status = Status.OPEN;
        public long changed, created;

        Request(int id, UUID from, UUID to, long amount, String note, long now) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.amount = amount;
            this.note = note;
            this.changed = now;
            this.created = now;
        }
    }

    public static final class Loan {
        public final int id;
        public final UUID lender, borrower;
        public final long amount;
        public final int interest, days;
        public Status status = Status.OPEN;
        public long due, changed, created;

        Loan(int id, UUID lender, UUID borrower, long amount, int interest, int days, long now) {
            this.id = id;
            this.lender = lender;
            this.borrower = borrower;
            this.amount = amount;
            this.interest = interest;
            this.days = days;
            this.changed = now;
            this.created = now;
        }

        public long repay() {
            return Math.addExact(amount, Math.addExact(Math.multiplyExact(amount, (long) interest), 99L) / 100);
        }

        public boolean overdue(long now) {
            return status == Status.ACTIVE && now > due;
        }
    }

    public static final class Offer {
        public final int id;
        public final UUID seller;
        public final ItemStack stack;
        public final long price;
        public Status status = Status.OPEN;
        public long changed;

        Offer(int id, UUID seller, ItemStack stack, long price, long now) {
            this.id = id;
            this.seller = seller;
            this.stack = stack;
            this.price = price;
            this.changed = now;
        }

        public long fee() {
            return Math.addExact(Math.multiplyExact(price, (long) FEE_PERCENT), 99L) / 100;
        }
    }

    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    private final Map<Integer, Request> requests = new LinkedHashMap<>();
    private final Map<Integer, Loan> loans = new LinkedHashMap<>();
    private final Map<Integer, Offer> offers = new LinkedHashMap<>();
    private int nextId = 1;
    private long inflow;
    long feesBurned;
    private final Exchange exchange = new Exchange(this);
    private final java.util.ArrayDeque<String> audit = new java.util.ArrayDeque<>();
    private final Map<UUID, long[]> rate = new java.util.HashMap<>();

    public static final int AUDIT_MAX = 500;
    public static final int RATE_MAX = 40;
    public static final long RATE_WINDOW = 20;
    public static final long EXPIRY = 3 * DAY;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static EconomyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    public Account touch(UUID id, String name) {
        Account a = accounts.computeIfAbsent(id, k -> new Account(id, name));
        if (!a.name.equals(name)) a.name = name;
        setDirty();
        return a;
    }

    void log(long now, String text) {
        String line = "t=" + now + " " + text;
        audit.addLast(line);
        while (audit.size() > AUDIT_MAX) audit.removeFirst();
        LOGGER.info("[bank] {}", line);
    }

    public List<String> auditLog() {
        return new ArrayList<>(audit);
    }

    public void throttle(UUID who, long now) {
        long[] w = rate.computeIfAbsent(who, k -> new long[]{now, 0});
        if (now - w[0] >= RATE_WINDOW || now < w[0]) {
            w[0] = now;
            w[1] = 0;
        }
        if (++w[1] > RATE_MAX) throw new BankException("too many requests, slow down");
    }

    public boolean invariantHolds() {
        long sum = feesBurned;
        for (Account a : accounts.values()) sum += a.balance + a.proceeds;
        for (Loan l : loans.values()) if (l.status == Status.OPEN) sum += l.amount;
        sum += exchange.escrowTotal();
        return sum == inflow && exchange.invariantHolds();
    }

    public Exchange exchange() {
        return exchange;
    }

    int newId() {
        return nextId++;
    }

    java.util.Collection<Account> allAccounts() {
        return accounts.values();
    }

    public long inflow() {
        return inflow;
    }

    @Nullable
    public Request request(int id) {
        return requests.get(id);
    }

    @Nullable
    public Loan loan(int id) {
        return loans.get(id);
    }

    @Nullable
    public Offer offer(int id) {
        return offers.get(id);
    }

    public int accountCount() {
        return accounts.size();
    }

    public int openOfferCount() {
        int n = 0;
        for (Offer o : offers.values()) if (o.status == Status.OPEN) n++;
        return n;
    }

    public Account account(UUID id) {
        Account a = accounts.get(id);
        if (a == null) throw new BankException("unknown account");
        return a;
    }

    @Nullable
    public Account byName(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        Account found = null;
        for (Account a : accounts.values()) {
            if (!a.name.toLowerCase(Locale.ROOT).equals(n)) continue;
            if (found != null) throw new BankException("the name '" + name + "' belongs to more than one account");
            found = a;
        }
        return found;
    }

    private Account target(String name) {
        Account a = byName(name);
        if (a == null) throw new BankException("no account named '" + name + "' (the player must have used a bank terminal once)");
        return a;
    }

    public List<String> names(String prefix, int max) {
        String p = prefix.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Account a : accounts.values()) if (a.name.toLowerCase(Locale.ROOT).startsWith(p)) out.add(a.name);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out.size() > max ? out.subList(0, max) : out;
    }

    private static void positive(long amount) {
        if (amount < 1) throw new BankException("amount must be at least 1");
        if (amount > MAX_AMOUNT) throw new BankException("amount too large");
    }

    private static void credit(Account a, long amount) {
        long nb = Math.addExact(a.balance, amount);
        if (nb > MAX_AMOUNT) throw new BankException("the balance of " + a.name + " would be too large");
        a.balance = nb;
    }

    private static void debit(Account a, long amount) {
        if (a.balance < amount) throw new BankException("not enough money (balance " + a.balance + ", needed " + amount + ")");
        a.balance -= amount;
    }

    public void deposit(UUID who, long amount, long now) {
        positive(amount);
        credit(account(who), amount);
        inflow += amount;
        log(now, "deposit " + name(who) + " " + amount);
        setDirty();
    }

    public void withdraw(UUID who, long amount, long now) {
        positive(amount);
        debit(account(who), amount);
        inflow -= amount;
        log(now, "withdraw " + name(who) + " " + amount);
        setDirty();
    }

    public void transfer(UUID from, String toName, long amount, long now) {
        positive(amount);
        Account a = account(from), b = target(toName);
        if (a == b) throw new BankException("cannot send money to yourself");
        if (b.balance + amount > MAX_AMOUNT) throw new BankException("the balance of " + b.name + " would be too large");
        debit(a, amount);
        credit(b, amount);
        log(now, "transfer " + a.name + " -> " + b.name + " " + amount);
        setDirty();
    }

    public static String clean(@Nullable String s, int max) {
        s = s == null ? "" : s.replaceAll("[\\p{Cntrl}\\p{Cf}\\u2028\\u2029]", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String note(String s) {
        return clean(s, 40);
    }

    private int openCount(UUID who) {
        int n = 0;
        for (Request r : requests.values()) if (r.from.equals(who) && r.status == Status.OPEN) n++;
        for (Loan l : loans.values()) if (l.lender.equals(who) && l.status == Status.OPEN) n++;
        for (Offer o : offers.values()) if (o.seller.equals(who) && o.status == Status.OPEN) n++;
        return n;
    }

    private void checkOpenLimit(UUID who) {
        if (openCount(who) >= MAX_OPEN_PER_PLAYER) throw new BankException("too many open entries (" + MAX_OPEN_PER_PLAYER + ")");
    }

    public Request requestMoney(UUID from, String toName, long amount, @Nullable String note, long now) {
        positive(amount);
        account(from);
        Account to = target(toName);
        if (to.id.equals(from)) throw new BankException("cannot ask yourself for money");
        checkOpenLimit(from);
        Request r = new Request(nextId++, from, to.id, amount, note(note), now);
        requests.put(r.id, r);
        setDirty();
        return r;
    }

    public void answerRequest(UUID me, int id, boolean accept, long now) {
        Request r = requests.get(id);
        if (r == null || r.status != Status.OPEN) throw new BankException("no open request #" + id);
        if (!r.to.equals(me)) throw new BankException("this request is not addressed to you");
        if (now - r.created > EXPIRY) {
            r.status = Status.CANCELLED;
            r.changed = now;
            setDirty();
            throw new BankException("request #" + id + " has expired");
        }
        if (accept) {
            Account payer = account(me), payee = account(r.from);
            if (payee.balance + r.amount > MAX_AMOUNT) throw new BankException("the balance of " + payee.name + " would be too large");
            debit(payer, r.amount);
            credit(payee, r.amount);
            r.status = Status.DONE;
            log(now, "request #" + id + " paid " + payer.name + " -> " + payee.name + " " + r.amount);
        } else {
            r.status = Status.DECLINED;
        }
        r.changed = now;
        setDirty();
    }

    public void cancelRequest(UUID me, int id, long now) {
        Request r = requests.get(id);
        if (r == null || r.status != Status.OPEN) throw new BankException("no open request #" + id);
        if (!r.from.equals(me)) throw new BankException("this is not your request");
        r.status = Status.CANCELLED;
        r.changed = now;
        setDirty();
    }

    public List<Request> requestsOf(UUID me) {
        List<Request> out = new ArrayList<>();
        for (Request r : requests.values()) if (r.from.equals(me) || r.to.equals(me)) out.add(r);
        return out;
    }

    public Loan offerLoan(UUID lender, String borrowerName, long amount, int interest, int days, long now) {
        positive(amount);
        if (interest < 0 || interest > MAX_INTEREST) throw new BankException("interest must be 0-" + MAX_INTEREST + " percent");
        if (days < 1 || days > MAX_DAYS) throw new BankException("term must be 1-" + MAX_DAYS + " days");
        Account a = account(lender), b = target(borrowerName);
        if (a == b) throw new BankException("cannot lend money to yourself");
        checkOpenLimit(lender);
        debit(a, amount);
        Loan l = new Loan(nextId++, lender, b.id, amount, interest, days, now);
        loans.put(l.id, l);
        log(now, "loan #" + l.id + " offered " + a.name + " -> " + b.name + " " + amount + " at " + interest + "% for " + days + "d");
        setDirty();
        return l;
    }

    public void answerLoan(UUID me, int id, boolean accept, long now) {
        Loan l = loans.get(id);
        if (l == null || l.status != Status.OPEN) throw new BankException("no open loan offer #" + id);
        if (!l.borrower.equals(me)) throw new BankException("this offer is not addressed to you");
        if (now - l.created > EXPIRY) {
            refund(l);
            l.status = Status.CANCELLED;
            l.changed = now;
            setDirty();
            throw new BankException("loan offer #" + id + " has expired");
        }
        if (accept) {
            Account b = account(me);
            if (b.balance + l.amount > MAX_AMOUNT) throw new BankException("your balance would be too large");
            b.balance += l.amount;
            l.status = Status.ACTIVE;
            l.due = now + l.days * DAY;
            log(now, "loan #" + id + " accepted by " + b.name + " " + l.amount + ", repay " + l.repay());
        } else {
            refund(l);
            l.status = Status.DECLINED;
            log(now, "loan #" + id + " declined");
        }
        l.changed = now;
        setDirty();
    }

    public void cancelLoan(UUID me, int id, long now) {
        Loan l = loans.get(id);
        if (l == null || l.status != Status.OPEN) throw new BankException("no open loan offer #" + id);
        if (!l.lender.equals(me)) throw new BankException("this is not your offer");
        refund(l);
        l.status = Status.CANCELLED;
        l.changed = now;
        log(now, "loan #" + id + " cancelled by lender");
        setDirty();
    }

    private void refund(Loan l) {
        Account a = account(l.lender);
        long room = MAX_AMOUNT - a.balance;
        long back = Math.min(room, l.amount);
        a.balance += back;
        a.proceeds += l.amount - back;
    }

    public void repayLoan(UUID me, int id, long now) {
        Loan l = loans.get(id);
        if (l == null || l.status != Status.ACTIVE) throw new BankException("no active loan #" + id);
        if (!l.borrower.equals(me)) throw new BankException("this is not your loan");
        Account b = account(me), a = account(l.lender);
        long pay = l.repay();
        if (a.balance + pay > MAX_AMOUNT) throw new BankException("the balance of " + a.name + " would be too large");
        debit(b, pay);
        credit(a, pay);
        l.status = Status.DONE;
        l.changed = now;
        log(now, "loan #" + id + " repaid " + b.name + " -> " + a.name + " " + pay);
        setDirty();
    }

    public List<Loan> loansOf(UUID me) {
        List<Loan> out = new ArrayList<>();
        for (Loan l : loans.values()) if (l.lender.equals(me) || l.borrower.equals(me)) out.add(l);
        return out;
    }

    public Offer listOffer(UUID seller, ItemStack stack, long price, long now) {
        positive(price);
        account(seller);
        if (stack.isEmpty()) throw new BankException("no item in that slot");
        checkOpenLimit(seller);
        Offer o = new Offer(nextId++, seller, stack.copy(), price, now);
        offers.put(o.id, o);
        log(now, "offer #" + o.id + " listed by " + name(seller) + " " + stack.getCount() + "x " + stack.getItem() + " for " + price);
        setDirty();
        return o;
    }

    public List<Offer> search(String query) {
        String q = clean(query, 64).toLowerCase(Locale.ROOT);
        List<Offer> out = new ArrayList<>();
        for (Offer o : offers.values()) {
            if (o.status != Status.OPEN) continue;
            if (q.isEmpty() || itemText(o.stack).contains(q) || name(o.seller).toLowerCase(Locale.ROOT).contains(q)) out.add(o);
        }
        out.sort(Comparator.comparingInt((Offer o) -> o.id).reversed());
        return out;
    }

    public static String itemText(ItemStack s) {
        return (s.getHoverName().getString() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()))
                .toLowerCase(Locale.ROOT);
    }

    public void buy(UUID buyer, int id, long now) {
        Offer o = offers.get(id);
        if (o == null || o.status != Status.OPEN) throw new BankException("no open offer #" + id);
        if (o.seller.equals(buyer)) throw new BankException("this is your own offer (cancel it instead)");
        Account b = account(buyer), s = account(o.seller);
        long fee = o.fee(), gain = o.price - fee;
        if (b.balance < o.price) throw new BankException("not enough money (balance " + b.balance + ", needed " + o.price + ")");
        if (s.proceeds + gain > MAX_AMOUNT) throw new BankException("the seller has too much unclaimed money");
        b.balance -= o.price;
        s.proceeds += gain;
        feesBurned += fee;
        b.pickup.add(o.stack.copy());
        o.status = Status.DONE;
        o.changed = now;
        log(now, "buy offer #" + id + " " + b.name + " from " + s.name + " price " + o.price + " fee " + fee);
        setDirty();
    }

    public void cancelOffer(UUID me, int id, long now) {
        Offer o = offers.get(id);
        if (o == null || o.status != Status.OPEN) throw new BankException("no open offer #" + id);
        if (!o.seller.equals(me)) throw new BankException("this is not your offer");
        account(me).pickup.add(o.stack.copy());
        o.status = Status.CANCELLED;
        o.changed = now;
        log(now, "offer #" + id + " cancelled");
        setDirty();
    }

    public List<Offer> offersOf(UUID me) {
        List<Offer> out = new ArrayList<>();
        for (Offer o : offers.values()) if (o.seller.equals(me) && o.status == Status.OPEN) out.add(o);
        return out;
    }

    public String name(UUID id) {
        Account a = accounts.get(id);
        return a == null ? "?" : a.name;
    }

    public long claimMoney(UUID me) {
        Account a = account(me);
        long take = Math.min(a.proceeds, MAX_AMOUNT - a.balance);
        if (take > 0) {
            a.balance += take;
            a.proceeds -= take;
            setDirty();
        }
        return take;
    }

    public List<ItemStack> pickup(UUID me) {
        return account(me).pickup;
    }

    public void prune(long now) {
        boolean any = false;
        for (Request r : requests.values()) {
            if (r.status == Status.OPEN && now - r.created > EXPIRY) {
                r.status = Status.CANCELLED;
                r.changed = now;
                any = true;
            }
        }
        for (Loan l : loans.values()) {
            if (l.status == Status.OPEN && now - l.created > EXPIRY) {
                refund(l);
                l.status = Status.CANCELLED;
                l.changed = now;
                log(now, "loan #" + l.id + " offer expired");
                any = true;
            }
        }
        any |= requests.values().removeIf(r -> closed(r.status) && now - r.changed > KEEP);
        any |= loans.values().removeIf(l -> closed(l.status) && now - l.changed > KEEP);
        any |= offers.values().removeIf(o -> closed(o.status) && now - o.changed > KEEP);
        if (any) setDirty();
    }

    private static boolean closed(Status s) {
        return s == Status.DONE || s == Status.DECLINED || s == Status.CANCELLED;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", nextId);
        tag.putLong("inflow", inflow);
        tag.putLong("fees", feesBurned);
        ListTag au = new ListTag();
        for (String a : audit) au.add(net.minecraft.nbt.StringTag.valueOf(a));
        tag.put("audit", au);
        ListTag acc = new ListTag();
        for (Account a : accounts.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", a.id);
            t.putString("name", a.name);
            t.putLong("bal", a.balance);
            t.putLong("proc", a.proceeds);
            ListTag items = new ListTag();
            for (ItemStack s : a.pickup) items.add(s.save(registries));
            t.put("pickup", items);
            CompoundTag goods = new CompoundTag();
            for (Map.Entry<String, Long> g : a.goods.entrySet()) goods.putLong(g.getKey(), g.getValue());
            t.put("goods", goods);
            acc.add(t);
        }
        tag.put("accounts", acc);
        ListTag rq = new ListTag();
        for (Request r : requests.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", r.id);
            t.putUUID("from", r.from);
            t.putUUID("to", r.to);
            t.putLong("amt", r.amount);
            t.putString("note", r.note);
            t.putByte("st", (byte) r.status.ordinal());
            t.putLong("ch", r.changed);
            t.putLong("cr", r.created);
            rq.add(t);
        }
        tag.put("requests", rq);
        ListTag ln = new ListTag();
        for (Loan l : loans.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", l.id);
            t.putUUID("lender", l.lender);
            t.putUUID("borrower", l.borrower);
            t.putLong("amt", l.amount);
            t.putInt("int", l.interest);
            t.putInt("days", l.days);
            t.putByte("st", (byte) l.status.ordinal());
            t.putLong("due", l.due);
            t.putLong("cr", l.created);
            t.putLong("ch", l.changed);
            ln.add(t);
        }
        tag.put("loans", ln);
        ListTag of = new ListTag();
        for (Offer o : offers.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", o.id);
            t.putUUID("seller", o.seller);
            t.put("stack", o.stack.save(registries));
            t.putLong("price", o.price);
            t.putByte("st", (byte) o.status.ordinal());
            t.putLong("ch", o.changed);
            of.add(t);
        }
        tag.put("offers", of);
        tag.put("exchange", exchange.save());
        return tag;
    }

    private static Status status(CompoundTag t) {
        Status[] all = Status.values();
        int i = t.getByte("st");
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    public static EconomyData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyData d = new EconomyData();
        d.nextId = Math.max(1, tag.getInt("next"));
        d.inflow = tag.getLong("inflow");
        d.feesBurned = tag.getLong("fees");
        for (Tag e : tag.getList("audit", Tag.TAG_STRING)) d.audit.addLast(e.getAsString());
        for (Tag e : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            Account a = new Account(t.getUUID("id"), t.getString("name"));
            a.balance = t.getLong("bal");
            a.proceeds = t.getLong("proc");
            for (Tag it : t.getList("pickup", Tag.TAG_COMPOUND)) ItemStack.parse(registries, it).ifPresent(a.pickup::add);
            CompoundTag goods = t.getCompound("goods");
            for (String g : goods.getAllKeys()) a.goods.put(g, goods.getLong(g));
            d.accounts.put(a.id, a);
        }
        for (Tag e : tag.getList("requests", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            Request r = new Request(t.getInt("id"), t.getUUID("from"), t.getUUID("to"), t.getLong("amt"), t.getString("note"), t.getLong("ch"));
            r.status = status(t);
            r.created = t.contains("cr") ? t.getLong("cr") : r.changed;
            d.requests.put(r.id, r);
        }
        for (Tag e : tag.getList("loans", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            Loan l = new Loan(t.getInt("id"), t.getUUID("lender"), t.getUUID("borrower"), t.getLong("amt"), t.getInt("int"), t.getInt("days"), t.getLong("ch"));
            l.status = status(t);
            l.due = t.getLong("due");
            l.created = t.contains("cr") ? t.getLong("cr") : l.changed;
            d.loans.put(l.id, l);
        }
        for (Tag e : tag.getList("offers", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            ItemStack s = ItemStack.parse(registries, t.get("stack")).orElse(ItemStack.EMPTY);
            if (s.isEmpty()) continue;
            Offer o = new Offer(t.getInt("id"), t.getUUID("seller"), s, t.getLong("price"), t.getLong("ch"));
            o.status = status(t);
            d.offers.put(o.id, o);
        }
        d.exchange.load(tag.getCompound("exchange"));
        return d;
    }
}
