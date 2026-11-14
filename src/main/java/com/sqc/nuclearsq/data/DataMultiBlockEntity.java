package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.data.ControlWallBlockEntity.Line;
import com.sqc.nuclearsq.data.ControlWallBlockEntity.Tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DataMultiBlockEntity extends BlockEntity {
    public static final int ROWS = 4, PERIOD = 10;
    private static final int NET_PERIODS = 10;

    @Nullable
    private BlockPos source;
    private final String[] keys = new String[ROWS];
    @Nullable
    private Tile tile;
    @Nullable
    private List<BlockPos> netPorts;
    private int netAge;

    public DataMultiBlockEntity(BlockPos pos, BlockState state) {
        super(DataRegistry.MULTI_BE.get(), pos, state);
        Arrays.fill(keys, "");
    }

    @Nullable public Tile tile() { return tile; }
    @Nullable public BlockPos source() { return source; }
    public String key(int row) { return keys[row]; }

    public void serverTick() {
        if (level == null || level.getGameTime() % PERIOD != Math.floorMod(worldPosition.asLong(), PERIOD)) return;
        List<DataSource> net = netPorts == null || ++netAge >= NET_PERIODS ? null : DataNet.read(level, netPorts);
        update(net != null ? net : scanNet());
    }

    private List<DataSource> scanNet() {
        netPorts = DataNet.scanPorts(level, worldPosition);
        netAge = 0;
        List<DataSource> net = DataNet.read(level, netPorts);
        return net != null ? net : List.of();
    }

    public void refresh() {
        if (level != null) update(scanNet());
    }

    private void update(List<DataSource> net) {
        DataSource s = null;
        for (DataSource c : net) if (c.pos().equals(source)) s = c;
        if (s == null && source == null && !net.isEmpty()) {
            s = net.get(0);
            source = s.pos();
        }
        Tile now = null;
        if (s != null) {
            List<DataValue> first = s.display(ROWS);
            List<Line> lines = new ArrayList<>();
            for (int i = 0; i < ROWS; i++) {
                DataValue v = s.value(keys[i]);
                if (v == null && i < first.size()) {
                    v = first.get(i);
                    keys[i] = v.key();
                }
                lines.add(v == null ? new Line(Component.empty(), "", 0, false) : new Line(v.label(), v.text(), v.frac(), v.max() > 0));
            }
            now = new Tile(s.pos(), s.name(), s.state(), s.stateText(), lines);
        }
        if (Objects.equals(now, tile)) return;
        tile = now;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public void cycleSource(int dir) {
        if (level == null) return;
        List<DataSource> net = scanNet();
        if (net.isEmpty()) return;
        int si = 0;
        for (int i = 0; i < net.size(); i++) if (net.get(i).pos().equals(source)) si = i;
        source = net.get(Math.floorMod(si + dir, net.size())).pos();
        Arrays.fill(keys, "");
        update(net);
    }

    public void cycleKey(int row, int dir) {
        if (level == null) return;
        List<DataSource> net = scanNet();
        for (DataSource s : net) {
            List<DataValue> values = s.pos().equals(source) ? DataDisplay.order(s) : List.of();
            if (values.isEmpty()) continue;
            int vi = 0;
            for (int i = 0; i < values.size(); i++) if (values.get(i).key().equals(keys[row])) vi = i;
            keys[row] = values.get(Math.floorMod(vi + dir, values.size())).key();
        }
        update(net);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (source != null) tag.put("source", NbtUtils.writeBlockPos(source));
        for (int i = 0; i < ROWS; i++) tag.putString("key" + i, keys[i]);
        tag.put("tile", ControlWallBlockEntity.writeTiles(tile == null ? List.of() : List.of(tile), registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        source = NbtUtils.readBlockPos(tag, "source").orElse(null);
        for (int i = 0; i < ROWS; i++) keys[i] = tag.getString("key" + i);
        ListTag list = tag.getList("tile", Tag.TAG_COMPOUND);
        tile = list.isEmpty() ? null : ControlWallBlockEntity.readTiles(list, registries).get(0);
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
