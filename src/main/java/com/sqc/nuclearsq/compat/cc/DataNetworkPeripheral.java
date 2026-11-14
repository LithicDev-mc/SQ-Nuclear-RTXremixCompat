package com.sqc.nuclearsq.compat.cc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.data.DataConsole;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataValue;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class DataNetworkPeripheral implements IPeripheral {
    private final Level level;
    private final BlockPos pos;

    public DataNetworkPeripheral(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Override
    public String getType() {
        return "sqc_network";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof DataNetworkPeripheral p && p.level == level && p.pos.equals(pos);
    }

    @Override
    public void attach(IComputerAccess computer) {
        Map<BlockPos, Integer> last = new HashMap<>();
        CcEvents.watch(this, computer, c -> {
            List<DataSource> net = net();
            Map<BlockPos, Integer> now = new HashMap<>();
            for (int i = 0; i < net.size(); i++) {
                DataSource s = net.get(i);
                int st = s.state();
                now.put(s.pos(), st);
                Integer old = last.get(s.pos());
                if (old != null && old != st) {
                    c.queueEvent("sqc_state", c.getAttachmentName(), i + 1, s.name().getString(), state(old), state(st),
                            s.stateText().getString());
                }
            }
            last.clear();
            last.putAll(now);
        });
    }

    @Override
    public void detach(IComputerAccess computer) {
        CcEvents.unwatch(this, computer);
    }

    private List<DataSource> net() {
        return DataNet.scan(level, pos);
    }

    private DataSource get(int n) throws LuaException {
        List<DataSource> net = net();
        if (n < 1 || n > net.size()) throw new LuaException("no machine #" + n + " (network has " + net.size() + ")");
        return net.get(n - 1);
    }

    private static String state(int s) {
        return switch (s) {
            case DataSource.OK -> "ok";
            case DataSource.WARN -> "warning";
            case DataSource.ALARM -> "alarm";
            default -> "off";
        };
    }

    private Map<String, Object> head(DataSource s, int n) {
        Map<String, Object> m = new HashMap<>();
        m.put("index", n);
        m.put("name", s.name().getString());
        m.put("type", s.type());
        m.put("state", state(s.state()));
        m.put("stateText", s.stateText().getString());
        m.put("x", s.pos().getX());
        m.put("y", s.pos().getY());
        m.put("z", s.pos().getZ());
        return m;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<DataSource> net = net();
        for (int i = 0; i < net.size(); i++) out.add(head(net.get(i), i + 1));
        return out;
    }

    @LuaFunction(mainThread = true)
    public final int count() {
        return net().size();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getInfo(int n) throws LuaException {
        DataSource s = get(n);
        Map<String, Object> m = head(s, n);
        Map<String, Object> values = new HashMap<>(), max = new HashMap<>(), units = new HashMap<>(), labels = new HashMap<>();
        for (DataValue v : s.values()) {
            values.put(v.key(), v.value());
            if (v.max() > 0) max.put(v.key(), v.max());
            units.put(v.key(), v.unit());
            labels.put(v.key(), v.label().getString());
        }
        m.put("values", values);
        m.put("max", max);
        m.put("units", units);
        m.put("labels", labels);
        m.put("settable", s.settable());
        return m;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getStatus(int n) throws LuaException {
        DataSource s = get(n);
        return Map.of("state", state(s.state()), "text", s.stateText().getString());
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getTanks(int n) throws LuaException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DataValue v : get(n).values()) {
            if (!v.unit().equals("mB")) continue;
            out.add(Map.of("key", v.key(), "label", v.label().getString(), "amount", v.value(), "capacity", v.max()));
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public final double getValue(int n, String key) throws LuaException {
        DataValue v = DataConsole.value(get(n), key);
        if (v == null) throw new LuaException("no value '" + key + "'");
        return v.value();
    }

    @LuaFunction(mainThread = true)
    public final boolean set(int n, String key, double value) throws LuaException {
        DataSource s = get(n);
        if (!s.settable().contains(key)) throw new LuaException("'" + key + "' is not settable here, use one of " + s.settable());
        Component err = s.set(key, value);
        if (err != null) throw new LuaException(err.getString());
        return true;
    }

    private boolean setFlag(int n, String key, boolean on) throws LuaException {
        return set(n, key, on ? 1 : 0);
    }

    @LuaFunction(mainThread = true)
    public final boolean setEnabled(int n, boolean on) throws LuaException {
        DataSource s = get(n);
        return setFlag(n, s.settable().contains("run") ? "run" : "enable", on);
    }

    @LuaFunction(mainThread = true)
    public final boolean setThrottle(int n, int percent) throws LuaException {
        return set(n, "throttle", percent);
    }

    @LuaFunction(mainThread = true)
    public final boolean setDepth(int n, int depth) throws LuaException {
        return set(n, "rod", depth);
    }

    @LuaFunction(mainThread = true)
    public final boolean scram(int n) throws LuaException {
        return set(n, "scram", 1);
    }

    @LuaFunction(mainThread = true)
    public final boolean setRodTarget(int n, int position) throws LuaException {
        return set(n, "rod", position);
    }

    @LuaFunction(mainThread = true)
    public final boolean setRodMode(int n, boolean terminal) throws LuaException {
        return setFlag(n, "rod_mode", terminal);
    }

    @LuaFunction(mainThread = true)
    public final boolean setChannel(int n, int channel) throws LuaException {
        return set(n, "channel", channel);
    }

    @LuaFunction(mainThread = true)
    public final boolean lockRod(int n, int shaft, boolean locked) throws LuaException {
        return setFlag(n, "lock." + shaft, locked);
    }

    @LuaFunction(mainThread = true)
    public final boolean setManual(int n, boolean manual) throws LuaException {
        return setFlag(n, "manual", manual);
    }

    @LuaFunction(mainThread = true)
    public final boolean dumpTank(int n, String port) throws LuaException {
        return set(n, "dump." + port.replace("tank.", ""), 1);
    }
}
