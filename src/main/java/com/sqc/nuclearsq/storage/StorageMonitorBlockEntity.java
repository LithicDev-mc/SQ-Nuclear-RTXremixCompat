package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StorageMonitorBlockEntity extends BlockEntity {
    public static final int PERIOD = 20;

    public record Row(BlockPos pos, String machine, boolean online, long used, long capacity, int drives, int slots, int priority) {
        public float frac() {
            return capacity <= 0 ? 0 : Math.min(1f, used / (float) capacity);
        }
    }

    private List<Row> rows = List.of();
    private CompoundTag lastSync = new CompoundTag();

    public StorageMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(StorageRegistry.MONITOR_BE.get(), pos, state);
    }

    public List<Row> rows() {
        return rows;
    }

    public long used() {
        long n = 0;
        for (Row r : rows) n += r.used();
        return n;
    }

    public long capacity() {
        long n = 0;
        for (Row r : rows) n += r.capacity();
        return n;
    }

    public int online() {
        int n = 0;
        for (Row r : rows) if (r.online()) n++;
        return n;
    }

    public int comparator() {
        long cap = capacity(), used = used();
        if (cap <= 0 || used <= 0) return 0;
        return Math.max(1, (int) Math.min(15, used * 15 / cap));
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % PERIOD != 0) return;
        StorageMonitorBlock.Layout l = StorageMonitorBlock.layout(level, worldPosition);
        List<Row> now = new ArrayList<>();
        if (l.origin().equals(worldPosition)) {
            for (BigMachineBlockEntity be : StorageNet.racks(level, l.cells())) {
                ServerRack r = be.rack();
                ServerRack.Space sp = r.space();
                now.add(new Row(be.getBlockPos(), be.machine().id, r.online(), sp.used(), sp.capacity(), sp.drives(), r.slots(), r.priority()));
            }
        }
        int oldCmp = comparator();
        rows = now;
        CompoundTag tag = new CompoundTag();
        write(tag);
        if (tag.equals(lastSync)) return;
        lastSync = tag;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        if (oldCmp != comparator()) {
            for (BlockPos p : l.cells()) level.updateNeighbourForOutputSignal(p, getBlockState().getBlock());
        }
    }

    private void write(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Row r : rows) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", r.pos().asLong());
            c.putString("machine", r.machine());
            c.putBoolean("online", r.online());
            c.putLong("used", r.used());
            c.putLong("capacity", r.capacity());
            c.putInt("drives", r.drives());
            c.putInt("slots", r.slots());
            c.putInt("priority", r.priority());
            list.add(c);
        }
        tag.put("racks", list);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        List<Row> out = new ArrayList<>();
        for (Tag e : tag.getList("racks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            out.add(new Row(BlockPos.of(c.getLong("pos")), c.getString("machine"), c.getBoolean("online"), c.getLong("used"),
                    c.getLong("capacity"), c.getInt("drives"), c.getInt("slots"), c.getInt("priority")));
        }
        rows = out;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
