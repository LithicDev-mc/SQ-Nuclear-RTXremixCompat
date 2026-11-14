package com.sqc.nuclearsq.nanobot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class NanoSwarms {
    private NanoSwarms() {}

    private static final Map<UUID, NanoSwarm> SWARMS = new HashMap<>();

    @Nullable
    public static NanoSwarm get(ServerPlayer sp) {
        return SWARMS.get(sp.getUUID());
    }

    public static NanoSwarm start(ServerPlayer sp, NanoTask task) {
        NanoSwarm old = SWARMS.get(sp.getUUID());
        if (old != null && !old.isEnded()) return old;
        NanoSwarm s = new NanoSwarm(sp, task);
        SWARMS.put(sp.getUUID(), s);
        return s;
    }

    public static void stop(ServerPlayer sp) {
        NanoSwarm s = SWARMS.remove(sp.getUUID());
        if (s != null) s.finish(sp);
    }

    public static void refresh(ServerPlayer sp) {
        NanoSwarm s = SWARMS.get(sp.getUUID());
        if (s != null) s.refreshSettings(sp);
    }

    public static int count() {
        return SWARMS.size();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || SWARMS.isEmpty()) return;
        NanoSwarm s = SWARMS.get(sp.getUUID());
        if (s != null && !s.tick(sp)) SWARMS.remove(sp.getUUID(), s);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) stop(sp);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) stop(sp);
        SWARMS.clear();
    }
}
