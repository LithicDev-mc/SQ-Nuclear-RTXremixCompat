package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.sqc.nuclearsq.data.ChunkGuard;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class StorageNet {
    private StorageNet() {}

    public static List<BigMachineBlockEntity> racks(Level level, BlockPos start) {
        return racks(level, List.of(start));
    }

    public static List<BigMachineBlockEntity> racks(Level level, java.util.Collection<BlockPos> starts) {
        return scanRacks(level, starts).racks;
    }

    private record Found(List<BigMachineBlockEntity> racks, List<BigMachineBlockEntity> patternRacks, boolean complete, java.util.Set<Long> chunks) {}

    private static Found scanRacks(Level level, java.util.Collection<BlockPos> starts) {
        BlockPos start = starts.iterator().next();
        List<BigMachineBlockEntity> out = new ArrayList<>();
        List<BigMachineBlockEntity> cards = new ArrayList<>();
        DataNet.Result scan = DataNet.scanChecked(level, starts);
        boolean complete = !scan.incomplete();
        java.util.Set<Long> chunks = new java.util.HashSet<>(scan.chunks());
        for (DataSource s : scan.sources()) {
            if (ChunkGuard.loaded(level, s.pos()) && level.getBlockEntity(s.pos()) instanceof BigMachineBlockEntity be && be.rack() != null) {
                (be.rack().isPatternRack() ? cards : out).add(be);
                java.util.List<BlockPos> cells = be.cellPositions();
                if (!ChunkGuard.allLoaded(level, cells)) complete = false;
                for (BlockPos c : cells) chunks.add(net.minecraft.world.level.ChunkPos.asLong(c));
            }
        }
        out.sort(Comparator.comparingInt((BigMachineBlockEntity be) -> -be.rack().priority())
                .thenComparingDouble(be -> be.getBlockPos().distSqr(start)));
        return new Found(out, cards, complete, chunks);
    }

    public record Patterns(List<Pattern> online, List<Pattern> known, boolean complete) {}

    public static Patterns patterns(Level level, java.util.Collection<BlockPos> starts) {
        Found f = scanRacks(level, starts);
        List<Pattern> online = new ArrayList<>(), known = new ArrayList<>();
        for (BigMachineBlockEntity be : f.patternRacks) {
            if (!be.isFormed()) continue;
            List<Pattern> ps = be.rack().patterns();
            known.addAll(ps);
            if (be.rack().online()) online.addAll(ps);
        }
        return new Patterns(online, known, f.complete);
    }

    public static StorageAccess access(Level level, BlockPos start) {
        return access(level, List.of(start));
    }

    public static StorageAccess access(Level level, java.util.Collection<BlockPos> starts) {
        Found f = scanRacks(level, starts);
        return new StorageAccess(level, f.racks, f.complete, f.chunks);
    }

    public static List<BigMachineBlockEntity> assemblers(Level level, BlockPos start) {
        return assemblers(level, List.of(start));
    }

    public static List<BigMachineBlockEntity> assemblers(Level level, java.util.Collection<BlockPos> starts) {
        List<BigMachineBlockEntity> out = new ArrayList<>();
        for (DataSource s : DataNet.scan(level, starts)) {
            if (ChunkGuard.loaded(level, s.pos()) && level.getBlockEntity(s.pos()) instanceof BigMachineBlockEntity be && be.assembler() != null && be.isFormed()) out.add(be);
        }
        return out;
    }
}
