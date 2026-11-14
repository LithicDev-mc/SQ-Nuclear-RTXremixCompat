package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PowerMonitorBlockEntity extends BlockEntity {
    public static final int PERIOD = 20;
    public static final int FINE_MAX = 120, COARSE_MAX = 120, COARSE_EVERY = 30;
    private static final int NET_PERIODS = 10;
    public static final int WORLD_SAMPLES = 60;
    private static final int WORLD_EVERY = 2;

    private final List<PowerSample> fine = new ArrayList<>();
    private final List<PowerSample> coarse = new ArrayList<>();
    private long fineCount, coarseCount;
    private final long[] acc = new long[PowerSample.FIELDS];
    private int accN;
    private PowerSummary summary = PowerSummary.EMPTY;
    private final Map<BlockPos, Long> lastUsed = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;
    @Nullable private List<BlockPos> netPorts;
    private int netAge;
    private boolean slowRange;

    public PowerMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(DataRegistry.POWER_BE.get(), pos, state);
    }

    public List<PowerSample> fine() {
        return fine;
    }

    public List<PowerSample> coarse() {
        return coarse;
    }

    public long fineCount() {
        return fineCount;
    }

    public long coarseCount() {
        return coarseCount;
    }

    public PowerSummary summary() {
        return summary;
    }

    public PowerSample latest() {
        return fine.isEmpty() ? PowerSample.ZERO : fine.get(fine.size() - 1);
    }

    public boolean slowRange() {
        return slowRange;
    }

    public boolean toggleRange() {
        slowRange = !slowRange;
        setChanged();
        sync();
        return slowRange;
    }

    private void sync() {
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % PERIOD != Math.floorMod(worldPosition.asLong(), PERIOD)) return;
        if (!PowerMonitorBlock.layout(level, worldPosition).origin().equals(worldPosition)) return;
        scan();
    }

    public void scan() {
        if (level == null || level.isClientSide) return;
        List<DataSource> net = netPorts == null || ++netAge >= NET_PERIODS ? null : DataNet.read(level, netPorts);
        if (net == null) {
            List<BlockPos> cells = PowerMonitorBlock.layout(level, worldPosition).cells();
            net = new ArrayList<>(DataNet.scanChecked(level, cells).sources());
            java.util.Set<BlockPos> ports = new java.util.LinkedHashSet<>(DataNet.scanPorts(level, cells));
            List<BlockPos> cable = cablePorts(cells);
            ports.addAll(cable);
            netPorts = new ArrayList<>(ports);
            List<DataSource> extra = DataNet.read(level, cable);
            if (extra != null) net.addAll(extra);
            netAge = 0;
        }
        sample(net, level.getGameTime());
    }

    private List<BlockPos> cablePorts(List<BlockPos> cells) {
        Set<BlockPos> seen = new HashSet<>(), out = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>();
        for (BlockPos c : cells) for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = c.relative(d);
            if (!cells.contains(n) && isPowerCable(n) && seen.add(n)) todo.add(n);
        }
        while (!todo.isEmpty() && seen.size() <= DataNet.MAX_CABLES) {
            BlockPos c = todo.poll();
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = c.relative(d);
                if (cells.contains(n) || !ChunkGuard.loaded(level, n)) continue;
                if (isPowerCable(n)) {
                    if (seen.add(n)) todo.add(n);
                } else if (DataSources.isPort(level, n)) out.add(n);
            }
        }
        return new ArrayList<>(out);
    }

    private boolean isPowerCable(BlockPos p) {
        return ChunkGuard.loaded(level, p) && level.getBlockEntity(p) instanceof com.sqc.nuclearsq.transport.ConduitBlockEntity c && c.energySide(null) != null;
    }

    public void sample(List<DataSource> sources, long now) {
        long elapsed = lastTick == Long.MIN_VALUE ? PERIOD : Math.max(1, now - lastTick);
        lastTick = now;
        long prod = 0, cons = 0, stored = 0, cap = 0, in = 0, out = 0, peak = 0;
        PowerSummary.Group producers = new PowerSummary.Group(), consumers = new PowerSummary.Group(), storages = new PowerSummary.Group();
        Set<BlockPos> present = new HashSet<>(), done = new HashSet<>();
        int plants = 0;
        for (DataSource s : sources) {
            PowerFlow f = s.power();
            if (f == null || !done.add(s.pos())) continue;
            plants++;
            switch (f.kind()) {
                case PRODUCER -> {
                    long p = Math.max(0, Math.round(f.now()));
                    prod += p;
                    peak += Math.max(Math.round(f.peak()), p);
                    producers.add(s.name(), p, Math.max(Math.round(f.peak()), p));
                }
                case CONSUMER -> {
                    present.add(s.pos());
                    Long before = lastUsed.put(s.pos(), f.usedTotal());
                    long used = before == null || f.usedTotal() < before ? 0 : f.usedTotal() - before;
                    long rate = Math.round(used / (double) elapsed);
                    cons += rate;
                    consumers.add(s.name(), rate, 0);
                }
                case STORAGE -> {
                    stored += f.stored();
                    cap += f.capacity();
                    in += Math.round(f.in());
                    out += Math.round(f.out());
                    storages.add(s.name(), f.stored(), f.capacity());
                }
            }
        }
        lastUsed.keySet().retainAll(present);
        List<PowerSummary.Item> shownConsumers = consumers.top();
        shownConsumers.removeIf(i -> i.a() <= 0);
        summary = new PowerSummary(plants, peak, producers.top(), shownConsumers, storages.top());
        add(new PowerSample(prod, cons, stored, cap, in, out));
    }

    private void add(PowerSample s) {
        fine.add(s);
        fineCount++;
        while (fine.size() > FINE_MAX) fine.remove(0);
        long[] a = s.toArray();
        for (int i = 0; i < a.length; i++) acc[i] += a[i];
        if (++accN >= COARSE_EVERY) {
            PowerSample avg = new PowerSample(Math.round(acc[0] / (double) accN), Math.round(acc[1] / (double) accN), s.stored(), s.capacity(),
                    Math.round(acc[4] / (double) accN), Math.round(acc[5] / (double) accN));
            coarse.add(avg);
            coarseCount++;
            while (coarse.size() > COARSE_MAX) coarse.remove(0);
            java.util.Arrays.fill(acc, 0);
            accN = 0;
        }
        setChanged();
        if (fineCount % WORLD_EVERY == 0) sync();
    }

    public List<PowerSample> fineSince(long from) {
        long first = fineCount - fine.size();
        int start = (int) Math.max(0, from - first);
        return start >= fine.size() ? List.of() : new ArrayList<>(fine.subList(start, fine.size()));
    }

    public List<PowerSample> worldSeries() {
        List<PowerSample> src = slowRange ? coarse : fine;
        return src.subList(Math.max(0, src.size() - WORLD_SAMPLES), src.size());
    }

    private List<PowerSample> shown = List.of();

    public List<PowerSample> shown() {
        return shown;
    }

    private static long[] flat(List<PowerSample> list) {
        long[] out = new long[list.size() * PowerSample.FIELDS];
        int i = 0;
        for (PowerSample s : list) for (long v : s.toArray()) out[i++] = v;
        return out;
    }

    private static List<PowerSample> unflat(long[] a) {
        List<PowerSample> out = new ArrayList<>();
        for (int i = 0; i + PowerSample.FIELDS <= a.length; i += PowerSample.FIELDS) out.add(PowerSample.of(a, i));
        return out;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("shown", new LongArrayTag(flat(level != null ? worldSeries() : List.of())));
        tag.putBoolean("slow", slowRange);
        tag.putLong("peak", summary.peak());
        return tag;
    }

    private long shownPeak;

    public long shownPeak() {
        return shownPeak;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        shown = unflat(tag.getLongArray("shown"));
        slowRange = tag.getBoolean("slow");
        shownPeak = tag.getLong("peak");
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this, (be, reg) -> be.getUpdateTag(reg));
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null && !tag.isEmpty()) handleUpdateTag(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fine", new LongArrayTag(flat(fine)));
        tag.put("coarse", new LongArrayTag(flat(coarse)));
        tag.putLong("fineCount", fineCount);
        tag.putLong("coarseCount", coarseCount);
        tag.putBoolean("slow", slowRange);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fine.clear();
        fine.addAll(unflat(tag.getLongArray("fine")));
        coarse.clear();
        coarse.addAll(unflat(tag.getLongArray("coarse")));
        while (fine.size() > FINE_MAX) fine.remove(0);
        while (coarse.size() > COARSE_MAX) coarse.remove(0);
        fineCount = Math.max(tag.getLong("fineCount"), fine.size());
        coarseCount = Math.max(tag.getLong("coarseCount"), coarse.size());
        slowRange = tag.getBoolean("slow");
        shown = new ArrayList<>(worldSeries());
    }
}
