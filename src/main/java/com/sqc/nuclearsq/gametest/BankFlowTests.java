package com.sqc.nuclearsq.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.economy.BankTerminalBlockEntity;
import com.sqc.nuclearsq.economy.Coins;
import com.sqc.nuclearsq.economy.EconomyData;
import com.sqc.nuclearsq.economy.EconomyRegistry;
import com.sqc.nuclearsq.economy.Exchange;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class BankFlowTests {
    private BankFlowTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "economy_flow", timeoutTicks = 400)
    public static void wholeBankFlow(GameTestHelper h) {
        if (!ModList.get().isLoaded("computercraft")) {
            h.succeed();
            return;
        }
        CcFlow.run(h);
    }

    private static final class CcFlow {
        static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-00000000f101");
        static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-00000000f102");
        static final BlockPos T1 = new BlockPos(3, 2, 3), M1 = new BlockPos(4, 2, 3), S = new BlockPos(8, 2, 3), MS = new BlockPos(7, 2, 3),
                T2 = new BlockPos(3, 2, 9), M2 = new BlockPos(4, 2, 9);

        static BankTerminalBlockEntity t1, t2;
        static ServerPlayer p1, p2;
        static com.sqc.nuclearsq.compat.cc.BankPeripheral c1, c2;
        static EconomyData d;
        static GameTestHelper h;

        interface Step {
            void run() throws Exception;
        }

        static void check(boolean ok, String msg) {
            if (!ok) throw new GameTestAssertException(msg);
        }

        static void step(String name, Step s) {
            try {
                s.run();
            } catch (GameTestAssertException e) {
                throw e;
            } catch (Exception e) {
                throw new GameTestAssertException(name + ": " + e);
            }
        }

        static <T> T confirmed(BankTerminalBlockEntity t, ServerPlayer p, Callable<T> c) throws Exception {
            try {
                return c.call();
            } catch (dan200.computercraft.api.lua.LuaException e) {
                if (e.getMessage() == null || !e.getMessage().contains("confirmation required")) throw e;
            }
            check(t.confirmPending(p), "Rechtsklick bestaetigt nichts (kein Auftrag vorgemerkt)");
            return c.call();
        }

        static void ensureBen(long min) throws Exception {
            long have = balance(U2);
            if (have >= min) return;
            long give = min - have;
            check(balance(U1) >= give, "Anna hat nicht genug, um Ben aufzufuellen (" + balance(U1) + " < " + give + ")");
            confirmed(t1, p1, () -> {
                c1.transfer("FlowBen", give);
                return null;
            });
            check(balance(U2) >= min, "Ben hat nach dem Auffuellen immer noch zu wenig: " + balance(U2));
        }

        static long balance(UUID u) {
            return d.account(u).balance;
        }

        static long coinsIn(BankTerminalBlockEntity t) {
            long sum = 0;
            for (ItemStack s : t.items()) sum += Coins.value(s);
            return sum;
        }

        static long count(BankTerminalBlockEntity t, net.minecraft.world.item.Item item) {
            long n = 0;
            for (ItemStack s : t.items()) if (s.is(item)) n += s.getCount();
            return n;
        }

        static ServerPlayer player(UUID id, String name, BlockPos near) {
            ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(id, name));
            Vec3 at = Vec3.atCenterOf(h.absolutePos(near).above());
            p.setPos(at.x, at.y, at.z);
            BankTerminalBlockEntity.registerTestPlayer(p);
            return p;
        }

        static void run(GameTestHelper helper) {
            h = helper;
            h.setBlock(T1, EconomyRegistry.TERMINAL.get());
            h.setBlock(M1, EconomyRegistry.MODEM.get());
            h.setBlock(S, EconomyRegistry.SERVER.get());
            h.setBlock(MS, EconomyRegistry.MODEM.get());
            h.setBlock(T2, EconomyRegistry.TERMINAL.get());
            h.setBlock(M2, EconomyRegistry.MODEM.get());
            BankTerminalBlockEntity.clearTestPlayers();
            h.runAfterDelay(3, () -> step("Aufbau", CcFlow::setup));
            h.runAfterDelay(30, () -> step("Konto und Ueberweisung", CcFlow::accounts));
            h.runAfterDelay(55, () -> step("Anforderung und Kredit", CcFlow::requestsAndLoans));
            h.runAfterDelay(80, () -> step("Auktion", CcFlow::auction));
            h.runAfterDelay(105, () -> step("Boerse", CcFlow::exchange));
            h.runAfterDelay(125, () -> step("Chat", CcFlow::chat));
            h.runAfterDelay(135, () -> step("Abschluss", CcFlow::finish));
        }

        static void setup() throws Exception {
            t1 = (BankTerminalBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(T1));
            t2 = (BankTerminalBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(T2));
            check(t1 != null && t2 != null, "Terminals ohne BlockEntity");
            d = EconomyData.get(h.getLevel().getServer());
            p1 = player(U1, "FlowAnna", T1);
            p2 = player(U2, "FlowBen", T2);
            check(t1.login(p1) && t2.login(p2), "Anmelden schlaegt fehl");
            c1 = new com.sqc.nuclearsq.compat.cc.BankPeripheral(h.getLevel(), h.absolutePos(T1));
            c2 = new com.sqc.nuclearsq.compat.cc.BankPeripheral(h.getLevel(), h.absolutePos(M2));
            Map<String, Object> st = c1.status();
            check(Boolean.TRUE.equals(st.get("online")) && "online".equals(st.get("link")), "Terminal 1 nicht online: " + st);
            check(Boolean.TRUE.equals(st.get("loggedIn")) && "FlowAnna".equals(st.get("user")), "Anmeldung nicht sichtbar: " + st);
            Map<String, Object> st2 = c2.status();
            check(Boolean.TRUE.equals(st2.get("online")) && "FlowBen".equals(st2.get("user")), "Terminal 2 am Modem nicht online: " + st2);
            check(d.invariantHolds(), "Invariante schon vor dem Start verletzt");
        }

        static long base1, base2;

        static void accounts() throws Exception {
            long b1 = balance(U1), b2 = balance(U2);
            base1 = b1;
            base2 = b2;
            t1.setItem(0, new ItemStack(EconomyRegistry.COIN_BAR.get(), 5));
            check(c1.deposit() == 50_000, "Einzahlen liefert nicht 50000");
            check(coinsIn(t1) == 0, "Muenzen blieben im Behaelter");
            check(balance(U1) == b1 + 50_000, "Kontostand nach Einzahlen");
            try {
                c1.withdraw(100);
                check(false, "Auszahlen ohne Bestaetigung lief durch");
            } catch (dan200.computercraft.api.lua.LuaException expected) {
                check(expected.getMessage().contains("confirmation required"), "falsche Fehlermeldung: " + expected.getMessage());
            }
            check(coinsIn(t1) == 0 && balance(U1) == b1 + 50_000, "vorgemerkte Auszahlung hat schon gebucht");
            confirmed(t1, p1, () -> {
                c1.withdraw(100);
                return null;
            });
            check(coinsIn(t1) == 100 && balance(U1) == b1 + 49_900, "Auszahlen: Muenzen " + coinsIn(t1));
            confirmed(t1, p1, () -> {
                c1.transfer("FlowBen", 300);
                return null;
            });
            check(balance(U1) == b1 + 49_600 && balance(U2) == b2 + 300, "Ueberweisung");
            boolean rejected = false;
            try {
                confirmed(t1, p1, () -> {
                    c1.transfer("FlowAnna", 5);
                    return null;
                });
            } catch (dan200.computercraft.api.lua.LuaException e) {
                rejected = true;
            }
            check(rejected, "Selbstueberweisung angenommen");
            check(balance(U1) == b1 + 49_600 && balance(U2) == b2 + 300, "Selbstueberweisung hat gebucht");
        }

        static void requestsAndLoans() throws Exception {
            long b1 = base1, b2 = base2;
            int req = c2.requestMoney("FlowAnna", 50, Optional.of("Miete"));
            Map<String, Object> rq = c1.requests();
            check(((List<?>) rq.get("incoming")).size() >= 1, "Anforderung kommt bei Anna nicht an");
            check(balance(U1) == b1 + 49_600, "Anforderung hat vor der Bestaetigung gebucht");
            confirmed(t1, p1, () -> {
                c1.answerRequest(req, true);
                return null;
            });
            check(balance(U1) == b1 + 49_550 && balance(U2) == b2 + 350, "Anforderung nicht bezahlt");
            int loan = (int) confirmed(t1, p1, () -> c1.offerLoan("FlowBen", 1000, 10, 5)).intValue();
            check(balance(U1) == b1 + 48_550, "Kredit-Verwahrung nicht abgebucht");
            confirmed(t2, p2, () -> {
                c2.answerLoan(loan, true);
                return null;
            });
            check(balance(U2) == b2 + 1350, "Kredit nicht gutgeschrieben");
            confirmed(t2, p2, () -> {
                c2.repayLoan(loan);
                return null;
            });
            check(balance(U2) == b2 + 250 && balance(U1) == b1 + 49_650, "Rueckzahlung falsch: Ben " + (balance(U2) - b2) + ", Anna " + (balance(U1) - b1));
            check(d.invariantHolds(), "Invariante nach den Zahlungen verletzt");
        }

        static void auction() throws Exception {
            ensureBen(500);
            long b1 = balance(U1), b2 = balance(U2);
            t1.clearContent();
            t1.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
            Map<String, Object> inv = c1.inventory().get(0);
            check("minecraft:iron_ingot".equals(inv.get("item")), "Behaelter-Inhalt falsch: " + inv);
            int offer = confirmed(t1, p1, () -> c1.listItem(1, 200));
            check(t1.items().get(0).isEmpty(), "Ware blieb im Behaelter (Dupe!)");
            Map<String, Object> found = c2.search(new dan200.computercraft.api.lua.ObjectArguments("iron", 1, 10));
            check(((Number) found.get("total")).intValue() >= 1, "Angebot nicht auffindbar: " + found);
            confirmed(t2, p2, () -> {
                c2.buy(offer);
                return null;
            });
            check(balance(U2) == b2 - 200, "Kaeufer nicht belastet");
            t2.clearContent();
            Map<String, Object> got2 = confirmed(t2, p2, () -> c2.claim());
            check(((Number) got2.get("moved")).intValue() == 16 && ((Number) got2.get("waiting")).intValue() == 0, "Abholen meldet falsch: " + got2);
            check(count(t2, Items.IRON_INGOT) == 16, "Kaeufer hat die Ware nicht im Behaelter, dort liegen " + count(t2, Items.IRON_INGOT) + " Eisenbarren");
            Map<String, Object> got1 = confirmed(t1, p1, () -> c1.claim());
            check(((Number) got1.get("money")).longValue() == 190 && balance(U1) == b1 + 190, "Verkaeufer-Erloes (200 minus 5 %) falsch: " + got1);
            check(((Number) confirmed(t1, p1, () -> c1.claim()).get("money")).longValue() == 0, "Erloes zweimal abholbar");
            check(d.invariantHolds(), "Invariante nach der Auktion verletzt");
        }

        static long stockOf(Object unused, com.sqc.nuclearsq.compat.cc.BankPeripheral c, String item) throws Exception {
            for (Map<String, Object> row : c.exchangeStock()) if (item.equals(row.get("item"))) return ((Number) row.get("count")).longValue();
            return 0;
        }

        static void exchange() throws Exception {
            String iron = "minecraft:iron_ingot";
            final long price = 37;
            ensureBen(1000);
            long b1 = balance(U1), b2 = balance(U2);
            t1.clearContent();
            t2.clearContent();
            long stock0 = stockOf(null, c1, iron);
            t1.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            t1.setItem(1, new ItemStack(Items.IRON_INGOT, 36));
            t1.setItem(2, new ItemStack(Items.DIAMOND, 1));
            check(c1.exchangeDeposit() == 101, "Einlagern zaehlt falsch");
            check(count(t1, Items.IRON_INGOT) == 0, "Eisen blieb im Behaelter");
            check(stockOf(null, c1, iron) == stock0 + 100, "Lager zeigt nicht +100 Eisen");
            check(c1.exchangeStock().size() >= 2, "Lager zeigt nicht Eisen und Diamant");
            int historyBefore = ((List<?>) c2.exchangeBook(iron).get("history")).size();
            Map<String, Object> sell = confirmed(t1, p1, () -> c1.exchangePlace("sell", iron, price, 20));
            int sellId = ((Number) sell.get("id")).intValue();
            check(((Number) sell.get("filled")).intValue() == 0 && ((Number) sell.get("remaining")).intValue() == 20, "Verkauf ohne Gegenseite: " + sell);
            Map<String, Object> buy = confirmed(t2, p2, () -> c2.exchangePlace("buy", iron, price, 5));
            check(((Number) buy.get("filled")).intValue() == 5 && ((Number) buy.get("remaining")).intValue() == 0, "Kauf nicht ausgefuehrt: " + buy);
            check(balance(U2) == b2 - 185, "Kaeufer zahlt nicht 185");
            check(balance(U1) == b1 + 181, "Verkaeufer bekommt nicht 185 minus 4 Gebuehr");
            boolean rejected = false;
            try {
                confirmed(t1, p1, () -> c1.exchangePlace("buy", iron, price + 2, 1));
            } catch (dan200.computercraft.api.lua.LuaException e) {
                rejected = true;
            }
            check(rejected, "Selbsthandel angenommen");
            Map<String, Object> book = c2.exchangeBook(iron);
            check(((Number) book.get("last")).longValue() == price, "Kursbuch: letzter Kurs ist nicht " + price + ": " + book.get("last"));
            List<?> history = (List<?>) book.get("history");
            check(history.size() == Math.min(historyBefore + 1, 120), "Kursverlauf waechst nicht um genau einen Abschluss (vorher " + historyBefore + ", jetzt " + history.size() + ")");
            check(((Number) history.get(history.size() - 1)).longValue() == price, "letzter Verlaufspreis ist nicht " + price);
            boolean level = false;
            for (Object o : (List<?>) book.get("asks")) {
                Map<?, ?> row = (Map<?, ?>) o;
                if (((Number) row.get("price")).longValue() == price) level = ((Number) row.get("qty")).longValue() >= 15;
            }
            check(level, "Verkaufsstufe 37 mit mindestens 15 Stueck fehlt im Kursbuch: " + book.get("asks"));
            boolean mine = false;
            for (Map<String, Object> o : c1.exchangeOrders()) if (((Number) o.get("id")).intValue() == sellId) mine = ((Number) o.get("qty")).longValue() == 15;
            check(mine, "Annas offene Order #" + sellId + " mit 15 Stueck fehlt");
            confirmed(t2, p2, () -> {
                c2.exchangeWithdraw(iron, 5);
                return null;
            });
            check(count(t2, Items.IRON_INGOT) == 5, "Auslagern legt keine 5 Eisen in den Behaelter");
            c1.exchangeCancel(sellId);
            for (Map<String, Object> o : c1.exchangeOrders()) check(((Number) o.get("id")).intValue() != sellId, "Order nach Abbruch noch offen");
            check(stockOf(null, c1, iron) == stock0 + 95, "Lager nach Abbruch: erwartet " + (stock0 + 95) + ", ist " + stockOf(null, c1, iron));
            confirmed(t1, p1, () -> {
                c1.exchangeWithdraw(iron, 95);
                return null;
            });
            check(count(t1, Items.IRON_INGOT) == 95, "Zurueckgegebene Ware fehlt");
            check(d.invariantHolds(), "Invariante nach der Boerse verletzt");
        }

        static dan200.computercraft.api.lua.ObjectArguments args(Object... a) {
            return new dan200.computercraft.api.lua.ObjectArguments(a);
        }

        static void chat() throws Exception {
            int first = c1.chatSend(args("", "chat-oeffentlich"));
            int priv = c1.chatSend(args("FlowBen", "chat-privat"));
            int answer = c2.chatSend(args("FlowAnna", "chat-antwort"));
            check(priv > first && answer > priv, "Chat-Nummern steigen nicht");
            List<Map<String, Object>> annaSees = c1.chatFetch(args(first - 1));
            List<Map<String, Object>> benSees = c2.chatFetch(args(first));
            check(annaSees.size() == 3 && benSees.size() == 2, "Chat: Anna sieht " + annaSees.size() + ", Ben " + benSees.size());
            check("FlowAnna".equals(annaSees.get(0).get("from")) && "".equals(annaSees.get(0).get("to")), "Chat: oeffentlicher Absender falsch");
            check("FlowAnna".equals(benSees.get(0).get("from")) && "FlowBen".equals(benSees.get(0).get("to")), "Chat: Ben sieht die private Nachricht nicht");
            check("FlowBen".equals(benSees.get(1).get("from")), "Chat: Antwort hat falschen Absender");
            check(c1.chatFetch(args(answer)).isEmpty(), "Chat: Abruf nach der letzten Nummer liefert noch etwas");
            for (Object[] bad : new Object[][] { { "NiemandHier", "x" }, { "FlowAnna", "ich selbst" }, { "", "   " } }) {
                boolean rejected = false;
                try {
                    c1.chatSend(args(bad));
                } catch (dan200.computercraft.api.lua.LuaException e) {
                    rejected = true;
                }
                check(rejected, "Chat: ungueltige Nachricht angenommen: " + bad[0]);
            }
        }

        static void finish() throws Exception {
            t1.logout();
            List<Map<String, Object>> ticker = c1.exchangeTicker();
            check(ticker.size() == Exchange.goods().size(), "Kurstafel zeigt nicht alle Waren");
            boolean ironSeen = false;
            for (Map<String, Object> row : ticker) {
                check(!row.containsKey("stock") && !row.containsKey("seller") && !row.containsKey("user"), "Kurstafel enthaelt persoenliche Daten: " + row.keySet());
                if ("minecraft:iron_ingot".equals(row.get("item"))) {
                    ironSeen = true;
                    check(((Number) row.get("last")).longValue() == 37, "Kurstafel: Eisenkurs ist nicht der letzte Abschluss (37)");
                    check(((List<?>) row.get("history")).size() >= 1, "Kurstafel ohne Verlauf");
                }
            }
            check(ironSeen, "Kurstafel ohne Eisen");
            boolean rejected = false;
            try {
                c1.balance();
            } catch (dan200.computercraft.api.lua.LuaException e) {
                rejected = true;
            }
            check(rejected, "Kontostand ohne Anmeldung lesbar");
            rejected = false;
            try {
                c1.chatFetch(args());
            } catch (dan200.computercraft.api.lua.LuaException e) {
                rejected = true;
            }
            check(rejected, "Chat ohne Anmeldung lesbar");
            check(d.invariantHolds(), "Invariante am Ende verletzt");
            BankTerminalBlockEntity.clearTestPlayers();
            h.succeed();
        }
    }
}
