package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.economy.BankException;
import com.sqc.nuclearsq.economy.Coins;
import com.sqc.nuclearsq.economy.EconomyData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class EconomyTests {
    private EconomyTests() {}

    private static final String FLOOR = "floor";
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static EconomyData fresh() {
        EconomyData d = new EconomyData();
        d.touch(A, "Alice");
        d.touch(B, "Bob");
        d.touch(C, "Carl");
        d.deposit(A, 1000, 0);
        d.deposit(B, 500, 0);
        return d;
    }

    private static void fails(String what, Runnable r) {
        try {
            r.run();
        } catch (BankException | ArithmeticException e) {
            return;
        }
        throw new GameTestAssertException(what + ": haette abgelehnt werden muessen");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    private static void invariant(EconomyData d, String when) {
        check(d.invariantHolds(), "Summe aller Konten stimmt nicht mehr " + when);
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void amountsAreChecked(GameTestHelper h) {
        EconomyData d = fresh();
        fails("Betrag 0", () -> d.transfer(A, "Bob", 0, 1));
        fails("negativer Betrag", () -> d.transfer(A, "Bob", -5, 1));
        fails("Long.MIN_VALUE", () -> d.transfer(A, "Bob", Long.MIN_VALUE, 1));
        fails("Long.MAX_VALUE", () -> d.transfer(A, "Bob", Long.MAX_VALUE, 1));
        fails("mehr als vorhanden", () -> d.transfer(A, "Bob", 1001, 1));
        fails("Selbstueberweisung", () -> d.transfer(A, "alice", 10, 1));
        fails("unbekannter Empfaenger", () -> d.transfer(A, "Nobody", 10, 1));
        fails("Einzahlung ueber Obergrenze", () -> d.deposit(A, EconomyData.MAX_AMOUNT, 1));
        fails("Auszahlung negativ", () -> d.withdraw(A, -1, 1));
        fails("Zins negativ", () -> d.offerLoan(A, "Bob", 10, -1, 5, 1));
        fails("Zins zu hoch", () -> d.offerLoan(A, "Bob", 10, 101, 5, 1));
        fails("Laufzeit 0", () -> d.offerLoan(A, "Bob", 10, 5, 0, 1));
        fails("Kredit an sich selbst", () -> d.offerLoan(A, "Alice", 10, 5, 5, 1));
        fails("Preis 0", () -> d.listOffer(A, new ItemStack(Items.DIAMOND), 0, 1));
        fails("Preis negativ", () -> d.listOffer(A, new ItemStack(Items.DIAMOND), -1, 1));
        check(d.account(A).balance == 1000 && d.account(B).balance == 500, "Kontostand nach Fehlversuchen veraendert");
        invariant(d, "nach Fehlversuchen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void overflowIsSafe(GameTestHelper h) {
        EconomyData d = fresh();
        d.deposit(A, EconomyData.MAX_AMOUNT - 1000, 1);
        fails("Empfaenger-Obergrenze", () -> d.transfer(B, "Alice", 500, 1));
        check(d.account(B).balance == 500, "Absender trotz Fehler belastet");
        EconomyData.Loan l = d.offerLoan(B, "Carl", 100, 100, 1, 1);
        check(l.repay() == 200, "Rueckzahlung falsch: " + l.repay());
        invariant(d, "nach Ueberlaufversuchen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void requestNeedsConfirmationOnce(GameTestHelper h) {
        EconomyData d = fresh();
        int id = d.requestMoney(A, "Bob", 100, "Miete", 10).id;
        check(d.account(B).balance == 500, "Anforderung hat schon gezahlt");
        fails("Absender bestaetigt selbst", () -> d.answerRequest(A, id, true, 11));
        fails("Dritter bestaetigt", () -> d.answerRequest(C, id, true, 11));
        d.answerRequest(B, id, true, 12);
        fails("zweite Bestaetigung", () -> d.answerRequest(B, id, true, 13));
        check(d.account(A).balance == 1100 && d.account(B).balance == 400, "Anforderung falsch gebucht");
        int late = d.requestMoney(A, "Bob", 50, "", 20).id;
        fails("abgelaufene Anforderung", () -> d.answerRequest(B, late, true, 20 + EconomyData.EXPIRY + 1));
        check(d.account(B).balance == 400, "abgelaufene Anforderung hat gezahlt");
        invariant(d, "nach Anforderungen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void loanLifecycle(GameTestHelper h) {
        EconomyData d = fresh();
        int id = d.offerLoan(A, "Bob", 200, 10, 5, 0).id;
        check(d.account(A).balance == 800, "Verwahrung nicht abgebucht");
        invariant(d, "mit Kreditangebot");
        fails("Fremder nimmt an", () -> d.answerLoan(C, id, true, 1));
        fails("Fremder gibt zurueck", () -> d.repayLoan(C, id, 1));
        d.answerLoan(B, id, true, 1);
        fails("zweite Annahme", () -> d.answerLoan(B, id, true, 2));
        fails("Abbruch nach Annahme", () -> d.cancelLoan(A, id, 2));
        check(d.account(B).balance == 700, "Kredit nicht gutgeschrieben");
        d.repayLoan(B, id, 3);
        fails("doppelte Rueckzahlung", () -> d.repayLoan(B, id, 4));
        check(d.account(B).balance == 480 && d.account(A).balance == 1020, "Rueckzahlung falsch");
        int id2 = d.offerLoan(A, "Bob", 100, 0, 1, 5).id;
        d.cancelLoan(A, id2, 6);
        fails("zweiter Abbruch", () -> d.cancelLoan(A, id2, 7));
        check(d.account(A).balance == 1020, "Verwahrung nicht zurueck");
        int id3 = d.offerLoan(A, "Bob", 100, 0, 1, 10).id;
        d.prune(10 + EconomyData.EXPIRY + 1);
        check(d.account(A).balance == 1020, "abgelaufenes Angebot gab das Geld nicht zurueck");
        fails("abgelaufenes Angebot annehmen", () -> d.answerLoan(B, id3, true, 10 + EconomyData.EXPIRY + 2));
        invariant(d, "nach Krediten");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void auctionIsAtomic(GameTestHelper h) {
        EconomyData d = fresh();
        int id = d.listOffer(A, new ItemStack(Items.DIAMOND, 3), 200, 0).id;
        fails("eigenes Angebot kaufen", () -> d.buy(A, id, 1));
        fails("zu wenig Geld", () -> d.buy(C, id, 1));
        check(d.search("diamond").size() == 1, "Angebot nicht auffindbar");
        d.buy(B, id, 2);
        fails("zweiter Kauf", () -> d.buy(B, id, 3));
        fails("Abbruch nach Verkauf", () -> d.cancelOffer(A, id, 4));
        check(d.account(B).balance == 300, "Kaeufer falsch belastet");
        check(d.account(A).proceeds == 190, "Erloes ohne 5 % Gebuehr falsch: " + d.account(A).proceeds);
        check(d.pickup(B).size() == 1 && d.pickup(B).get(0).getCount() == 3, "Item beim Kaeufer fehlt");
        check(d.claimMoney(A) == 190 && d.claimMoney(A) == 0, "Erloes doppelt abholbar");
        int id2 = d.listOffer(A, new ItemStack(Items.GOLD_INGOT), 10, 5).id;
        fails("Fremder bricht ab", () -> d.cancelOffer(B, id2, 6));
        d.cancelOffer(A, id2, 7);
        check(d.pickup(A).size() == 1, "Item nach Abbruch nicht zurueck");
        invariant(d, "nach Auktion");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void rateLimitAndAudit(GameTestHelper h) {
        EconomyData d = fresh();
        for (int i = 0; i < EconomyData.RATE_MAX; i++) d.throttle(A, 100);
        fails("Rate-Limit", () -> d.throttle(A, 100));
        d.throttle(A, 100 + EconomyData.RATE_WINDOW);
        d.throttle(B, 100);
        check(!d.auditLog().isEmpty(), "Audit-Log leer");
        check(Coins.change(12_345).stream().mapToLong(Coins::value).sum() == 12_345, "Muenzwechsel falsch");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void roundingCannotBeAbused(GameTestHelper h) {
        EconomyData d = fresh();
        d.deposit(A, 1000, 0);
        for (long price = 1; price <= 40; price++) {
            EconomyData.Offer o = d.listOffer(A, new ItemStack(Items.STICK), price, 1);
            check(o.fee() >= 1, "Preis " + price + " ohne Gebuehr");
            check(o.fee() <= price, "Gebuehr groesser als Preis " + price);
            d.cancelOffer(A, o.id, 2);
        }
        check(d.offerLoan(A, "Bob", 1, 1, 1, 1).repay() >= 2, "Zins 1 % auf 1 rundet auf Null");
        check(d.offerLoan(A, "Bob", 1000, 7, 1, 1).repay() == 1070, "Zins 7 % auf 1000 falsch");
        invariant(d, "nach Rundungsfaellen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void fuzzedInputsAreHarmless(GameTestHelper h) {
        EconomyData d = fresh();
        fails("Riesen-Auszahlung", () -> Coins.change(Long.MAX_VALUE));
        fails("Auszahlung 0", () -> Coins.change(0));
        fails("Auszahlung ueber Behaelter", () -> Coins.change(Coins.MAX_CHANGE + 1));
        check(Coins.change(Coins.MAX_CHANGE).size() == 18, "groesste Auszahlung passt nicht auf 18 Plaetze");
        String evil = "Diamant\u001b[2J\n\r\u0007Fake\u202e";
        String clean = EconomyData.clean(evil, 64);
        check(clean.chars().noneMatch(c -> c < 32 || c == 127 || c == 0x202e), "Steuerzeichen blieben: " + clean);
        check(EconomyData.clean("x".repeat(10_000), 40).length() == 40, "Text nicht gekuerzt");
        check(EconomyData.clean(null, 40).isEmpty(), "null nicht abgefangen");
        check(d.search("y".repeat(100_000)).isEmpty(), "Such-Fuzz fand etwas");
        for (long v : new long[]{Long.MIN_VALUE, -1, 0, Long.MAX_VALUE, Long.MAX_VALUE - 1, EconomyData.MAX_AMOUNT + 1}) {
            fails("transfer " + v, () -> d.transfer(A, "Bob", v, 1));
            fails("Anforderung " + v, () -> d.requestMoney(A, "Bob", v, "", 1));
            fails("Kredit " + v, () -> d.offerLoan(A, "Bob", v, 1, 1, 1));
            fails("Angebot " + v, () -> d.listOffer(A, new ItemStack(Items.DIAMOND), v, 1));
        }
        for (int v : new int[]{Integer.MIN_VALUE, -1, 101, Integer.MAX_VALUE}) {
            fails("Zins " + v, () -> d.offerLoan(A, "Bob", 10, v, 1, 1));
        }
        for (int v : new int[]{Integer.MIN_VALUE, -1, 0, EconomyData.MAX_DAYS + 1, Integer.MAX_VALUE}) {
            fails("Tage " + v, () -> d.offerLoan(A, "Bob", 10, 1, v, 1));
        }
        fails("unbekannte Anforderung", () -> d.answerRequest(A, Integer.MAX_VALUE, true, 1));
        fails("unbekannter Kredit", () -> d.repayLoan(A, -1, 1));
        fails("unbekanntes Angebot", () -> d.buy(A, 0, 1));
        check(d.account(A).balance == 1000 && d.account(B).balance == 500, "Fuzzing hat Konten veraendert");
        invariant(d, "nach Fuzzing");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 200)
    public static void randomizedAttackersCannotHarmOthers(GameTestHelper h) {
        UUID dora = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        EconomyData d = fresh();
        d.touch(dora, "Dora");
        d.deposit(C, 300, 0);
        d.deposit(dora, 300, 0);
        UUID[] who = {A, B, C, dora};
        String[] names = {"Alice", "Bob", "Carl", "Dora", "Nobody", "alice", " Bob ", ""};
        long[] amounts = {Long.MIN_VALUE, -1, 0, 1, 2, 19, 20, 99, 100, 500, 1000, EconomyData.MAX_AMOUNT, Long.MAX_VALUE};
        int[] held = {1000, 1000, 1000, 1000};
        java.util.Random rnd = new java.util.Random(20261027L);
        long now = 0;
        for (int step = 0; step < 4000; step++) {
            int ai = rnd.nextInt(4);
            UUID me = who[ai];
            long[] before = new long[4];
            for (int i = 0; i < 4; i++) before[i] = d.account(who[i]).balance + d.account(who[i]).proceeds;
            String name = names[rnd.nextInt(names.length)];
            long amt = amounts[rnd.nextInt(amounts.length)];
            int id = rnd.nextInt(10) == 0 ? Integer.MIN_VALUE : 1 + rnd.nextInt(300);
            boolean yes = rnd.nextBoolean();
            int op = rnd.nextInt(14);
            String what = "Schritt " + step + " Aktion " + op + " Spieler " + ai + " id " + id + " betrag " + amt;
            boolean allowed = switch (op) {
                case 2 -> d.requestsOf(me).stream().anyMatch(r -> r.id == id && r.to.equals(me) && r.status == EconomyData.Status.OPEN);
                case 3 -> d.requestsOf(me).stream().anyMatch(r -> r.id == id && r.from.equals(me) && r.status == EconomyData.Status.OPEN);
                case 5 -> d.loansOf(me).stream().anyMatch(l -> l.id == id && l.borrower.equals(me) && l.status == EconomyData.Status.OPEN);
                case 6 -> d.loansOf(me).stream().anyMatch(l -> l.id == id && l.lender.equals(me) && l.status == EconomyData.Status.OPEN);
                case 7 -> d.loansOf(me).stream().anyMatch(l -> l.id == id && l.borrower.equals(me) && l.status == EconomyData.Status.ACTIVE);
                case 9 -> d.search("").stream().anyMatch(o -> o.id == id && !o.seller.equals(me));
                case 10 -> d.offersOf(me).stream().anyMatch(o -> o.id == id);
                default -> true;
            };
            boolean done = false;
            try {
                switch (op) {
                    case 0 -> d.transfer(me, name, amt, now);
                    case 1 -> d.requestMoney(me, name, amt, "x", now);
                    case 2 -> d.answerRequest(me, id, yes, now);
                    case 3 -> d.cancelRequest(me, id, now);
                    case 4 -> d.offerLoan(me, name, amt, rnd.nextInt(108) - 3, rnd.nextInt(66) - 3, now);
                    case 5 -> d.answerLoan(me, id, yes, now);
                    case 6 -> d.cancelLoan(me, id, now);
                    case 7 -> d.repayLoan(me, id, now);
                    case 8 -> {
                        int c = 1 + rnd.nextInt(5);
                        d.listOffer(me, new ItemStack(Items.STICK, c), amt, now);
                        held[ai] -= c;
                        if (held[ai] < 0) throw new GameTestAssertException(what + ": mehr Items eingestellt als vorhanden");
                    }
                    case 9 -> d.buy(me, id, now);
                    case 10 -> d.cancelOffer(me, id, now);
                    case 11 -> {
                        d.claimMoney(me);
                        var pk = d.pickup(me);
                        for (ItemStack st : pk) held[ai] += st.getCount();
                        pk.clear();
                    }
                    case 12 -> d.deposit(me, 1 + rnd.nextInt(300), now);
                    case 13 -> {
                        now += rnd.nextInt((int) (2 * EconomyData.DAY));
                        d.prune(now);
                    }
                    default -> { }
                }
                done = true;
            } catch (BankException e) {
            }
            if (done && !allowed) throw new GameTestAssertException(what + ": fremder Eintrag wurde angenommen");
            long total = 0;
            for (int i = 0; i < 4; i++) {
                EconomyData.Account a = d.account(who[i]);
                check(a.balance >= 0 && a.proceeds >= 0, what + ": negatives Konto " + i);
                check(a.balance <= EconomyData.MAX_AMOUNT && a.proceeds <= EconomyData.MAX_AMOUNT, what + ": Konto ueber Obergrenze " + i);
                if (i != ai) check(a.balance + a.proceeds >= before[i], what + ": fremdes Geld gesunken bei " + i);
            }
            check(d.invariantHolds(), what + ": Summen-Invariante verletzt");
            long items = 0;
            for (int i = 0; i < 4; i++) {
                items += held[i];
                for (ItemStack st : d.pickup(who[i])) items += st.getCount();
            }
            for (EconomyData.Offer o : d.search("")) items += o.stack.getCount();
            check(items == 4000, what + ": Items nicht erhalten (" + items + " statt 4000)");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void confirmationIsOneShotAndBound(GameTestHelper h) {
        var c = new com.sqc.nuclearsq.economy.ActionConfirm();
        long t0 = 1000;
        check(c.check("transfer|bob|100", t0) == com.sqc.nuclearsq.economy.ActionConfirm.Result.NEW_PENDING, "erste Anfrage nicht vorgemerkt");
        check(c.check("transfer|bob|100", t0 + 1) == com.sqc.nuclearsq.economy.ActionConfirm.Result.STILL_PENDING, "ohne Rechtsklick durchgelassen");
        check(c.confirm(t0 + 2), "Rechtsklick bestaetigt nicht");
        check(c.check("transfer|bob|101", t0 + 3) == com.sqc.nuclearsq.economy.ActionConfirm.Result.NEW_PENDING, "Bestaetigung galt auch fuer anderen Betrag");
        check(c.check("transfer|bob|100", t0 + 4) == com.sqc.nuclearsq.economy.ActionConfirm.Result.NEW_PENDING, "alte Bestaetigung nach Aktionswechsel noch gueltig");
        check(c.confirm(t0 + 5), "zweiter Rechtsklick bestaetigt nicht");
        check(c.check("transfer|bob|100", t0 + 6) == com.sqc.nuclearsq.economy.ActionConfirm.Result.CONSUMED, "bestaetigte Aktion lief nicht durch");
        check(c.check("transfer|bob|100", t0 + 7) == com.sqc.nuclearsq.economy.ActionConfirm.Result.NEW_PENDING, "Bestaetigung zweimal benutzbar (Replay)");
        c.clear();
        check(!c.confirm(t0 + 8), "Vorab-Bestaetigung ohne Anfrage");
        check(c.check("withdraw|50", t0 + 9) == com.sqc.nuclearsq.economy.ActionConfirm.Result.NEW_PENDING, "Vorab-Bestaetigung wirkt");
        check(!c.confirm(t0 + 9 + com.sqc.nuclearsq.economy.ActionConfirm.TICKS + 1), "abgelaufene Anfrage bestaetigt");
        c.check("withdraw|50", t0 + 100);
        check(c.confirm(t0 + 101), "Bestaetigung nicht angenommen");
        check(c.check("withdraw|50", t0 + 101 + com.sqc.nuclearsq.economy.ActionConfirm.TICKS + 1)
                == com.sqc.nuclearsq.economy.ActionConfirm.Result.NEW_PENDING, "abgelaufene Bestaetigung noch gueltig");
        c.check("buy|7|10", t0 + 200);
        c.confirm(t0 + 201);
        c.clear();
        check(c.check("buy|7|10", t0 + 202) == com.sqc.nuclearsq.economy.ActionConfirm.Result.NEW_PENDING, "Bestaetigung ueberlebt das Abmelden");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void confirmationNeedsLoggedInUser(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 2, 3);
        h.setBlock(rel, com.sqc.nuclearsq.economy.EconomyRegistry.TERMINAL.get());
        var t = (com.sqc.nuclearsq.economy.BankTerminalBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        fails("Bestaetigung ohne Nutzer", () -> t.requireConfirmation("transfer|bob|1", net.minecraft.network.chat.Component.literal("x")));
        var a = net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(A, "FakeAlice"));
        check(!t.confirmPending(a), "Rechtsklick eines Nicht-Angemeldeten bestaetigt");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 20)
    public static void ambiguousNameIsRejected(GameTestHelper h) {
        EconomyData d = fresh();
        d.touch(UUID.fromString("00000000-0000-0000-0000-0000000000d0"), "Bob");
        fails("doppelter Name", () -> d.transfer(A, "Bob", 10, 1));
        fails("doppelter Name bei Anforderung", () -> d.requestMoney(A, "bob", 10, "", 1));
        check(d.account(A).balance == 1000, "trotz doppeltem Namen gebucht");
        invariant(d, "nach doppeltem Namen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 40)
    public static void noUserNoAction(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 2, 3);
        h.setBlock(rel, com.sqc.nuclearsq.economy.EconomyRegistry.TERMINAL.get());
        var t = (com.sqc.nuclearsq.economy.BankTerminalBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        check(t != null, "kein Terminal");
        fails("Einzahlen ohne Anmeldung", t::deposit);
        fails("Auszahlen ohne Anmeldung", () -> t.withdraw(10));
        fails("Einstellen ohne Anmeldung", () -> t.listSlot(1, 10));
        fails("Abholen ohne Anmeldung", t::claim);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy", timeoutTicks = 40)
    public static void containerBelongsToItsOwner(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 2, 3);
        h.setBlock(rel, com.sqc.nuclearsq.economy.EconomyRegistry.TERMINAL.get());
        var t = (com.sqc.nuclearsq.economy.BankTerminalBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        var a = net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(A, "FakeAlice"));
        var b = net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(B, "FakeBob"));
        check(t.login(a), "Alice kann sich nicht anmelden");
        t.setItem(0, new ItemStack(com.sqc.nuclearsq.economy.EconomyRegistry.COIN_BAR.get(), 5));
        check(!t.login(b), "Bob meldet sich bei fremdem Behaelter an");
        t.clearContent();
        check(t.login(b), "Bob kann sich bei leerem Behaelter nicht anmelden");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "economy_link_diagnosis", timeoutTicks = 60)
    public static void linkDiagnosisSaysWhatIsMissing(GameTestHelper h) {
        BlockPos term = new BlockPos(3, 2, 3), modemT = new BlockPos(4, 2, 3), modemS = new BlockPos(7, 2, 3), server = new BlockPos(8, 2, 3);
        h.setBlock(term, com.sqc.nuclearsq.economy.EconomyRegistry.TERMINAL.get());
        h.runAfterDelay(2, () -> {
            check(com.sqc.nuclearsq.economy.BankNet.link(h.getLevel(), h.absolutePos(term)) == com.sqc.nuclearsq.economy.BankNet.Link.NO_SERVER,
                    "ohne Server nicht NO_SERVER");
            h.setBlock(server, com.sqc.nuclearsq.economy.EconomyRegistry.SERVER.get());
            h.runAfterDelay(2, () -> {
                check(com.sqc.nuclearsq.economy.BankNet.link(h.getLevel(), h.absolutePos(term)) == com.sqc.nuclearsq.economy.BankNet.Link.TERMINAL_NO_MODEM,
                        "Server weit weg, Terminal ohne Modem: nicht TERMINAL_NO_MODEM");
                h.setBlock(modemT, com.sqc.nuclearsq.economy.EconomyRegistry.MODEM.get());
                h.runAfterDelay(2, () -> {
                    check(com.sqc.nuclearsq.economy.BankNet.link(h.getLevel(), h.absolutePos(term)) == com.sqc.nuclearsq.economy.BankNet.Link.SERVER_NO_MODEM,
                            "Modem nur am Terminal: nicht SERVER_NO_MODEM");
                    h.setBlock(modemS, com.sqc.nuclearsq.economy.EconomyRegistry.MODEM.get());
                    h.runAfterDelay(2, () -> {
                        check(com.sqc.nuclearsq.economy.BankNet.link(h.getLevel(), h.absolutePos(term)) == com.sqc.nuclearsq.economy.BankNet.Link.ONLINE,
                                "mit beiden Modems nicht ONLINE");
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "economy_link_basic", timeoutTicks = 40)
    public static void terminalNeedsServerLink(GameTestHelper h) {
        BlockPos term = new BlockPos(3, 2, 3), server = new BlockPos(6, 2, 3), modemT = new BlockPos(4, 2, 3), modemS = new BlockPos(5, 2, 3);
        h.setBlock(term, com.sqc.nuclearsq.economy.EconomyRegistry.TERMINAL.get());
        h.setBlock(server, com.sqc.nuclearsq.economy.EconomyRegistry.SERVER.get());
        h.runAfterDelay(2, () -> {
            check(!com.sqc.nuclearsq.economy.BankNet.online(h.getLevel(), h.absolutePos(term)), "ohne Modem online");
            h.setBlock(modemT, com.sqc.nuclearsq.economy.EconomyRegistry.MODEM.get());
            check(!com.sqc.nuclearsq.economy.BankNet.online(h.getLevel(), h.absolutePos(term)), "Modem nur am Terminal macht online");
            h.setBlock(modemS, com.sqc.nuclearsq.economy.EconomyRegistry.MODEM.get());
            h.runAfterDelay(2, () -> {
                check(com.sqc.nuclearsq.economy.BankNet.online(h.getLevel(), h.absolutePos(term)), "mit zwei Modems nicht online");
                h.succeed();
            });
        });
    }
}
