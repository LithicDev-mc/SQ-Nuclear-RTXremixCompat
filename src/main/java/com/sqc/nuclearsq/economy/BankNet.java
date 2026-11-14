package com.sqc.nuclearsq.economy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class BankNet {
    private BankNet() {}

    static final Set<GlobalPos> MODEMS = ConcurrentHashMap.newKeySet();
    static final Set<GlobalPos> SERVERS = ConcurrentHashMap.newKeySet();

    public enum Link {
        ONLINE, NO_SERVER, TERMINAL_NO_MODEM, SERVER_NO_MODEM;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        MODEMS.clear();
        SERVERS.clear();
    }

    private static boolean touchesServer(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.isLoaded(n) && level.getBlockState(n).is(EconomyRegistry.SERVER.get())) return true;
        }
        return false;
    }

    public static boolean hasModem(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.isLoaded(n) && level.getBlockState(n).is(EconomyRegistry.MODEM.get())) return true;
        }
        return false;
    }

    private static void prune(MinecraftServer server) {
        SERVERS.removeIf(g -> stale(server, g, EconomyRegistry.SERVER.get()));
        MODEMS.removeIf(g -> stale(server, g, EconomyRegistry.MODEM.get()));
    }

    private static boolean stale(MinecraftServer server, GlobalPos g, Block block) {
        ServerLevel l = server.getLevel(g.dimension());
        return l == null || (l.isLoaded(g.pos()) && !l.getBlockState(g.pos()).is(block));
    }

    public static Link link(Level level, BlockPos terminal) {
        if (touchesServer(level, terminal)) return Link.ONLINE;
        if (level.getServer() == null) return Link.NO_SERVER;
        prune(level.getServer());
        if (SERVERS.isEmpty()) return Link.NO_SERVER;
        if (!hasModem(level, terminal)) return Link.TERMINAL_NO_MODEM;
        for (GlobalPos g : MODEMS) {
            ServerLevel l = level.getServer().getLevel(g.dimension());
            if (l != null && l.isLoaded(g.pos()) && touchesServer(l, g.pos())) return Link.ONLINE;
        }
        return Link.SERVER_NO_MODEM;
    }

    public static boolean online(Level level, BlockPos terminal) {
        return link(level, terminal) == Link.ONLINE;
    }
}
