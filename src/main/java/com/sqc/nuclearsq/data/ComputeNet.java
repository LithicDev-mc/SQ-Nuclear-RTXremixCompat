package com.sqc.nuclearsq.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.storage.ServerRack;
import com.sqc.nuclearsq.storage.StorageValues;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class ComputeNet {
    private ComputeNet() {}

    public record Status(int cpu, int ram, int cpuUsed, int ramUsed, int modules, int users,
                         @Nullable StorageValues.Demand need, boolean served) {
        public int cpuFree() {
            return Math.max(0, cpu - cpuUsed);
        }

        public int ramFree() {
            return Math.max(0, ram - ramUsed);
        }
    }

    public static boolean required() {
        return NuclearConfig.value(NuclearConfig.COMPUTE_REQUIRED);
    }

    @Nullable
    public static StorageValues.Demand demandAt(Level level, BlockPos pos) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return NuclearSQ.MODID.equals(id.getNamespace()) ? StorageValues.demand(id.getPath()) : null;
    }

    public static Status status(Level level, BlockPos pos) {
        BigMachineBlockEntity desk = ControlDesk.at(level, pos);
        if (desk != null) return status(level, desk.cellPositions(), pos);
        ControlWallBlock.Layout l = ControlWallBlock.layout(level, pos);
        if (l != null) return status(level, l.cells(), l.origin());
        return status(level, List.of(pos), pos);
    }

    public record Share(int cpu, int ram) {}

    private static boolean takesShare(BigMachineBlockEntity be) {
        return be.assembler() != null && be.machine() != com.sqc.nuclearsq.machine.big.BigMachine.QUANTUM_COMPUTER
                || com.sqc.nuclearsq.machine.big.ProductionTier.handles(be.machine());
    }

    public static Share share(Level level, BigMachineBlockEntity be) {
        Status st = status(level, be.dataStarts(), be.getBlockPos());
        List<BigMachineBlockEntity> peers = new ArrayList<>();
        for (DataSource ds : DataNet.scan(level, be.dataStarts())) {
            if (level.getBlockEntity(ds.pos()) instanceof BigMachineBlockEntity o && o != be && o.isFormed() && takesShare(o)) peers.add(o);
        }
        peers.add(be);
        peers.sort(java.util.Comparator.comparingLong(b -> b.getBlockPos().asLong()));
        int n = Math.max(1, peers.size()), idx = Math.max(0, peers.indexOf(be));
        int cpu = st.cpuFree() / n + (idx < st.cpuFree() % n ? 1 : 0);
        int ram = st.ramFree() / n + (idx < st.ramFree() % n ? 1 : 0);
        return new Share(cpu, ram);
    }

    public static Status status(Level level, Collection<BlockPos> starts, BlockPos self) {
        Map<BlockPos, StorageValues.Demand> users = new TreeMap<>();
        StorageValues.Demand own = demandAt(level, self);
        if (own != null) users.put(self.immutable(), own);
        ServerRack.Compute supply = ServerRack.Compute.NONE;
        Set<BlockPos> racks = new HashSet<>();
        Set<BlockPos> seen = new HashSet<>(starts);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(starts);
        int cables = 0;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (DataCableBlock.off(level, p, d) || !seen.add(n) || !level.isLoaded(n)) continue;
                if (level.getBlockState(n).getBlock() instanceof DataCableBlock) {
                    if (DataCableBlock.off(level, n, d.getOpposite())) {
                        seen.remove(n);
                        continue;
                    }
                    if (++cables <= DataNet.MAX_CABLES) queue.add(n);
                } else if (DataSources.isPort(level, n)) {
                    DataNet.bridge(level, n, seen, queue);
                    DataSource s = DataSources.at(level, n);
                    if (s == null) continue;
                    if (!(level.getBlockEntity(s.pos()) instanceof BigMachineBlockEntity be)) {
                        StorageValues.Demand single = demandAt(level, s.pos());
                        if (single != null) users.putIfAbsent(s.pos().immutable(), single);
                        continue;
                    }
                    if (be.rack() != null) {
                        if (racks.add(s.pos()) && be.rack().online()) supply = supply.plus(be.rack().compute());
                    } else if (be.cluster() != null) {
                        if (racks.add(s.pos())) supply = supply.plus(be.cluster().supply());
                    } else {
                        StorageValues.Demand need = demandAt(level, s.pos());
                        if (need != null) users.putIfAbsent(s.pos().immutable(), need);
                    }
                } else {
                    BlockPos key = n;
                    if (level.getBlockState(n).getBlock() instanceof ControlWallBlock) {
                        ControlWallBlock.Layout l = ControlWallBlock.layout(level, n);
                        if (l == null) continue;
                        key = l.origin();
                    }
                    StorageValues.Demand need = demandAt(level, key);
                    if (need != null) users.putIfAbsent(key.immutable(), need);
                }
            }
        }
        int cpuUsed = 0, ramUsed = 0;
        boolean served = false;
        for (var e : users.entrySet()) {
            StorageValues.Demand need = e.getValue();
            if (cpuUsed + need.cpu() > supply.cpu() || ramUsed + need.ram() > supply.ram()) continue;
            cpuUsed += need.cpu();
            ramUsed += need.ram();
            if (e.getKey().equals(self)) served = true;
        }
        return new Status(supply.cpu(), supply.ram(), cpuUsed, ramUsed, supply.modules(), users.size(), own,
                own == null || served || !required());
    }

    public static Component missing(Status s) {
        StorageValues.Demand need = s.need() == null ? new StorageValues.Demand(0, 0) : s.need();
        if (s.modules() == 0) return Component.translatable("data.nuclearsq.compute.none", need.cpu(), StorageValues.gb(need.ram())).withStyle(ChatFormatting.RED);
        return Component.translatable("data.nuclearsq.compute.short", need.cpu(), StorageValues.gb(need.ram()), s.cpuFree(), StorageValues.gb(s.ramFree()))
                .withStyle(ChatFormatting.RED);
    }

    public static boolean allow(ServerPlayer sp, Level level, BlockPos pos) {
        Status s = status(level, pos);
        if (s.served()) return true;
        sp.displayClientMessage(missing(s), false);
        return false;
    }

    public static ControlWallBlockEntity.Tile tile(BlockPos pos, Status s) {
        List<ControlWallBlockEntity.Line> lines = new ArrayList<>();
        lines.add(new ControlWallBlockEntity.Line(Component.translatable("data.nuclearsq.val.cpu"), s.cpuUsed() + " / " + s.cpu(),
                s.cpu() <= 0 ? 0 : s.cpuUsed() / (float) s.cpu(), true));
        lines.add(new ControlWallBlockEntity.Line(Component.translatable("data.nuclearsq.val.ram"), StorageValues.gb(s.ramUsed()) + " / " + StorageValues.gb(s.ram()) + " GB",
                s.ram() <= 0 ? 0 : s.ramUsed() / (float) s.ram(), true));
        StorageValues.Demand need = s.need() == null ? new StorageValues.Demand(0, 0) : s.need();
        lines.add(new ControlWallBlockEntity.Line(Component.translatable("data.nuclearsq.compute.need"), need.cpu() + " / " + StorageValues.gb(need.ram()) + " GB", 0, false));
        return new ControlWallBlockEntity.Tile(pos, Component.translatable("data.nuclearsq.compute.title"), DataSource.ALARM,
                Component.translatable(s.modules() == 0 ? "data.nuclearsq.compute.state.none" : "data.nuclearsq.compute.state.short"), lines);
    }

    public static List<Component> report(Status s) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("data.nuclearsq.compute.title").withStyle(ChatFormatting.BOLD));
        out.add(Component.translatable("data.nuclearsq.compute.modules", s.modules()).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("data.nuclearsq.compute.cpu", s.cpuUsed(), s.cpu()).withStyle(ChatFormatting.WHITE));
        out.add(Component.translatable("data.nuclearsq.compute.ram", StorageValues.gb(s.ramUsed()), StorageValues.gb(s.ram())).withStyle(ChatFormatting.WHITE));
        out.add(Component.translatable("data.nuclearsq.compute.users", s.users()).withStyle(ChatFormatting.GRAY));
        if (!required()) out.add(Component.translatable("data.nuclearsq.compute.off").withStyle(ChatFormatting.GRAY));
        else if (s.served()) out.add(Component.translatable("data.nuclearsq.compute.ok").withStyle(ChatFormatting.GREEN));
        else out.add(missing(s));
        return out;
    }
}
