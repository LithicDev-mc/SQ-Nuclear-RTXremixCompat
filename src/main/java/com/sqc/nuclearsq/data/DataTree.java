package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.registry.ModDataComponents;
import com.sqc.nuclearsq.storage.ComputeModuleItem;
import com.sqc.nuclearsq.storage.DriveContents;
import com.sqc.nuclearsq.storage.DriveItem;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.ServerRack;
import com.sqc.nuclearsq.storage.StorageFormat;
import com.sqc.nuclearsq.storage.StorageValues;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class DataTree {
    private DataTree() {}

    public static final int ITEMS_PER_DRIVE = 8;

    private static final Set<String> NO_QUICK = Set.of("scram", "repair");

    public static List<DataEntry> scan(Level level, BlockPos terminal) {
        List<DataEntry> out = new ArrayList<>();
        for (DataSource s : DataNet.scan(level, terminal)) out.add(entry(level, s, terminal));
        return out;
    }

    public static DataEntry entry(Level level, DataSource s, BlockPos from) {
        List<DataValue> all = DataDisplay.order(s);
        List<DataEntry.Line> controls = controls(s, all);
        List<DataEntry.Line> values = new ArrayList<>();
        for (DataValue v : all) {
            if (DataDisplay.HIDDEN.contains(v.key())) continue;
            boolean control = false;
            for (DataEntry.Line c : controls) control |= c.key().equals(v.key());
            if (!control) values.add(new DataEntry.Line(DataEntry.VALUE, v.key(), v.label(), text(v), v.value(), v.max(), 0));
        }
        DataEntry head = DataEntry.of(s, from);
        return new DataEntry(head.pos(), head.name(), head.type(), head.state(), head.stateText(), head.dist(), values, controls,
                slots(level, s.pos()));
    }

    private static String text(DataValue v) {
        if (v.key().equals("storage_used")) return StorageFormat.count((long) v.value()) + " / " + StorageFormat.count((long) v.max());
        if (v.max() > 0 && (v.key().equals("types") || v.key().equals("drives") || v.key().equals("jobs") || v.key().equals("rods"))) {
            return DataValue.number(v.value()) + " / " + DataValue.number(v.max());
        }
        return v.text();
    }

    static List<DataEntry.Line> controls(DataSource s, List<DataValue> all) {
        List<DataEntry.Line> out = new ArrayList<>();
        List<String> settable = s.settable();
        for (DataValue v : all) {
            if (!settable.contains(v.key()) || !quick(v.key())) continue;
            if (v.max() == 1) out.add(new DataEntry.Line(DataEntry.SWITCH, v.key(), v.label(), v.text(), v.value(), 1, 1));
            else if (v.max() > 1) {
                double step = v.max() >= 50 ? Math.round(v.max() / 20) : 1;
                out.add(new DataEntry.Line(DataEntry.STEP, v.key(), v.label(), v.text(), v.value(), v.max(), step));
            }
        }
        return out;
    }

    private static boolean quick(String key) {
        return !NO_QUICK.contains(key) && !key.startsWith("dump.") && !key.startsWith("lock.");
    }

    public static boolean controllable(DataSource s, String key) {
        for (DataEntry.Line l : controls(s, s.values())) if (l.key().equals(key)) return true;
        return false;
    }

    static List<DataEntry.Slot> slots(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BigMachineBlockEntity be) || be.rack() == null) return List.of();
        ServerRack rack = be.rack();
        List<DataEntry.Slot> out = new ArrayList<>();
        for (int i = 0; i < rack.slots(); i++) {
            ItemStack st = rack.drives().getStackInSlot(i);
            if (st.getItem() instanceof DriveItem d) {
                StorageValues.Drive lim = d.limits();
                DriveItem.Fill f = st.get(ModDataComponents.DRIVE_FILL.get());
                long used = f == null ? 0 : f.used();
                List<DataEntry.Stock> items = new ArrayList<>();
                int more = 0;
                DriveContents c = level.getServer() == null ? null : rack.contents(level.getServer(), i);
                if (c != null) {
                    List<Map.Entry<ItemKey, Long>> sorted = new ArrayList<>(c.items().entrySet());
                    sorted.sort(Map.Entry.<ItemKey, Long>comparingByValue(Comparator.reverseOrder()));
                    for (int k = 0; k < sorted.size(); k++) {
                        if (k < ITEMS_PER_DRIVE) items.add(new DataEntry.Stock(sorted.get(k).getKey().proto(), sorted.get(k).getValue()));
                        else more++;
                    }
                    used = c.total();
                }
                out.add(new DataEntry.Slot(DataEntry.DRIVE, st.getHoverName(),
                        StorageFormat.count(used) + " / " + StorageFormat.count(lim.items()),
                        lim.items() <= 0 ? 0 : Math.min(1f, used / (float) lim.items()), items, more));
            } else if (st.getItem() instanceof ComputeModuleItem m) {
                StorageValues.Compute v = m.values();
                String text = v.cpu() > 0 && v.ram() > 0 ? v.cpu() + " CPU, " + StorageValues.gb(v.ram()) + " GB"
                        : v.cpu() > 0 ? v.cpu() + " CPU" : StorageValues.gb(v.ram()) + " GB";
                out.add(new DataEntry.Slot(DataEntry.MODULE, st.getHoverName(), text, -1f, List.of(), 0));
            } else {
                out.add(new DataEntry.Slot(DataEntry.EMPTY, Component.translatable("gui.nuclearsq.data.terminal.slot_empty"), "", -1f, List.of(), 0));
            }
        }
        return out;
    }

    @Nullable
    public static Component control(Level level, BlockPos terminal, BlockPos source, String key, double value) {
        return control(level, terminal, source, key, value, null);
    }

    @Nullable
    public static Component control(Level level, BlockPos terminal, BlockPos source, String key, double value, @Nullable ServerPlayer player) {
        DataSource s = DataNet.find(level, terminal, source);
        if (s == null) return Component.translatable("data.nuclearsq.msg.gone");
        if (player != null && !s.mayControl(player)) return Component.translatable("message.nuclearsq.access.denied");
        if (!controllable(s, key)) return Component.translatable("data.nuclearsq.err.readonly", key);
        return s.set(key, value);
    }

    public static boolean locate(ServerPlayer sp, BlockPos terminal, BlockPos source) {
        DataSource s = DataNet.find(sp.level(), terminal, source);
        if (s == null) {
            sp.displayClientMessage(Component.translatable("data.nuclearsq.msg.gone"), true);
            return false;
        }
        BlockPos p = s.pos();
        for (int i = 0; i < 40; i++) {
            sp.serverLevel().sendParticles(sp, ParticleTypes.END_ROD, true, p.getX() + 0.5, p.getY() + 1 + i * 0.4, p.getZ() + 0.5,
                    2, 0.08, 0.1, 0.08, 0.005);
        }
        sp.serverLevel().sendParticles(sp, ParticleTypes.GLOW, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.02);
        sp.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
        sp.displayClientMessage(Component.translatable("data.nuclearsq.msg.located", s.name(), p.getX(), p.getY(), p.getZ(),
                (int) Math.round(Math.sqrt(com.sqc.nuclearsq.compat.sable.SableCompat.distSqr(sp.level(), p, sp.blockPosition())))), true);
        return true;
    }
}
