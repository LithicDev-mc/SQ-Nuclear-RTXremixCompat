package com.sqc.nuclearsq.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class DataNet {
    private DataNet() {}

    public static final int MAX_CABLES = 8192;

    public static List<DataSource> scan(Level level, BlockPos start) {
        com.sqc.nuclearsq.machine.big.BigMachineBlockEntity desk = ControlDesk.at(level, start);
        return desk != null ? ControlDesk.scan(level, desk) : scan(level, List.of(start));
    }

    public static boolean isTerminal(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof DataDevice || ControlDesk.at(level, pos) != null;
    }

    public static List<DataSource> scan(Level level, Collection<BlockPos> starts) {
        return scan(level, starts, null, null);
    }

    public record Result(List<DataSource> sources, boolean incomplete, Set<Long> chunks, boolean capped) {}

    private static final class Probe {
        boolean cut;
        boolean capped;
        final Set<Long> chunks = new HashSet<>();
        List<BlockPos> cables;
        List<BlockPos> devices;
    }

    public record Trace(List<BlockPos> cables, List<DataSource> sources, Map<BlockPos, BlockPos> ports, List<BlockPos> devices,
                        boolean incomplete, boolean capped) {}

    public static Trace trace(Level level, Collection<BlockPos> starts) {
        Probe probe = new Probe();
        probe.cables = new ArrayList<>();
        probe.devices = new ArrayList<>();
        Map<BlockPos, BlockPos> ports = new LinkedHashMap<>();
        List<DataSource> found = scan(level, starts, ports, probe);
        for (BlockPos st : starts) {
            if (level.getBlockState(st).getBlock() instanceof DataCableBlock && !probe.cables.contains(st)) probe.cables.add(0, st);
        }
        return new Trace(probe.cables, found, ports, probe.devices, probe.cut || probe.capped, probe.capped);
    }

    public static Result scanChecked(Level level, Collection<BlockPos> starts) {
        return scanChecked(level, starts, MAX_CABLES);
    }

    public static Result scanChecked(Level level, Collection<BlockPos> starts, int maxCables) {
        Probe probe = new Probe();
        List<DataSource> found = scan(level, starts, null, probe, maxCables);
        return new Result(found, probe.cut || probe.capped, probe.chunks, probe.capped);
    }

    public static ControlWallBlockEntity.Tile capTile(BlockPos pos) {
        List<ControlWallBlockEntity.Line> lines = new ArrayList<>();
        lines.add(new ControlWallBlockEntity.Line(net.minecraft.network.chat.Component.translatable("data.nuclearsq.cap.cables"),
                MAX_CABLES + " / " + MAX_CABLES, 1f, true));
        return new ControlWallBlockEntity.Tile(pos, net.minecraft.network.chat.Component.translatable("data.nuclearsq.cap.title"), DataSource.ALARM,
                net.minecraft.network.chat.Component.translatable("data.nuclearsq.cap.state"), lines);
    }

    public static List<BlockPos> scanPorts(Level level, BlockPos start) {
        return scanPorts(level, List.of(start));
    }

    public static List<BlockPos> scanPorts(Level level, Collection<BlockPos> starts) {
        Map<BlockPos, BlockPos> ports = new LinkedHashMap<>();
        List<BlockPos> out = new ArrayList<>();
        for (DataSource s : scan(level, starts, ports, null)) out.add(ports.get(s.pos()));
        return out;
    }

    @Nullable
    public static List<DataSource> read(Level level, List<BlockPos> ports) {
        List<DataSource> out = new ArrayList<>(ports.size());
        for (BlockPos p : ports) {
            DataSource s = ChunkGuard.loaded(level, p) && DataSources.isPort(level, p) ? DataSources.at(level, p) : null;
            if (s == null) return null;
            out.add(s);
        }
        return out;
    }

    private static List<DataSource> scan(Level level, Collection<BlockPos> starts, @Nullable Map<BlockPos, BlockPos> ports, @Nullable Probe probe) {
        return scan(level, starts, ports, probe, MAX_CABLES);
    }

    private static List<DataSource> scan(Level level, Collection<BlockPos> starts, @Nullable Map<BlockPos, BlockPos> ports, @Nullable Probe probe,
                                         int maxCables) {
        Map<BlockPos, DataSource> found = new LinkedHashMap<>();
        Set<BlockPos> seen = new HashSet<>(starts);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(starts);
        int cables = 0;
        if (probe != null) for (BlockPos st : starts) probe.chunks.add(net.minecraft.world.level.ChunkPos.asLong(st));
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (DataCableBlock.off(level, p, d) || !seen.add(n)) continue;
                if (!ChunkGuard.loaded(level, n)) {
                    if (probe != null) {
                        probe.cut = true;
                        probe.chunks.add(net.minecraft.world.level.ChunkPos.asLong(n));
                    }
                    continue;
                }
                if (level.getBlockState(n).getBlock() instanceof DataCableBlock) {
                    if (DataCableBlock.off(level, n, d.getOpposite())) {
                        seen.remove(n);
                        continue;
                    }
                    if (++cables <= maxCables) queue.add(n);
                    else if (probe != null) probe.capped = true;
                    if (probe != null) probe.chunks.add(net.minecraft.world.level.ChunkPos.asLong(n));
                    if (probe != null && probe.cables != null && probe.cables.size() < maxCables) probe.cables.add(n);
                } else if (DataSources.isPort(level, n)) {
                    DataSource s = DataSources.at(level, n);
                    if (s == null && probe != null && DataSources.controllerUnloaded(level, n)) probe.cut = true;
                    if (s != null && found.putIfAbsent(s.pos(), s) == null && ports != null) ports.put(s.pos(), n);
                    bridge(level, n, seen, queue);
                } else if (probe != null && probe.devices != null && isTerminal(level, n)) {
                    probe.devices.add(n);
                }
            }
        }
        BlockPos o = starts.iterator().next();
        List<DataSource> out = new ArrayList<>(found.values());
        out.sort(Comparator.comparingDouble((DataSource s) -> s.pos().distSqr(o)).thenComparing(s -> s.pos()));
        return out;
    }

    public static void bridge(Level level, BlockPos port, Set<BlockPos> seen, java.util.Queue<BlockPos> queue) {
        if (!(level.getBlockEntity(port) instanceof com.sqc.nuclearsq.machine.big.BigPortBlockEntity bp)) return;
        if (!(bp.findController() instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity c)) return;
        BlockPos other = c.dataBridge(bp.portName());
        if (other != null && seen.add(other)) queue.add(other);
    }

    @Nullable
    public static DataSource find(Level level, Collection<BlockPos> starts, BlockPos source) {
        for (DataSource s : scan(level, starts)) if (s.pos().equals(source)) return s;
        return null;
    }

    @Nullable
    public static DataSource find(Level level, BlockPos start, BlockPos source) {
        for (DataSource s : scan(level, start)) if (s.pos().equals(source)) return s;
        return null;
    }
}
