package com.sqc.nuclearsq.machine.big;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.transport.GridNode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class Substation implements GridNode {
    private static final int[][] BUSHINGS = {{1, 2}, {2, 2}, {3, 2}};
    private static final double CAP_Y = 74 / 16.0;
    private static final int MAX_NODES = 512;

    private final BigMachineBlockEntity be;
    private final List<BlockPos> links = new ArrayList<>();
    private int feed, supply;
    private List<BlockPos> targets = List.of();
    private int nodes;
    private long searchedAt = -1_000_000L;
    private int inTick, lineTick, outTick;
    private float inAvg, lineAvg, outAvg;
    private final IEnergyStorage[] inputs;
    private final int[] inputUsed;

    public Substation(BigMachineBlockEntity be) {
        this.be = be;
        List<BigMachine.Port> ins = energyInputs();
        inputs = new IEnergyStorage[ins.size()];
        inputUsed = new int[ins.size()];
        for (int i = 0; i < ins.size(); i++) inputs[i] = input(i, ins.get(i));
    }

    public static boolean handles(BigMachine m) {
        return m == BigMachine.SUBSTATION;
    }

    private List<BigMachine.Port> energyInputs() {
        List<BigMachine.Port> out = new ArrayList<>();
        for (BigMachine.Port p : be.machine().ports) if (p.kind() == BigPortKind.ENERGY && p.in()) out.add(p);
        return out;
    }

    public int lineRate() {
        return Math.max(1, be.machine().extra("line_rate"));
    }

    public int feed() { return feed; }
    public int supply() { return supply; }
    public List<BlockPos> targets() { return targets; }
    public int networkNodes() { return nodes; }
    public int inRate() { return Math.round(inAvg); }
    public int lineRateNow() { return Math.round(lineAvg); }
    public int outRate() { return Math.round(outAvg); }

    @Override
    public BlockPos nodePos() {
        return be.getBlockPos();
    }

    @Override
    public List<BlockPos> links() {
        return links;
    }

    @Override
    public List<Vec3> attachPoints() {
        MultiblockStructure s = be.machine().structure;
        int rot = Math.max(0, be.formedRot());
        List<Vec3> out = new ArrayList<>(BUSHINGS.length);
        for (int[] b : BUSHINGS) {
            BlockPos p = s.world(be.getBlockPos(), rot, b[0], 0, b[1]);
            out.add(new Vec3(p.getX() + 0.5, p.getY() + CAP_Y, p.getZ() + 0.5));
        }
        return out;
    }

    @Override
    public boolean addLink(BlockPos p) {
        if (links.contains(p)) return false;
        links.add(p.immutable());
        changed();
        return true;
    }

    @Override
    public boolean removeLink(BlockPos p) {
        if (!links.remove(p)) return false;
        changed();
        return true;
    }

    @Override
    public boolean delivers() {
        return be.isFormed();
    }

    @Override
    public int lineSpace() {
        return be.isFormed() ? Math.max(0, lineRate() - supply) : 0;
    }

    @Override
    public int fromLine(int max) {
        int n = Math.max(0, Math.min(max, lineSpace()));
        if (n > 0) {
            supply += n;
            be.setChanged();
        }
        return n;
    }

    private void changed() {
        searchedAt = -1_000_000L;
        be.setChanged();
        Level level = be.getLevel();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
    }

    @Nullable
    public IEnergyStorage at(String port) {
        List<BigMachine.Port> ins = energyInputs();
        for (int i = 0; i < ins.size() && i < inputs.length; i++) if (ins.get(i).name().equals(port)) return inputs[i];
        BigMachine.Port p = be.machine().port(port);
        return p != null && p.kind() == BigPortKind.ENERGY && p.out() ? output : null;
    }

    private final IEnergyStorage output = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) { return 0; }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return supply; }
        @Override public int getMaxEnergyStored() { return lineRate(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return false; }
    };

    private IEnergyStorage input(int i, BigMachine.Port p) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int max, boolean simulate) {
                if (!be.isFormed() || !be.enabled()) return 0;
                int n = Math.max(0, Math.min(max, Math.min(lineRate() - feed, p.rate() - inputUsed[i])));
                if (!simulate && n > 0) {
                    feed += n;
                    inputUsed[i] += n;
                    inTick += n;
                    be.setChanged();
                }
                return n;
            }

            @Override public int extractEnergy(int max, boolean simulate) { return 0; }
            @Override public int getEnergyStored() { return feed; }
            @Override public int getMaxEnergyStored() { return lineRate(); }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return be.isFormed() && be.enabled(); }
        };
    }

    private void search(Level level) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<GridNode> queue = new ArrayDeque<>();
        seen.add(nodePos());
        out.add(nodePos());
        queue.add(this);
        while (!queue.isEmpty() && seen.size() < MAX_NODES) {
            GridNode n = queue.poll();
            for (BlockPos o : List.copyOf(n.links())) {
                if (!level.isLoaded(o)) continue;
                GridNode other = GridNode.node(level, o);
                if (other == null) {
                    n.removeLink(o);
                    continue;
                }
                if (!seen.add(o)) continue;
                if (other instanceof Substation) out.add(o);
                queue.add(other);
            }
        }
        targets = out;
        nodes = seen.size();
        searchedAt = level.getGameTime();
    }

    public int tick(Level level, boolean formed, boolean enabled) {
        java.util.Arrays.fill(inputUsed, 0);
        if (formed && level.getGameTime() - searchedAt >= 20) search(level);
        if (formed && enabled && feed > 0 && !targets.isEmpty()) {
            int left = targets.size();
            for (BlockPos t : targets) {
                int share = (feed + left - 1) / left--;
                if (share <= 0) break;
                GridNode n = t.equals(nodePos()) ? this : level.isLoaded(t) ? GridNode.node(level, t) : null;
                if (n == null || !n.delivers()) continue;
                int took = n.fromLine(share);
                feed -= took;
                lineTick += took;
            }
            be.setChanged();
        }
        if (formed && enabled && supply > 0) pushOut(level);
        int moved = inTick + lineTick + outTick;
        inAvg += (inTick - inAvg) * 0.1f;
        lineAvg += (lineTick - lineAvg) * 0.1f;
        outAvg += (outTick - outAvg) * 0.1f;
        inTick = lineTick = outTick = 0;
        return moved;
    }

    private void pushOut(Level level) {
        for (BigMachine.Port p : be.machine().ports) {
            if (p.kind() != BigPortKind.ENERGY || !p.out() || supply <= 0) continue;
            BlockPos pos = be.portPos(p);
            if (pos == null) continue;
            int left = Math.min(p.rate(), supply);
            for (Direction d : Direction.values()) {
                if (left <= 0) break;
                BlockPos n = pos.relative(d);
                BlockEntity nb = level.getBlockEntity(n);
                if (nb instanceof BigMachineBlockEntity c && c == be || nb instanceof BigPortBlockEntity pb && pb.findController() == be) continue;
                int took = 0;
                if (nb instanceof TranslatorSink sink) took = sink.acceptTranslated(pos, left, false);
                else {
                    IEnergyStorage t = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, d.getOpposite());
                    if (t != null && t.canReceive()) took = t.receiveEnergy(left, false);
                }
                if (took > 0) {
                    supply -= took;
                    left -= took;
                    outTick += took;
                }
            }
        }
    }

    public void save(CompoundTag tag) {
        tag.putLongArray("Links", links.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("Feed", feed);
        tag.putInt("Supply", supply);
    }

    public void load(CompoundTag tag) {
        links.clear();
        for (long l : tag.getLongArray("Links")) links.add(BlockPos.of(l));
        feed = Math.max(0, Math.min(lineRate(), tag.getInt("Feed")));
        supply = Math.max(0, Math.min(lineRate(), tag.getInt("Supply")));
        searchedAt = -1_000_000L;
    }

    public void saveLinks(CompoundTag tag) {
        tag.putLongArray("Links", links.stream().mapToLong(BlockPos::asLong).toArray());
    }

    public void loadLinks(CompoundTag tag) {
        links.clear();
        for (long l : tag.getLongArray("Links")) links.add(BlockPos.of(l));
    }
}
