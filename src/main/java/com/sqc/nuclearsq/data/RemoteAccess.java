package com.sqc.nuclearsq.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.sqc.nuclearsq.NuclearSQ;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class RemoteAccess {
    private RemoteAccess() {}

    private static final double REACH_SQR = 64;

    private record Grant(BlockPos machine, BlockPos terminal, ResourceKey<Level> dim) {}

    private static final Map<UUID, Grant> GRANTS = new ConcurrentHashMap<>();

    public static void grant(ServerPlayer player, BlockPos machine, BlockPos terminal) {
        GRANTS.put(player.getUUID(), new Grant(machine.immutable(), terminal.immutable(), player.level().dimension()));
    }

    public static void revoke(Player player) {
        GRANTS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        revoke(event.getEntity());
    }

    private static boolean near(Player p, BlockPos pos) {
        return p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQR;
    }

    public static boolean inReach(Player player, BlockPos pos) {
        if (near(player, pos)) return true;
        Grant g = GRANTS.get(player.getUUID());
        return g != null && g.machine.equals(pos) && g.dim == player.level().dimension() && near(player, g.terminal)
                && DataNet.isTerminal(player.level(), g.terminal);
    }
}
