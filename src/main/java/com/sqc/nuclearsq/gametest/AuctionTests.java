package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.apps.AppMonitorBlock;
import com.sqc.nuclearsq.apps.AppRegistry;
import com.sqc.nuclearsq.apps.AppSessions;
import com.sqc.nuclearsq.apps.AuctionMonitor;
import com.sqc.nuclearsq.apps.AuctionPayloads;
import com.sqc.nuclearsq.economy.Auctions;
import com.sqc.nuclearsq.economy.BankException;
import com.sqc.nuclearsq.economy.EconomyData;
import com.sqc.nuclearsq.economy.EconomyRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class AuctionTests {
    private AuctionTests() {}

    private static final String FLOOR = "floor";
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000a0c01");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000a0c02");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000a0c03");
    private static final GlobalPos P1 = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 1));
    private static final GlobalPos P2 = GlobalPos.of(Level.OVERWORLD, new BlockPos(9, 64, 9));

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    private static void fails(String what, Runnable r) {
        try {
            r.run();
        } catch (BankException e) {
            return;
        }
        throw new GameTestAssertException(what + ": haette abgelehnt werden muessen");
    }

    @GameTest(template = FLOOR, batch = "app_auction", timeoutTicks = 20)
    public static void biddingReservesAndRefunds(GameTestHelper h) {
        EconomyData d = new EconomyData();
        d.touch(A, "Anna");
        d.touch(B, "Ben");
        d.touch(C, "Cem");
        d.deposit(B, 1000, 0);
        d.deposit(C, 1000, 0);
        Auctions au = d.auctions();
        fails("Startpreis 0", () -> au.create(A, "Anna", new ItemStack(Items.DIAMOND, 3), 0, 0, 1, P1, 0));
        fails("Sofortkauf unter Start", () -> au.create(A, "Anna", new ItemStack(Items.DIAMOND, 3), 100, 50, 1, P1, 0));
        fails("Laufzeit 2 h", () -> au.create(A, "Anna", new ItemStack(Items.DIAMOND, 3), 100, 0, 2, P1, 0));
        Auctions.Auction a = au.create(A, "Anna", new ItemStack(Items.DIAMOND, 3), 100, 0, 1, P1, 0);
        fails("eigenes Gebot", () -> au.bid(A, "Anna", a.id, 200, P1, 1));
        fails("unter Startpreis", () -> au.bid(B, "Ben", a.id, 99, P2, 1));
        au.bid(B, "Ben", a.id, 100, P2, 1);
        check(d.account(B).balance == 900 && d.invariantHolds(), "Gebot nicht reserviert");
        fails("unter Mindestschritt", () -> au.bid(C, "Cem", a.id, 104, P1, 2));
        au.bid(C, "Cem", a.id, 150, P1, 2);
        check(d.account(B).balance == 1000 && d.account(C).balance == 850 && d.invariantHolds(), "Ueberbotener bekommt Coins nicht zurueck");
        au.bid(C, "Cem", a.id, 200, P1, 3);
        check(d.account(C).balance == 800, "Erhoehung des eigenen Gebots falsch abgebucht: " + d.account(C).balance);
        check(au.tick(Auctions.HOUR - 1).isEmpty(), "Auktion endet zu frueh");
        check(au.tick(Auctions.HOUR).size() == 1 && au.count() == 0, "Auktion endet nicht");
        check(d.account(A).balance == 196 && d.invariantHolds(), "Erloes abzueglich 2 % falsch: " + d.account(A).balance);
        check(au.pickupsAt(C, P1).size() == 1 && au.pickupsAt(C, P2).isEmpty(), "Gewinner holt nicht am Bietmonitor ab");
        check(au.pickupsAt(C, P1).get(0).getCount() == 3, "Stapel unvollstaendig");
        check(au.take(C, P1, 36).size() == 1 && au.pickupsAt(C, P1).isEmpty(), "Abholen leert nicht");
        check(au.soldBy(A) == 1, "Verkauf nicht gezaehlt");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "app_auction", timeoutTicks = 20)
    public static void buyoutCancelAndExpiry(GameTestHelper h) {
        EconomyData d = new EconomyData();
        d.touch(A, "Anna");
        d.touch(B, "Ben");
        d.deposit(B, 200, 0);
        Auctions au = d.auctions();
        Auctions.Auction a = au.create(A, "Anna", new ItemStack(Items.EMERALD, 5), 10, 300, 6, P1, 0);
        fails("Sofortkauf ohne Geld", () -> au.bid(B, "Ben", a.id, 900, P2, 1));
        d.deposit(B, 500, 1);
        check(au.bid(B, "Ben", a.id, 900, P2, 2) != null && au.count() == 0, "Sofortkauf beendet die Auktion nicht");
        check(d.account(B).balance == 400 && d.account(A).balance == 294 && d.invariantHolds(), "Sofortkauf zum falschen Preis");
        check(au.pickupsAt(B, P2).size() == 1, "Sofortkauf nicht am Bietmonitor abholbar");

        Auctions.Auction b = au.create(A, "Anna", new ItemStack(Items.IRON_INGOT, 7), 10, 0, 1, P1, 10);
        au.cancel(A, b.id, 11);
        check(au.pickupsAt(A, P1).size() == 1, "Zurueckgezogenes Item nicht am Einstellmonitor");
        Auctions.Auction c = au.create(A, "Anna", new ItemStack(Items.GOLD_INGOT, 2), 10, 0, 1, P2, 20);
        au.bid(B, "Ben", c.id, 10, P1, 21);
        fails("Zurueckziehen mit Gebot", () -> au.cancel(A, c.id, 22));
        Auctions.Auction e = au.create(A, "Anna", new ItemStack(Items.COAL, 1), 10, 0, 1, P2, 30);
        au.tick(30 + Auctions.HOUR);
        check(au.get(e.id) == null && au.pickupsAt(A, P2).size() == 1, "Abgelaufene Auktion nicht am Einstellmonitor zurueck");
        for (int i = au.count(); i < Auctions.MAX_PER_PLAYER; i++) au.create(A, "Anna", new ItemStack(Items.STONE), 1, 0, 48, P1, 40);
        fails("mehr als " + Auctions.MAX_PER_PLAYER + " Auktionen", () -> au.create(A, "Anna", new ItemStack(Items.STONE), 1, 0, 48, P1, 40));

        EconomyData back = EconomyData.load(d.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        check(back.auctions().count() == au.count() && back.auctions().escrowTotal() == au.escrowTotal() && back.invariantHolds(),
                "Auktionen ueberstehen Speichern nicht");
        check(back.auctions().pickupsAt(A, P2).size() == 1 && back.auctions().pickupsAt(B, P2).size() == 1, "Abholungen ueberstehen Speichern nicht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "app_auction", timeoutTicks = 40)
    public static void monitorNeedsLoginAndTakesHandItem(GameTestHelper h) {
        BlockPos mon = new BlockPos(2, 2, 2), server = new BlockPos(3, 2, 2);
        h.setBlock(mon, AppRegistry.AUCTION_MONITOR.get().defaultBlockState().setValue(AppMonitorBlock.FACING, Direction.NORTH));
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(A, "AuctionTester"));
        BlockPos abs = h.absolutePos(mon);
        p.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() - 1.5);
        AppSessions.logout(p);
        EconomyData d = EconomyData.get(h.getLevel().getServer());
        int before = d.auctions().count();
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 4));
            String m = AuctionMonitor.apply(p, new AuctionPayloads.Action(abs, AuctionPayloads.CREATE, 0, 50, 0, 1));
            check("offline".equals(m), "ohne Bank-Server eingestellt: " + m);
            h.setBlock(server, EconomyRegistry.SERVER.get().defaultBlockState());
            m = AuctionMonitor.apply(p, new AuctionPayloads.Action(abs, AuctionPayloads.CREATE, 0, 50, 0, 1));
            check("not_in".equals(m) && p.getMainHandItem().getCount() == 4, "ohne Anmeldung eingestellt: " + m);
            AppSessions.login(p);
            m = AuctionMonitor.apply(p, new AuctionPayloads.Action(abs, AuctionPayloads.CREATE, 0, 50, 0, 1));
            check("created".equals(m) && p.getMainHandItem().isEmpty() && d.auctions().count() == before + 1, "Einstellen: " + m);
            Auctions.Auction a = d.auctions().list().stream().filter(x -> x.seller.equals(A)).reduce((x, y) -> y).orElseThrow();
            m = AuctionMonitor.apply(p, new AuctionPayloads.Action(abs, AuctionPayloads.CANCEL, a.id, 0, 0, 0));
            check("cancelled".equals(m), "Zurueckziehen: " + m);
            m = AuctionMonitor.apply(p, new AuctionPayloads.Action(abs, AuctionPayloads.TAKE, 0, 0, 0, 0));
            check("taken".equals(m) && p.getInventory().countItem(Items.DIAMOND) == 4, "Abholen am Einstellmonitor: " + m);
        } finally {
            AppSessions.logout(p);
        }
        h.succeed();
    }
}
