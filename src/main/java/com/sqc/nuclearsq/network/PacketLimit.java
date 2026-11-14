package com.sqc.nuclearsq.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.sqc.nuclearsq.NuclearSQ;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class PacketLimit {
    private PacketLimit() {}

    private static final Map<UUID, Map<String, long[]>> SEEN = new ConcurrentHashMap<>();

    public static boolean allow(Player p, String kind, int max) {
        long sec = p.level().getGameTime() / 20;
        long[] w = SEEN.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).computeIfAbsent(kind, k -> new long[2]);
        if (w[0] != sec) {
            w[0] = sec;
            w[1] = 0;
        }
        return ++w[1] <= max;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SEEN.remove(event.getEntity().getUUID());
    }
}
