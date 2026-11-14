package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.MonitorTerminal;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class ControlDesk {
    public static final int LINES = 6, PERIOD = 20;

    public static final double[] SCREEN_LEFT = box(10, 22.5, 9, 22, 30, 10);
    public static final double[] SCREEN_RIGHT = box(-22, 22.5, 9, -10, 30, 10);
    public static final double[] SCREEN_MAIN = box(-6.5, 22.5, 8, 6.5, 31, 9);
    public static final double[] ESTOP = box(16, 15, -6, 22, 19, 0);
    public static final double[][] GAUGES = {box(15, 16, 2, 21, 21, 3), box(8, 16, 2, 14, 21, 3),
            box(-14, 16, 2, -8, 21, 3), box(-21, 16, 2, -15, 21, 3)};
    public static final double[] SEGMENT = box(-6, 18, 2, 6, 21, 3);
    public static final double[][] LAMPS = {box(3.5, 16, 2, 5.5, 17, 3), box(0.5, 16, 2, 2.5, 17, 3),
            box(-2.5, 16, 2, -0.5, 17, 3), box(-5.5, 16, 2, -3.5, 17, 3)};

    private static double[] box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new double[] {(x0 + 8) / 16, y0 / 16, (z0 + 8) / 16, (x1 + 8) / 16, y1 / 16, (z1 + 8) / 16};
    }

    public static AABB worldBox(BlockPos ctrl, int rot, double[] m) {
        double[] a = MonitorTerminal.rotate(m[0], m[2], rot), b = MonitorTerminal.rotate(m[3], m[5], rot);
        return new AABB(ctrl.getX() + a[0], ctrl.getY() + m[1], ctrl.getZ() + a[1],
                ctrl.getX() + b[0], ctrl.getY() + m[4], ctrl.getZ() + b[1]).inflate(1 / 32.0);
    }

    private List<ControlWallBlockEntity.Tile> tiles = List.of();
    @Nullable
    private BlockPos selected;
    private CompoundTag lastSync = new CompoundTag();

    public List<ControlWallBlockEntity.Tile> tiles() {
        return tiles;
    }

    public int rows() {
        return Math.max(3, Math.min(6, tiles.size()));
    }

    public int selectedIndex() {
        if (tiles.isEmpty()) return -1;
        for (int i = 0; i < tiles.size(); i++) if (tiles.get(i).pos().equals(selected)) return i;
        return 0;
    }

    @Nullable
    public ControlWallBlockEntity.Tile selectedTile() {
        int i = selectedIndex();
        return i < 0 ? null : tiles.get(i);
    }

    public static List<DataSource> scan(Level level, BigMachineBlockEntity desk) {
        List<DataSource> out = new ArrayList<>(DataNet.scan(level, desk.cellPositions()));
        out.removeIf(s -> s.pos().equals(desk.getBlockPos()));
        return out;
    }

    public static DataNet.Result scanChecked(Level level, BigMachineBlockEntity desk) {
        DataNet.Result r = DataNet.scanChecked(level, desk.cellPositions());
        List<DataSource> out = new ArrayList<>(r.sources());
        out.removeIf(s -> s.pos().equals(desk.getBlockPos()));
        return new DataNet.Result(out, r.incomplete(), r.chunks(), r.capped());
    }

    public boolean tick(Level level, BigMachineBlockEntity desk, HolderLookup.Provider registries) {
        if (level.getGameTime() % PERIOD != 0) return false;
        List<ControlWallBlockEntity.Tile> now = new ArrayList<>();
        ComputeNet.Status c = ComputeNet.status(level, desk.cellPositions(), desk.getBlockPos());
        if (!c.served()) now.add(ComputeNet.tile(desk.getBlockPos(), c));
        else {
            DataNet.Result r = scanChecked(level, desk);
            if (r.capped()) now.add(DataNet.capTile(desk.getBlockPos()));
            for (DataSource s : r.sources()) now.add(ControlWallBlockEntity.tile(s, LINES));
        }
        tiles = now;
        return changed(registries);
    }

    public boolean clear(HolderLookup.Provider registries) {
        if (tiles.isEmpty()) return false;
        tiles = List.of();
        return changed(registries);
    }

    private boolean changed(HolderLookup.Provider registries) {
        CompoundTag tag = save(registries);
        if (tag.equals(lastSync)) return false;
        lastSync = tag;
        return true;
    }

    @Nullable
    public ControlWallBlockEntity.Tile selectNext() {
        if (tiles.isEmpty()) return null;
        ControlWallBlockEntity.Tile t = tiles.get((selectedIndex() + 1) % tiles.size());
        selected = t.pos();
        lastSync = new CompoundTag();
        return t;
    }

    @Nullable
    public ControlWallBlockEntity.Tile selectNextAlarm() {
        int n = tiles.size(), from = selectedIndex();
        for (int k = 1; k <= n; k++) {
            ControlWallBlockEntity.Tile t = tiles.get((from + k) % n);
            if (t.state() == DataSource.ALARM || t.state() == DataSource.WARN) {
                selected = t.pos();
                lastSync = new CompoundTag();
                return t;
            }
        }
        return null;
    }

    public List<ControlWallBlockEntity.Tile> alarms() {
        List<ControlWallBlockEntity.Tile> out = new ArrayList<>();
        for (ControlWallBlockEntity.Tile t : tiles) if (t.state() == DataSource.ALARM) out.add(t);
        for (ControlWallBlockEntity.Tile t : tiles) if (t.state() == DataSource.WARN) out.add(t);
        return out;
    }

    public static int emergencyStop(Level level, BigMachineBlockEntity desk) {
        int n = 0;
        for (DataSource s : scan(level, desk)) if (s.settable().contains("scram") && s.set("scram", 1) == null) n++;
        return n;
    }

    public static boolean click(ServerPlayer sp, BigMachineBlockEntity desk, double[] zone) {
        ControlDesk d = desk.desk();
        if (d == null) return false;
        Level level = sp.level();
        if (zone == ESTOP) {
            int n = emergencyStop(level, desk);
            sp.displayClientMessage(Component.translatable(n > 0 ? "data.nuclearsq.msg.estop" : "data.nuclearsq.msg.estop_none", n), true);
            return true;
        }
        if (!desk.isFormed() || !desk.isRunning()) {
            sp.displayClientMessage(Component.translatable("data.nuclearsq.msg.desk_off"), true);
            return true;
        }
        ControlWallBlockEntity.Tile t = zone == SCREEN_LEFT ? d.selectNext() : d.selectNextAlarm();
        if (t == null) {
            sp.displayClientMessage(Component.translatable(zone == SCREEN_LEFT ? "data.nuclearsq.msg.none" : "data.nuclearsq.msg.no_alarm"), true);
        } else {
            sp.displayClientMessage(Component.translatable("data.nuclearsq.msg.selected", t.name()), true);
            desk.syncDesk();
        }
        return true;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("tiles", ControlWallBlockEntity.writeTiles(tiles, registries));
        if (selected != null) tag.put("selected", NbtUtils.writeBlockPos(selected));
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        tiles = ControlWallBlockEntity.readTiles(tag.getList("tiles", Tag.TAG_COMPOUND), registries);
        selected = NbtUtils.readBlockPos(tag, "selected").orElse(null);
    }

    @Nullable
    public static BigMachineBlockEntity at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BigMachineBlockEntity b && b.machine() == BigMachine.CONTROL_DESK ? b : null;
    }
}
