package com.sqc.nuclearsq.apps;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.economy.Auctions;
import com.sqc.nuclearsq.economy.BankException;
import com.sqc.nuclearsq.economy.BankNet;
import com.sqc.nuclearsq.economy.EconomyData;
import com.sqc.nuclearsq.network.PacketLimit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class AuctionMonitor {
    private AuctionMonitor() {}

    private static final java.util.Set<String> ERRORS = java.util.Set.of("empty", "price", "buyout", "hours", "full", "limit", "gone", "own",
            "low", "money", "not_own", "has_bids", "nothing");

    public static boolean inReach(ServerPlayer sp, BlockPos pos) {
        return sp.level().isLoaded(pos) && sp.level().getBlockState(pos).getBlock() instanceof AuctionMonitorBlock
                && sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= LoginMonitor.REACH_SQR;
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static void handle(AuctionPayloads.Action a, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && PacketLimit.allow(sp, "app_auction", 6)) apply(sp, a);
        });
    }

    public static String apply(ServerPlayer sp, AuctionPayloads.Action a) {
        BlockPos pos = a.pos();
        if (!inReach(sp, pos)) return "far";
        String msg = "";
        boolean ok = false;
        if (a.op() != AuctionPayloads.OPEN) {
            if (!BankNet.online(sp.level(), pos)) msg = "offline";
            else if (!AppSessions.use(sp)) msg = "not_in";
            else {
                try {
                    msg = act(sp, a);
                    ok = true;
                } catch (BankException e) {
                    msg = ERRORS.contains(e.getMessage()) ? e.getMessage() : "error";
                } catch (ArithmeticException e) {
                    msg = "error";
                }
            }
        }
        if (ok) sp.level().playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.5f, 1.2f);
        send(sp, pos, msg, ok);
        return msg;
    }

    private static String act(ServerPlayer sp, AuctionPayloads.Action a) {
        EconomyData d = EconomyData.get(sp.server);
        Auctions au = d.auctions();
        long now = now(sp.server);
        UUID me = sp.getUUID();
        String name = sp.getGameProfile().getName();
        GlobalPos at = GlobalPos.of(sp.level().dimension(), a.pos());
        d.throttle(me, now);
        switch (a.op()) {
            case AuctionPayloads.CREATE -> {
                ItemStack hand = sp.getMainHandItem();
                if (hand.isEmpty()) throw new BankException("empty");
                au.create(me, name, hand, a.a(), a.b(), a.c(), at, now);
                sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return "created";
            }
            case AuctionPayloads.BID, AuctionPayloads.BUYOUT -> {
                Auctions.Auction x = au.get(a.id());
                if (x == null) throw new BankException("gone");
                UUID before = x.bidder;
                Auctions.Settled s = au.bid(me, name, a.id(), a.op() == AuctionPayloads.BUYOUT ? x.buyout : a.a(), at, now);
                if (before != null && !before.equals(me)) notify(sp.server, before, "outbid", x);
                if (s != null) {
                    announce(sp.server, s);
                    return "bought";
                }
                return "bid";
            }
            case AuctionPayloads.CANCEL -> {
                au.cancel(me, a.id(), now);
                return "cancelled";
            }
            case AuctionPayloads.TAKE -> {
                List<ItemStack> got = au.take(me, at, 36);
                if (got.isEmpty()) throw new BankException("nothing");
                for (ItemStack s : got) {
                    if (!sp.getInventory().add(s)) au.putBack(me, at, s);
                }
                return "taken";
            }
            default -> throw new BankException("unknown");
        }
    }

    private static void notify(MinecraftServer server, UUID who, String key, Auctions.Auction a) {
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p != null) p.displayClientMessage(Component.translatable("message.nuclearsq.app_auction." + key, a.stack.getHoverName(), a.bid), false);
    }

    private static void announce(MinecraftServer server, Auctions.Settled s) {
        Auctions.Auction a = s.auction();
        if (s.winner() == null) {
            notify(server, a.seller, "expired", a);
            return;
        }
        notify(server, a.seller, "sold", a);
        notify(server, s.winner(), "won", a);
        ServerPlayer seller = server.getPlayerList().getPlayer(a.seller);
        if (seller != null) com.sqc.nuclearsq.progression.Titles.mark(seller, "rank:auction");
    }

    public static void send(ServerPlayer sp, BlockPos pos, String msg, boolean ok) {
        if (sp.connection == null) return;
        EconomyData d = EconomyData.get(sp.server);
        Auctions au = d.auctions();
        UUID me = sp.getUUID();
        if (au.soldBy(me) > 0) com.sqc.nuclearsq.progression.Titles.mark(sp, "rank:auction");
        long now = now(sp.server);
        GlobalPos at = GlobalPos.of(sp.level().dimension(), pos);
        List<AuctionPayloads.Entry> entries = new ArrayList<>();
        for (Auctions.Auction a : au.list()) {
            entries.add(new AuctionPayloads.Entry(a.id, a.stack, d.name(a.seller), a.bidder == null ? a.start : a.bid, a.minBid(), a.buyout, a.bids,
                    Math.max(0, a.end - now), a.seller.equals(me), me.equals(a.bidder)));
        }
        EconomyData.Account acc = null;
        try {
            acc = d.account(me);
        } catch (BankException ignored) {
        }
        PacketDistributor.sendToPlayer(sp, new AuctionPayloads.State(pos, AppSessions.loggedIn(sp), BankNet.link(sp.level(), pos).ordinal(),
                acc == null ? 0 : acc.balance, entries, au.pickupsAt(me, at), au.pickupsElsewhere(me, at), msg, ok));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) return;
        EconomyData d = EconomyData.get(server);
        if (d.auctions().count() == 0) return;
        for (Auctions.Settled s : d.auctions().tick(now(server))) announce(server, s);
    }
}
