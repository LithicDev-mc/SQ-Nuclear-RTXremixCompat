package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;

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

public class ControlWallBlockEntity extends BlockEntity {
    public static final int LINES = 3, PERIOD = 20;

    public record Line(Component label, String text, float frac, boolean scaled) {}

    public record Tile(BlockPos pos, Component name, int state, Component stateText, List<Line> lines) {}

    private List<Tile> tiles = List.of();
    private CompoundTag lastSync = new CompoundTag();

    public ControlWallBlockEntity(BlockPos pos, BlockState state) {
        super(DataRegistry.WALL_BE.get(), pos, state);
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % PERIOD != 0) return;
        ControlWallBlock.Layout l = ControlWallBlock.originCandidate(level, worldPosition) ? ControlWallBlock.layout(level, worldPosition) : null;
        List<Tile> now = new ArrayList<>();
        if (l != null && l.origin().equals(worldPosition)) {
            ComputeNet.Status c = ComputeNet.status(level, l.cells(), l.origin());
            if (!c.served()) now.add(ComputeNet.tile(worldPosition, c));
            else {
                DataNet.Result r = DataNet.scanChecked(level, l.cells());
                if (r.capped()) now.add(DataNet.capTile(worldPosition));
                for (DataSource s : r.sources()) now.add(tile(s, LINES));
            }
        }
        tiles = now;
        CompoundTag tag = new CompoundTag();
        write(tag, level.registryAccess());
        if (tag.equals(lastSync)) return;
        lastSync = tag;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public static Tile tile(DataSource s, int max) {
        List<Line> lines = new ArrayList<>();
        for (DataValue v : s.display(max)) lines.add(new Line(v.label(), v.text(), v.frac(), v.max() > 0));
        return new Tile(s.pos(), s.name(), s.state(), s.stateText(), lines);
    }

    private void write(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("tiles", writeTiles(tiles, registries));
    }

    public static ListTag writeTiles(List<Tile> tiles, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Tile t : tiles) {
            CompoundTag c = new CompoundTag();
            c.put("pos", NbtUtils.writeBlockPos(t.pos()));
            c.putString("name", Component.Serializer.toJson(t.name(), registries));
            c.putInt("state", t.state());
            c.putString("stateText", Component.Serializer.toJson(t.stateText(), registries));
            ListTag lines = new ListTag();
            for (Line ln : t.lines()) {
                CompoundTag lc = new CompoundTag();
                lc.putString("label", Component.Serializer.toJson(ln.label(), registries));
                lc.putString("text", ln.text());
                lc.putFloat("frac", ln.frac());
                lc.putBoolean("scaled", ln.scaled());
                lines.add(lc);
            }
            c.put("lines", lines);
            list.add(c);
        }
        return list;
    }

    public static List<Tile> readTiles(ListTag list, HolderLookup.Provider registries) {
        List<Tile> out = new ArrayList<>();
        for (Tag e : list) {
            CompoundTag c = (CompoundTag) e;
            List<Line> lines = new ArrayList<>();
            for (Tag le : c.getList("lines", Tag.TAG_COMPOUND)) {
                CompoundTag lc = (CompoundTag) le;
                lines.add(new Line(DataInstrumentBlockEntity.json(lc.getString("label"), registries), lc.getString("text"),
                        lc.getFloat("frac"), lc.getBoolean("scaled")));
            }
            out.add(new Tile(NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO),
                    DataInstrumentBlockEntity.json(c.getString("name"), registries), c.getInt("state"),
                    DataInstrumentBlockEntity.json(c.getString("stateText"), registries), lines));
        }
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tiles = readTiles(tag.getList("tiles", Tag.TAG_COMPOUND), registries);
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
