package com.sqc.nuclearsq.apps;

import com.sqc.nuclearsq.economy.BankNet;
import com.sqc.nuclearsq.network.PacketLimit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class LoginMonitor {
    private LoginMonitor() {}

    public static final double REACH_SQR = 8.0 * 8.0;

    public static boolean inReach(ServerPlayer sp, BlockPos pos) {
        return sp.level().isLoaded(pos) && sp.level().getBlockState(pos).getBlock() instanceof LoginMonitorBlock
                && sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQR;
    }

    public static void handle(AppPayloads.Action a, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && PacketLimit.allow(sp, "app_login", 4)) apply(sp, a);
        });
    }

    public static String apply(ServerPlayer sp, AppPayloads.Action a) {
        BlockPos pos = a.pos();
        if (!inReach(sp, pos)) return "far";
        AppAccounts acc = AppAccounts.get(sp.server);
        long now = sp.server.overworld().getGameTime();
        boolean online = BankNet.online(sp.level(), pos);
        String name = sp.getGameProfile().getName();
        String msg = "";
        boolean ok = false;
        switch (a.op()) {
            case AppPayloads.CREATE, AppPayloads.LOGIN, AppPayloads.CHANGE -> {
                if (!online) {
                    msg = "offline";
                    break;
                }
                if (a.op() == AppPayloads.CHANGE && !AppSessions.loggedIn(sp)) {
                    msg = "not_in";
                    break;
                }
                AppAccounts.Result r = switch (a.op()) {
                    case AppPayloads.CREATE -> acc.create(sp.getUUID(), name, a.a(), a.b());
                    case AppPayloads.LOGIN -> acc.login(sp.getUUID(), name, a.a(), now);
                    default -> acc.change(sp.getUUID(), a.a(), a.b(), a.c(), now);
                };
                ok = r == AppAccounts.Result.OK;
                if (ok && a.op() != AppPayloads.CHANGE) AppSessions.login(sp);
                msg = ok ? (a.op() == AppPayloads.CREATE ? "created" : a.op() == AppPayloads.LOGIN ? "welcome" : "changed") : r.key();
            }
            case AppPayloads.LOGOUT -> {
                AppSessions.logout(sp);
                msg = "bye";
                ok = true;
            }
            default -> { }
        }
        if (ok) sp.level().playSound(null, pos, a.op() == AppPayloads.LOGOUT ? SoundEvents.NOTE_BLOCK_BASS.value() : SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.BLOCKS, 0.5f, 1.4f);
        send(sp, pos, msg, ok);
        return msg;
    }

    public static void send(ServerPlayer sp, BlockPos pos, String msg, boolean ok) {
        AppAccounts acc = AppAccounts.get(sp.server);
        long now = sp.server.overworld().getGameTime();
        int wait = (int) ((acc.lockedFor(sp.getUUID(), now) + 19) / 20);
        int minutes = (int) ((AppSessions.remaining(sp) + 1199) / 1200);
        AppPayloads.State s = new AppPayloads.State(pos, sp.getGameProfile().getName(), acc.has(sp.getUUID()), AppSessions.loggedIn(sp),
                BankNet.link(sp.level(), pos).ordinal(), msg, ok, wait, minutes);
        if (sp.connection != null) PacketDistributor.sendToPlayer(sp, s);
    }
}
