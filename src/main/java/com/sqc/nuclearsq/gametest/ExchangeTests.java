package com.sqc.nuclearsq.gametest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.economy.BankException;
import com.sqc.nuclearsq.economy.EconomyData;
import com.sqc.nuclearsq.economy.Exchange;
import com.sqc.nuclearsq.economy.Exchange.Side;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ExchangeTests {
    private ExchangeTests() {}

    private static final String FLOOR = "floor";
    private static final String IRON = "minecraft:iron_ingot", COAL = "minecraft:coal";
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID E = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private static EconomyData market() {
        EconomyData d = new EconomyData();
        String[] names = {"Anna", "Ben", "Cleo", "Dirk"};
        UUID[] ids = {A, B, C, D};
        for (int i = 0; i < 4; i++) {
            d.touch(ids[i], names[i]);
            d.deposit(ids[i], 100_000, 0);
            d.exchange().depositGoods(ids[i], Map.of(IRON, 500L, COAL, 500L), 0);
        }
        d.touch(E, "Erik");
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
        check(d.invariantHolds(), "Summen-Invariante (Geld oder Waren) verletzt " + when);
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 20)
    public static void priceTimePriority(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        x.place(A, Side.SELL, IRON, 12, 10, 1);
        x.place(B, Side.SELL, IRON, 10, 10, 2);
        x.place(C, Side.SELL, IRON, 10, 10, 3);
        Exchange.Placement p = x.place(D, Side.BUY, IRON, 12, 25, 4);
        check(p.filled() == 25 && p.remaining() == 0, "Kauf nicht ganz ausgefuehrt: " + p);
        List<Exchange.Trade> t = x.recent(IRON, 10);
        check(t.size() == 3, "falsche Zahl Ausfuehrungen: " + t.size());
        check(t.get(0).price() == 10 && t.get(0).qty() == 10, "erst der billigste und aelteste (Ben)");
        check(t.get(1).price() == 10 && t.get(1).qty() == 10, "dann Cleo zum gleichen Preis");
        check(t.get(2).price() == 12 && t.get(2).qty() == 5, "zuletzt Anna mit 5 Stueck");
        check(d.account(D).balance == 100_000 - 260, "Kaeufer zahlt 260 (Preisverbesserung erstattet): " + d.account(D).balance);
        check(d.account(D).goods.get(IRON) == 525, "Kaeufer bekommt 25 Eisen");
        check(d.account(B).balance == 100_000 + 98 && d.account(C).balance == 100_000 + 98, "Verkaeufer bekommen 100 minus 2 Gebuehr");
        check(d.account(A).balance == 100_000 + 58, "Anna 60 minus 2 Gebuehr");
        check(x.ordersOf(A).size() == 1 && x.ordersOf(A).get(0).qty == 5, "Annas Rest steht noch im Buch");
        check(x.ordersOf(B).isEmpty() && x.ordersOf(C).isEmpty() && x.ordersOf(D).isEmpty(), "ausgefuehrte Orders stehen noch im Buch");
        Exchange.Info i = x.info(IRON, 10);
        check(i.last() == 12 && i.high() == 12 && i.low() == 10 && i.volume() == 25, "Kursdaten falsch: " + i);
        invariant(d, "nach Preis-Zeit-Matching");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 20)
    public static void restingOrderSetsThePrice(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        x.place(B, Side.BUY, IRON, 10, 5, 1);
        check(d.account(B).balance == 100_000 - 50, "Kauf-Order verwahrt das Geld nicht");
        Exchange.Placement p = x.place(C, Side.SELL, IRON, 9, 8, 2);
        check(p.filled() == 5 && p.remaining() == 3, "Teilausfuehrung falsch: " + p);
        check(x.recent(IRON, 5).get(0).price() == 10, "Preis ist der der wartenden Order (10), nicht der des Verkaeufers");
        check(d.account(C).balance == 100_000 + 49, "Verkaeufer: 50 minus 1 Gebuehr");
        check(x.ordersOf(C).size() == 1 && x.ordersOf(C).get(0).qty == 3 && x.ordersOf(C).get(0).price == 9, "Rest der Verkaufs-Order fehlt");
        check(d.account(B).goods.get(IRON) == 505, "Kaeufer bekommt die Ware");
        invariant(d, "nach Teilausfuehrung");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 20)
    public static void noSelfTrading(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        x.place(A, Side.SELL, IRON, 10, 5, 1);
        fails("eigener Kauf trifft eigenen Verkauf", () -> x.place(A, Side.BUY, IRON, 10, 5, 2));
        fails("eigener Kauf darueber", () -> x.place(A, Side.BUY, IRON, 11, 5, 2));
        x.place(A, Side.BUY, IRON, 9, 5, 3);
        fails("eigener Verkauf trifft eigenen Kauf", () -> x.place(A, Side.SELL, IRON, 9, 5, 4));
        fails("eigener Verkauf darunter", () -> x.place(A, Side.SELL, IRON, 8, 5, 4));
        x.place(A, Side.SELL, COAL, 5, 5, 5);
        check(x.recent(IRON, 5).isEmpty(), "Selbsthandel hat Kurs erzeugt");
        invariant(d, "nach Selbsthandel-Versuchen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 20)
    public static void cancelGivesBackEverything(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        int buy = x.place(B, Side.BUY, IRON, 7, 10, 1).id();
        int sell = x.place(A, Side.SELL, COAL, 20, 5, 2).id();
        check(d.account(B).balance == 100_000 - 70 && d.account(A).goods.get(COAL) == 495, "Verwahrung nicht abgezogen");
        fails("Fremder bricht ab", () -> x.cancel(C, buy, 3));
        x.cancel(B, buy, 3);
        x.cancel(A, sell, 3);
        fails("zweiter Abbruch", () -> x.cancel(B, buy, 4));
        check(d.account(B).balance == 100_000 && d.account(A).goods.get(COAL) == 500, "Geld oder Ware nicht zurueck");
        check(x.openOrderCount() == 0, "Orders nicht entfernt");
        invariant(d, "nach Abbruch");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 20)
    public static void noShortSellingNoLeverage(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        fails("Verkauf ohne Ware", () -> x.place(E, Side.SELL, IRON, 5, 1, 1));
        fails("Kauf ohne Geld", () -> x.place(E, Side.BUY, IRON, 5, 1, 1));
        fails("mehr verkaufen als im Lager", () -> x.place(A, Side.SELL, IRON, 5, 501, 1));
        fails("mehr kaufen als das Geld reicht", () -> x.place(A, Side.BUY, IRON, 1000, 101, 1));
        x.place(A, Side.SELL, IRON, 5, 500, 1);
        fails("dieselbe Ware zweimal verkaufen", () -> x.place(A, Side.SELL, IRON, 6, 1, 2));
        check(d.account(A).goods.getOrDefault(IRON, 0L) == 0, "Lager nicht leer");
        invariant(d, "nach Leerverkauf-Versuchen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 20)
    public static void limitsAndOverflow(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        for (long v : new long[]{Long.MIN_VALUE, -1, 0, Exchange.MAX_PRICE + 1, Long.MAX_VALUE}) {
            fails("Preis " + v, () -> x.place(A, Side.BUY, IRON, v, 1, 1));
            fails("Menge " + v, () -> x.place(A, Side.SELL, IRON, 1, v, 1));
        }
        fails("Menge ueber Hoechstmenge", () -> x.place(A, Side.SELL, IRON, 1, Exchange.MAX_QTY + 1, 1));
        fails("Orderwert ueber Obergrenze", () -> x.place(A, Side.BUY, IRON, Exchange.MAX_PRICE, Exchange.MAX_QTY, 1));
        fails("unbekannte Ware", () -> x.place(A, Side.BUY, "minecraft:bedrock", 1, 1, 1));
        fails("Ware ohne Namensraum", () -> x.place(A, Side.BUY, "iron_ingot", 1, 1, 1));
        fails("keine Seite", () -> x.place(A, null, IRON, 1, 1, 1));
        fails("Auslagern 0", () -> x.withdrawGoods(A, IRON, 0, 1));
        fails("Auslagern zu viel", () -> x.withdrawGoods(A, IRON, 501, 1));
        fails("Auslagern ueber Behaelter", () -> x.withdrawGoods(A, IRON, Exchange.MAX_WITHDRAW + 1, 1));
        fails("Einlagern unbekannte Ware", () -> x.depositGoods(A, Map.of("minecraft:bedrock", 5L), 1));
        fails("Einlagern negativ", () -> x.depositGoods(A, Map.of(IRON, -5L), 1));
        fails("Einlagern ueber Lagergrenze", () -> x.depositGoods(A, Map.of(IRON, Exchange.MAX_STOCK), 1));
        for (int i = 0; i < Exchange.MAX_ORDERS_PER_PLAYER; i++) x.place(B, Side.BUY, COAL, 1 + i, 1, 1);
        fails("21. Order", () -> x.place(B, Side.BUY, COAL, 50, 1, 1));
        check(d.account(A).goods.get(IRON) == 500 && d.account(A).balance == 100_000, "Fehlversuche haben das Konto veraendert");
        invariant(d, "nach Grenzwert-Versuchen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 20)
    public static void feeCannotBeEvaded(GameTestHelper h) {
        for (long cost = 1; cost <= 400; cost++) {
            long fee = Exchange.fee(cost);
            check(fee >= 1 && fee <= cost, "Gebuehr " + fee + " bei " + cost);
            check(fee * 100 >= cost * Exchange.FEE_PERCENT, "Gebuehr rundet zu Gunsten des Verkaeufers ab bei " + cost);
        }
        EconomyData d = market();
        Exchange x = d.exchange();
        long before = d.account(A).balance + d.account(B).balance;
        x.place(A, Side.SELL, IRON, 1, 1, 1);
        x.place(B, Side.BUY, IRON, 1, 1, 2);
        check(d.account(A).balance + d.account(B).balance < before, "Handel zu Preis 1 ohne Gebuehr");
        invariant(d, "nach Kleinsthandel");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 60)
    public static void historyAndStatsAreBounded(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        for (int i = 0; i < Exchange.HISTORY + 50; i++) {
            x.place(A, Side.SELL, IRON, 5 + (i % 7), 1, i);
            x.place(B, Side.BUY, IRON, 5 + (i % 7), 1, i);
        }
        check(x.recent(IRON, 100_000).size() == Exchange.HISTORY, "Kursverlauf nicht auf " + Exchange.HISTORY + " begrenzt");
        Exchange.Info i = x.info(IRON, Exchange.HISTORY + 49);
        check(i.high() == 11 && i.low() == 5, "Hoch/Tief falsch: " + i);
        Exchange.Info later = x.info(IRON, 10 * EconomyData.DAY);
        check(later.last() == i.last() && later.volume() == 0, "alte Handel zaehlen noch zum Tag: " + later);
        invariant(d, "nach vielen Handeln");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 40)
    public static void bookSurvivesSaving(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        x.place(A, Side.SELL, IRON, 12, 10, 1);
        x.place(B, Side.BUY, IRON, 12, 4, 2);
        x.place(C, Side.BUY, COAL, 3, 9, 3);
        var prov = h.getLevel().registryAccess();
        EconomyData d2 = EconomyData.load(d.save(new net.minecraft.nbt.CompoundTag(), prov), prov);
        check(d2.invariantHolds(), "geladene Daten verletzen die Invariante");
        check(d2.exchange().openOrderCount() == d.exchange().openOrderCount(), "Orders gehen beim Speichern verloren");
        check(d2.exchange().info(IRON, 5).last() == 12, "Kurs geht beim Speichern verloren");
        check(d2.account(B).goods.get(IRON) == 504 && d2.account(C).balance == d.account(C).balance, "Konto/Lager nicht gleich");
        check(d2.exchange().levels(IRON, Side.SELL, 5).get(0)[1] == 6, "Buchtiefe nicht gleich");
        d2.exchange().place(D, Side.BUY, IRON, 12, 6, 6);
        invariant(d2, "nach Weiterhandeln");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 20)
    public static void tickerIsPublicAndBounded(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        for (int i = 0; i < 60; i++) {
            x.place(A, Side.SELL, IRON, 10 + (i % 5), 1, i);
            x.place(B, Side.BUY, IRON, 10 + (i % 5), 1, i);
        }
        x.place(C, Side.BUY, COAL, 3, 9, 70);
        List<Exchange.Ticker> tick = x.ticker(70, 40);
        check(tick.size() == Exchange.goods().size(), "Kurstafel zeigt nicht alle Waren");
        for (Exchange.Ticker row : tick) {
            check(row.history().size() <= 40, "Verlauf laenger als 40: " + row.history().size());
            for (var comp : Exchange.Ticker.class.getRecordComponents()) {
                check(comp.getType() != UUID.class && !comp.getType().getName().contains("Account") && !comp.getType().getName().contains("Order"),
                        "Kurstafel enthaelt Personen- oder Orderdaten: " + comp.getName());
            }
        }
        Exchange.Ticker iron = tick.stream().filter(r -> r.good().equals(IRON)).findFirst().orElseThrow();
        check(iron.history().size() == 40 && iron.info().last() > 0, "Eisen-Verlauf falsch");
        Exchange.Ticker coal = tick.stream().filter(r -> r.good().equals(COAL)).findFirst().orElseThrow();
        check(coal.history().isEmpty() && coal.info().last() == 0 && coal.info().bid() == 3, "Kohle ohne Handel falsch: " + coal);
        com.sqc.nuclearsq.economy.Throttle th = new com.sqc.nuclearsq.economy.Throttle(10, 20);
        for (int i = 0; i < 10; i++) th.hit(100);
        fails("11. Abruf im Fenster", () -> th.hit(100));
        th.hit(120);
        invariant(d, "nach Kurstafel");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exchange", timeoutTicks = 200)
    public static void randomizedTradersCannotHarmOthers(GameTestHelper h) {
        EconomyData d = market();
        Exchange x = d.exchange();
        UUID[] who = {A, B, C, D};
        String[] goods = {IRON, COAL};
        long[][] inv = new long[4][2];
        for (long[] row : inv) {
            row[0] = 200;
            row[1] = 200;
        }
        long[] hostile = {Long.MIN_VALUE, -1, 0, Exchange.MAX_PRICE + 1, Long.MAX_VALUE};
        java.util.Random rnd = new java.util.Random(20261028L);
        for (int step = 0; step < 4000; step++) {
            int ai = rnd.nextInt(4), gi = rnd.nextInt(2);
            UUID me = who[ai];
            String good = goods[gi];
            long[] moneyBefore = new long[4];
            long[][] stockBefore = new long[4][2];
            for (int i = 0; i < 4; i++) {
                moneyBefore[i] = d.account(who[i]).balance + d.account(who[i]).proceeds;
                for (int g = 0; g < 2; g++) stockBefore[i][g] = x.stock(who[i], goods[g]);
            }
            long price = 1 + rnd.nextInt(20), qty = 1 + rnd.nextInt(30);
            int id = rnd.nextInt(8) == 0 ? Integer.MIN_VALUE : 1 + rnd.nextInt(400);
            int op = rnd.nextInt(8);
            String what = "Schritt " + step + " Aktion " + op + " Spieler " + ai + " Ware " + good + " Preis " + price + " Menge " + qty + " id " + id;
            boolean eligible = x.ordersOf(me).stream().anyMatch(o -> o.id == id);
            boolean done = false, mustFail = false;
            try {
                switch (op) {
                    case 0 -> x.place(me, Side.BUY, good, price, qty, step);
                    case 1 -> x.place(me, Side.SELL, good, price, qty, step);
                    case 2 -> {
                        x.cancel(me, id, step);
                        if (!eligible) throw new GameTestAssertException(what + ": fremde oder erfundene Order abgebrochen");
                    }
                    case 3 -> {
                        long n = 1 + rnd.nextInt(50);
                        if (inv[ai][gi] >= n) {
                            x.depositGoods(me, Map.of(good, n), step);
                            inv[ai][gi] -= n;
                        }
                    }
                    case 4 -> {
                        long n = 1 + rnd.nextInt(50);
                        if (x.stock(me, good) >= n) {
                            x.withdrawGoods(me, good, n, step);
                            inv[ai][gi] += n;
                        }
                    }
                    case 5 -> {
                        mustFail = true;
                        x.place(me, rnd.nextBoolean() ? Side.BUY : Side.SELL, good, hostile[rnd.nextInt(5)], qty, step);
                        x.place(me, rnd.nextBoolean() ? Side.BUY : Side.SELL, good, price, hostile[rnd.nextInt(5)], step);
                    }
                    case 6 -> d.transfer(me, "Ben", 1 + rnd.nextInt(500), step);
                    default -> x.place(me, rnd.nextBoolean() ? Side.BUY : Side.SELL, good, 1 + rnd.nextInt(3), 1 + rnd.nextInt(5), step);
                }
                done = true;
            } catch (BankException e) {
            }
            if (done && mustFail) throw new GameTestAssertException(what + ": ungueltige Order wurde angenommen");
            check(d.invariantHolds(), what + ": Summen-Invariante verletzt");
            for (int i = 0; i < 4; i++) {
                EconomyData.Account a = d.account(who[i]);
                check(a.balance >= 0 && a.proceeds >= 0, what + ": negatives Konto " + i);
                if (i != ai) {
                    check(a.balance + a.proceeds >= moneyBefore[i], what + ": fremdes Geld gesunken bei " + i);
                    for (int g = 0; g < 2; g++) check(x.stock(who[i], goods[g]) >= stockBefore[i][g], what + ": fremdes Lager gesunken bei " + i);
                }
                check(x.ordersOf(who[i]).size() <= Exchange.MAX_ORDERS_PER_PLAYER, what + ": mehr als 20 Orders bei " + i);
                for (Exchange.Order b : x.ordersOf(who[i])) {
                    for (Exchange.Order s : x.ordersOf(who[i])) {
                        if (b.side == Side.BUY && s.side == Side.SELL && b.good.equals(s.good) && b.price >= s.price) {
                            throw new GameTestAssertException(what + ": eigene Orders kreuzen sich bei " + i);
                        }
                    }
                }
            }
            for (int g = 0; g < 2; g++) {
                Exchange.Info info = x.info(goods[g], step);
                check(info.bid() == 0 || info.ask() == 0 || info.bid() < info.ask(), what + ": Buch gekreuzt (Gebot " + info.bid() + ", Angebot " + info.ask() + ")");
                long sum = 0;
                for (int i = 0; i < 4; i++) {
                    sum += inv[i][g] + x.stock(who[i], goods[g]);
                    for (Exchange.Order o : x.ordersOf(who[i])) if (o.good.equals(goods[g]) && o.side == Side.SELL) sum += o.qty;
                }
                check(sum == 4 * (200 + 500), what + ": Waren nicht erhalten (" + goods[g] + ": " + sum + ")");
            }
        }
        h.succeed();
    }
}
