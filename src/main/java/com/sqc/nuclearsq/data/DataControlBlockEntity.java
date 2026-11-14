package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DataControlBlockEntity extends BlockEntity {
    public static final int PERIOD = 40;
    static final Set<String> SWITCHES = Set.of("enable", "run", "manual", "rod_mode", "open");
    static final Set<String> ACTIONS = Set.of("scram", "reset_peak", "cycle");
    static final Set<String> RANGES = Set.of("rod", "throttle", "channel", "radius");

    @Nullable
    private BlockPos source;
    private String key = "";

    public DataControlBlockEntity(BlockPos pos, BlockState state) {
        super(DataRegistry.CONTROL_BE.get(), pos, state);
    }

    public interface Listener {
        void used(String kind, double value, String player);
    }

    public final java.util.Map<Object, Listener> listeners = new java.util.concurrent.ConcurrentHashMap<>();
    private double lastValue;

    public double lastValue() { return lastValue; }

    public String kindName() {
        return switch (kind()) {
            case DataControlBlock.LEVER -> "lever";
            case DataControlBlock.SLIDER -> "slider";
            default -> "button";
        };
    }

    private void fire(Player player, double value) {
        lastValue = value;
        for (Listener l : listeners.values()) l.used(kindName(), value, player.getName().getString());
    }

    @Nullable public BlockPos source() { return source; }
    public String key() { return key; }

    private int kind() {
        return getBlockState().getBlock() instanceof DataControlBlock b ? b.kind : DataControlBlock.BUTTON;
    }

    static boolean isSwitch(String k) {
        return SWITCHES.contains(k) || k.startsWith("lock.");
    }

    public static List<String> keysFor(int kind, DataSource s) {
        List<String> out = new ArrayList<>();
        for (String k : s.settable()) {
            boolean ok = switch (kind) {
                case DataControlBlock.LEVER -> isSwitch(k);
                case DataControlBlock.SLIDER -> RANGES.contains(k) && s.value(k) != null && s.value(k).max() > 0;
                default -> ACTIONS.contains(k) || k.startsWith("dump.") || isSwitch(k);
            };
            if (ok) out.add(k);
        }
        return out;
    }

    @Nullable
    private DataSource target() {
        if (level == null || source == null || key.isEmpty()) return null;
        return DataNet.find(level, worldPosition, source);
    }

    public void cycle(Player player) {
        if (level == null) return;
        List<DataSource> net = DataNet.scan(level, worldPosition);
        List<DataSource> usable = new ArrayList<>();
        for (DataSource s : net) if (!keysFor(kind(), s).isEmpty()) usable.add(s);
        if (usable.isEmpty()) {
            source = null;
            key = "";
            setChanged();
            player.displayClientMessage(Component.translatable(net.isEmpty() ? "data.nuclearsq.msg.none" : "data.nuclearsq.msg.control_nothing"), true);
            return;
        }
        int si = -1;
        for (int i = 0; i < usable.size(); i++) if (usable.get(i).pos().equals(source)) si = i;
        DataSource s;
        String k;
        if (si < 0) {
            s = usable.get(0);
            k = keysFor(kind(), s).get(0);
        } else {
            List<String> keys = keysFor(kind(), usable.get(si));
            int ki = keys.indexOf(key);
            if (ki >= 0 && ki + 1 < keys.size()) {
                s = usable.get(si);
                k = keys.get(ki + 1);
            } else {
                s = usable.get((si + 1) % usable.size());
                k = keysFor(kind(), s).get(0);
            }
        }
        source = s.pos();
        key = k;
        setChanged();
        player.displayClientMessage(Component.translatable("data.nuclearsq.msg.control_bound", s.name(), keyLabel(s, k)), true);
        follow(s);
    }

    static Component keyLabel(DataSource s, String k) {
        DataValue v = s.value(k);
        if (v != null) return v.label();
        if (k.startsWith("dump.")) {
            DataValue t = s.value("tank." + k.substring(5));
            return Component.translatable("data.nuclearsq.ctl.dump", t != null ? t.label() : Component.literal(k.substring(5)));
        }
        if (k.startsWith("lock.")) return Component.translatable("data.nuclearsq.ctl.lock", k.substring(5));
        return Component.translatable("data.nuclearsq.ctl." + k);
    }

    public void press(Player player) {
        fire(player, 1);
        DataSource s = target();
        if (s == null) {
            hint(player);
            return;
        }
        double v = 1;
        if (isSwitch(key)) {
            DataValue cur = s.value(key);
            v = cur != null && cur.value() != 0 ? 0 : 1;
        }
        apply(player, s, v);
    }

    public void lever(Player player, boolean on) {
        fire(player, on ? 1 : 0);
        DataSource s = target();
        if (s == null) {
            if (source != null) hint(player);
            return;
        }
        apply(player, s, on ? 1 : 0);
    }

    public void slide(Player player, int lvl) {
        fire(player, lvl);
        DataSource s = target();
        if (s == null) {
            if (source != null) hint(player);
            else player.displayClientMessage(Component.translatable("data.nuclearsq.msg.control_level", lvl), true);
            return;
        }
        apply(player, s, value(s.value(key), lvl));
    }

    static double value(@Nullable DataValue v, int lvl) {
        double max = v == null ? 15 : v.max();
        return Math.round(lvl * max / 15.0);
    }

    static int level(DataValue v) {
        if (v.max() <= 0) return 0;
        return Math.max(0, Math.min(15, (int) Math.round(v.value() * 15.0 / v.max())));
    }

    private void apply(Player player, DataSource s, double v) {
        Component err = player instanceof net.minecraft.server.level.ServerPlayer sp && !s.mayControl(sp) ? Component.translatable("message.nuclearsq.access.denied") : s.set(key, v);
        if (err != null) {
            player.displayClientMessage(err.copy().withStyle(ChatFormatting.RED), true);
        } else {
            player.displayClientMessage(Component.translatable("data.nuclearsq.msg.control_set", s.name(), keyLabel(s, key),
                    DataValue.number(v)), true);
        }
        follow(s);
    }

    private void hint(Player player) {
        player.displayClientMessage(Component.translatable(source == null ? "data.nuclearsq.msg.control_unbound" : "data.nuclearsq.msg.control_lost"), true);
    }

    public void serverTick() {
        if (level == null || source == null || key.isEmpty()) return;
        if (level.getGameTime() % PERIOD != Math.floorMod(worldPosition.asLong(), PERIOD)) return;
        DataSource s = target();
        if (s != null) follow(s);
    }

    private void follow(DataSource s) {
        if (level == null || !(getBlockState().getBlock() instanceof DataControlBlock b)) return;
        DataValue v = s.value(key.equals("rod") ? "rod_target" : key);
        if (v == null) return;
        BlockState st = getBlockState();
        if (b.kind == DataControlBlock.LEVER) b.setState(level, worldPosition, st.setValue(DataControlBlock.ON, v.value() != 0));
        else if (b.kind == DataControlBlock.SLIDER) b.setState(level, worldPosition, st.setValue(DataControlBlock.LEVEL, level(v)));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (source != null) tag.put("source", NbtUtils.writeBlockPos(source));
        tag.putString("key", key);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        source = NbtUtils.readBlockPos(tag, "source").orElse(null);
        key = tag.getString("key");
    }
}
