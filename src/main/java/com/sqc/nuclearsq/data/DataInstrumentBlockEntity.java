package com.sqc.nuclearsq.data;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DataInstrumentBlockEntity extends BlockEntity {
    public static final int PERIOD = 10;

    @Nullable
    private BlockPos source;
    private String key = "";
    private boolean linked;
    private Component name = Component.empty();
    private Component label = Component.empty();
    private String text = "";
    private float frac;
    private boolean scaled;
    private int state = DataSource.OFF;

    public DataInstrumentBlockEntity(BlockPos pos, BlockState state) {
        super(DataRegistry.INSTRUMENT_BE.get(), pos, state);
    }

    public boolean linked() { return linked; }
    public Component name() { return name; }
    public Component label() { return label; }
    public String text() { return text; }
    public float frac() { return frac; }
    public boolean scaled() { return scaled; }
    public int state() { return state; }
    @Nullable public BlockPos source() { return source; }
    public String key() { return key; }

    public int comparator() {
        return linked && scaled ? Math.round(frac * 15) : 0;
    }

    private static final int NET_PERIODS = 10;
    @Nullable private List<BlockPos> netPorts;
    private int netAge;

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
        if (level == null) return;
        update(scanNet());
    }

    private void update(List<DataSource> net) {
        DataSource s = null;
        for (DataSource c : net) if (c.pos().equals(source)) s = c;
        if (s == null && source == null && !net.isEmpty()) {
            s = net.get(0);
            source = s.pos();
        }
        DataValue v = null;
        if (s != null) {
            List<DataValue> values = DataDisplay.order(s);
            v = s.value(key);
            if (v == null && !values.isEmpty()) {
                v = values.get(0);
                key = v.key();
            }
        }
        boolean nowLinked = v != null;
        Component nowName = s == null ? Component.empty() : s.name();
        Component nowLabel = v == null ? Component.empty() : v.label();
        String nowText = v == null ? "" : v.text();
        float nowFrac = v == null ? 0 : v.frac();
        boolean nowScaled = v != null && v.max() > 0;
        int nowState = s == null ? DataSource.OFF : s.state();
        if (nowLinked == linked && nowName.equals(name) && nowLabel.equals(label) && nowText.equals(text)
                && Mth.equal(nowFrac, frac) && nowScaled == scaled && nowState == state) return;
        int oldCmp = comparator();
        linked = nowLinked;
        name = nowName;
        label = nowLabel;
        text = nowText;
        frac = nowFrac;
        scaled = nowScaled;
        state = nowState;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        if (oldCmp != comparator()) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    public void cycle(Player player, boolean nextSource) {
        if (level == null) return;
        List<DataSource> net = DataNet.scan(level, worldPosition);
        if (net.isEmpty()) {
            player.displayClientMessage(Component.translatable("data.nuclearsq.msg.none"), true);
            return;
        }
        int si = 0;
        for (int i = 0; i < net.size(); i++) if (net.get(i).pos().equals(source)) si = i;
        if (nextSource) {
            si = (si + 1) % net.size();
            key = "";
        }
        DataSource s = net.get(si);
        source = s.pos();
        List<DataValue> values = DataDisplay.order(s);
        if (!values.isEmpty()) {
            int vi = -1;
            for (int i = 0; i < values.size(); i++) if (values.get(i).key().equals(key)) vi = i;
            key = values.get(nextSource ? 0 : (vi + 1) % values.size()).key();
        }
        refresh();
        DataValue v = s.value(key);
        player.displayClientMessage(Component.translatable("data.nuclearsq.msg.instrument", s.name(),
                v == null ? Component.literal("-") : v.label()), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (source != null) tag.put("source", NbtUtils.writeBlockPos(source));
        tag.putString("key", key);
        tag.putBoolean("linked", linked);
        tag.putString("name", Component.Serializer.toJson(name, registries));
        tag.putString("label", Component.Serializer.toJson(label, registries));
        tag.putString("text", text);
        tag.putFloat("frac", frac);
        tag.putBoolean("scaled", scaled);
        tag.putInt("state", state);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        source = NbtUtils.readBlockPos(tag, "source").orElse(null);
        key = tag.getString("key");
        linked = tag.getBoolean("linked");
        name = json(tag.getString("name"), registries);
        label = json(tag.getString("label"), registries);
        text = tag.getString("text");
        frac = tag.getFloat("frac");
        scaled = tag.getBoolean("scaled");
        state = tag.getInt("state");
    }

    static Component json(String s, HolderLookup.Provider registries) {
        if (s.isEmpty()) return Component.empty();
        try {
            return Objects.requireNonNullElse(Component.Serializer.fromJson(s, registries), Component.empty());
        } catch (RuntimeException e) {
            return Component.empty();
        }
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
