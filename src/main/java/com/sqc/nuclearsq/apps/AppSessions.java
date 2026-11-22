package com.sqc.nuclearsq.apps;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class AppSessions {
    private AppSessions() {}

    public static final long IDLE_TICKS = 20L * 60 * 10;

    private static final Map<UUID, long[]> SESSIONS = new ConcurrentHashMap<>();

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static void login(ServerPlayer p) {
        long t = now(p.server);
        SESSIONS.put(p.getUUID(), new long[]{t, t});
        sync(p);
    }

    public static void logout(ServerPlayer p) {
        if (SESSIONS.remove(p.getUUID()) != null) sync(p);
    }

    public static boolean loggedIn(ServerPlayer p) {
        long[] s = SESSIONS.get(p.getUUID());
        return s != null && now(p.server) - s[1] <= IDLE_TICKS;
    }

    public static boolean use(ServerPlayer p) {
        if (!loggedIn(p)) return false;
        SESSIONS.get(p.getUUID())[1] = now(p.server);
        return true;
    }

    public static long remaining(ServerPlayer p) {
        long[] s = SESSIONS.get(p.getUUID());
        return s == null ? 0 : Math.max(0, IDLE_TICKS - (now(p.server) - s[1]));
    }

    public static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        PacketDistributor.sendToPlayer(p, new AppPayloads.Session(loggedIn(p), p.getGameProfile().getName()));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (SESSIONS.isEmpty() || server.getTickCount() % 20 != 0) return;
        long t = now(server);
        for (var it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (t - e.getValue()[1] <= IDLE_TICKS && t >= e.getValue()[0]) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                sync(p);
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.nuclearsq.app_login.timeout"), false);
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }
}
