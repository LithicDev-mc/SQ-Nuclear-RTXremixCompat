package com.sqc.nuclearsq.storage;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

public final class ChunkTickets {
    private ChunkTickets() {}

    public static final TicketController CONTROLLER = new TicketController(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "assembler"));

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static Set<Long> chunksOf(Collection<BlockPos> positions) {
        Set<Long> out = new HashSet<>();
        for (BlockPos p : positions) out.add(ChunkPos.asLong(p));
        return out;
    }

    static double distSq(long chunk, BlockPos pos) {
        double dx = (ChunkPos.getX(chunk) * 16 + 8) - pos.getX(), dz = (ChunkPos.getZ(chunk) * 16 + 8) - pos.getZ();
        return dx * dx + dz * dz;
    }

    static boolean sync(ServerLevel level, BlockPos owner, Set<Long> current, Set<Long> want) {
        boolean changed = false;
        for (long c : new HashSet<>(current)) {
            if (want.contains(c)) continue;
            CONTROLLER.forceChunk(level, owner, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
            current.remove(c);
            changed = true;
        }
        for (long c : want) {
            if (current.contains(c)) continue;
            CONTROLLER.forceChunk(level, owner, ChunkPos.getX(c), ChunkPos.getZ(c), true, true);
            current.add(c);
            changed = true;
        }
        return changed;
    }
}
