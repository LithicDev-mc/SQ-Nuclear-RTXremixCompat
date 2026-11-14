package com.sqc.nuclearsq.radiation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.network.RadiationDebugPayload;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class RadiationDebug {
    private RadiationDebug() {}

    private static final Set<UUID> ENABLED = new HashSet<>();

    public static boolean enabled(ServerPlayer sp) {
        return ENABLED.contains(sp.getUUID());
    }

    public static void set(ServerPlayer sp, boolean on) {
        if (on) {
            ENABLED.add(sp.getUUID());
        } else if (ENABLED.remove(sp.getUUID()) && !(sp instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(sp, new RadiationDebugPayload(new long[0], new float[0], new byte[0]));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ENABLED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ENABLED.clear();
    }
}
