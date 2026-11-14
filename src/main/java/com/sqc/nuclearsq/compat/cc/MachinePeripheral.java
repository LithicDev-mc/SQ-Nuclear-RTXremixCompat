package com.sqc.nuclearsq.compat.cc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.data.DataConsole;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.data.DataValue;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.storage.ComponentAssembler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class MachinePeripheral implements IPeripheral {
    private final Level level;
    private final BlockPos pos;

    public MachinePeripheral(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Override
    public String getType() {
        return "sqc_machine";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof MachinePeripheral p && p.level == level && p.pos.equals(pos);
    }

    @Override
    public void attach(IComputerAccess computer) {
        int[] last = {-1};
        CcEvents.watch(this, computer, c -> {
            DataSource s = DataSources.at(level, pos);
            if (s == null) return;
            int now = s.state();
            if (last[0] >= 0 && now != last[0]) {
                c.queueEvent("sqc_state", c.getAttachmentName(), state(last[0]), state(now), s.stateText().getString());
            }
            last[0] = now;
        });
    }

    @Override
    public void detach(IComputerAccess computer) {
        CcEvents.unwatch(this, computer);
    }

    private BigMachineBlockEntity big() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BigPortBlockEntity port) be = port.findController();
        return be instanceof BigMachineBlockEntity m ? m : null;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getJobs() throws LuaException {
        get();
        List<Map<String, Object>> out = new ArrayList<>();
        BigMachineBlockEntity m = big();
        ComponentAssembler asm = m == null ? null : m.assembler();
        if (asm == null) return out;
        for (ComponentAssembler.Job j : asm.jobs()) {
            Map<String, Object> e = new HashMap<>();
            e.put("item", BuiltInRegistries.ITEM.getKey(j.target.getItem()).toString());
            e.put("amount", j.amount);
            e.put("step", j.step() + 1);
            e.put("steps", j.steps());
            e.put("leftInStep", j.leftInStep());
            out.add(e);
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getCraft() throws LuaException {
        DataSource s = get();
        Map<String, Object> out = new HashMap<>();
        BigMachineBlockEntity m = big();
        BigCrafter c = m == null ? null : m.crafter();
        if (c == null) return out;
        out.put("status", s.stateText().getString());
        out.put("progress", c.progress());
        out.put("maxProgress", c.maxProgress());
        out.put("needed", c.needed());
        ItemStack last = c.lastResult();
        out.put("lastItem", last.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(last.getItem()).toString());
        out.put("lastCount", last.getCount());
        return out;
    }

    private DataSource get() throws LuaException {
        DataSource s = DataSources.at(level, pos);
        if (s == null) throw new LuaException("no machine here (port without controller?)");
        return s;
    }

    static String state(int s) {
        return switch (s) {
            case DataSource.OK -> "ok";
            case DataSource.WARN -> "warning";
            case DataSource.ALARM -> "alarm";
            default -> "off";
        };
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getInfo() throws LuaException {
        DataSource s = get();
        Map<String, Object> m = new HashMap<>();
        m.put("name", s.name().getString());
        m.put("type", s.type());
        m.put("state", state(s.state()));
        m.put("stateText", s.stateText().getString());
        m.put("x", s.pos().getX());
        m.put("y", s.pos().getY());
        m.put("z", s.pos().getZ());
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
    public final String getMachineType() throws LuaException {
        return get().type();
    }

    @LuaFunction(mainThread = true)
    public final String getName() throws LuaException {
        return get().name().getString();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getStatus() throws LuaException {
        DataSource s = get();
        return Map.of("state", state(s.state()), "text", s.stateText().getString());
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getValues() throws LuaException {
        Map<String, Object> out = new HashMap<>();
        for (DataValue v : get().values()) out.put(v.key(), v.value());
        return out;
    }

    @LuaFunction(mainThread = true)
    public final double getValue(String key) throws LuaException {
        DataValue v = DataConsole.value(get(), key);
        if (v == null) throw new LuaException("no value '" + key + "'");
        return v.value();
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getTanks() throws LuaException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DataValue v : get().values()) {
            if (!v.unit().equals("mB")) continue;
            out.add(Map.of("key", v.key(), "label", v.label().getString(), "amount", v.value(), "capacity", v.max()));
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public final List<String> getSettable() throws LuaException {
        return get().settable();
    }

    @LuaFunction(mainThread = true)
    public final boolean set(String key, double value) throws LuaException {
        DataSource s = get();
        if (!s.settable().contains(key)) throw new LuaException("'" + key + "' is not settable here, use one of " + s.settable());
        Component err = s.set(key, value);
        if (err != null) throw new LuaException(err.getString());
        return true;
    }

    @LuaFunction(mainThread = true)
    public final boolean setEnabled(boolean on) throws LuaException {
        return set(get().settable().contains("run") ? "run" : "enable", on ? 1 : 0);
    }

    @LuaFunction(mainThread = true)
    public final boolean isEnabled() throws LuaException {
        DataSource s = get();
        DataValue v = s.value("run");
        if (v == null) v = s.value("enable");
        if (v == null) throw new LuaException("this machine has no on/off switch");
        return v.value() != 0;
    }

    @LuaFunction(mainThread = true)
    public final boolean dumpTank(String tank) throws LuaException {
        return set("dump." + tank.replace("tank.", ""), 1);
    }
}
