package com.sqc.nuclearsq.compat.cc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.data.PowerMonitorBlock;
import com.sqc.nuclearsq.data.PowerMonitorBlockEntity;
import com.sqc.nuclearsq.data.PowerSample;
import com.sqc.nuclearsq.data.PowerStats;
import com.sqc.nuclearsq.data.PowerSummary;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class PowerPeripheral implements IPeripheral {
    private final Level level;
    private final BlockPos pos;

    public PowerPeripheral(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Override
    public String getType() {
        return "sqc_power";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof PowerPeripheral p && p.level == level && p.pos.equals(pos);
    }

    private PowerMonitorBlockEntity head() throws LuaException {
        if (PowerMonitorBlock.head(level, pos) instanceof PowerMonitorBlockEntity be) return be;
        throw new LuaException("power monitor is gone");
    }

    private static Map<String, Object> table(PowerSample s) {
        Map<String, Object> m = new HashMap<>();
        m.put("production", s.prod());
        m.put("consumption", s.cons());
        m.put("balance", s.balance());
        m.put("stored", s.stored());
        m.put("capacity", s.capacity());
        m.put("fill", s.fill());
        m.put("charging", s.in());
        m.put("discharging", s.out());
        return m;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> status() throws LuaException {
        PowerMonitorBlockEntity be = head();
        Map<String, Object> m = table(be.latest());
        m.put("peak", be.summary().peak());
        m.put("plants", be.summary().plants());
        PowerStats.Eta eta = PowerStats.eta(be.fine());
        m.put("eta", eta.kind().name().toLowerCase(java.util.Locale.ROOT));
        m.put("etaSeconds", eta.seconds());
        return m;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> history(int n, boolean slow) throws LuaException {
        PowerMonitorBlockEntity be = head();
        List<PowerSample> src = slow ? be.coarse() : be.fine();
        int count = Math.max(0, Math.min(Math.min(n, 120), src.size()));
        List<Map<String, Object>> out = new ArrayList<>(count);
        for (PowerSample s : src.subList(src.size() - count, src.size())) out.add(table(s));
        return out;
    }

    private static List<Map<String, Object>> items(List<PowerSummary.Item> list, String a, String b) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PowerSummary.Item i : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", i.name().getString());
            m.put("count", i.count());
            m.put(a, i.a());
            if (b != null) m.put(b, i.b());
            out.add(m);
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> producers() throws LuaException {
        return items(head().summary().producers(), "power", "peak");
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> consumers() throws LuaException {
        return items(head().summary().consumers(), "power", null);
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> storages() throws LuaException {
        return items(head().summary().storages(), "stored", "capacity");
    }
}
