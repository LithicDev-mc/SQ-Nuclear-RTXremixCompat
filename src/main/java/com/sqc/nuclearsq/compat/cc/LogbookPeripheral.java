package com.sqc.nuclearsq.compat.cc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.data.LogEntry;
import com.sqc.nuclearsq.data.LogbookBlockEntity;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class LogbookPeripheral implements IPeripheral {
    private final Level level;
    private final BlockPos pos;

    public LogbookPeripheral(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Override
    public String getType() {
        return "sqc_logbook";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof LogbookPeripheral p && p.level == level && p.pos.equals(pos);
    }

    @Override
    public void attach(IComputerAccess computer) {
        long[] next = {-1};
        CcEvents.watch(this, computer, c -> {
            if (!(com.sqc.nuclearsq.data.LogbookBlock.book(level, pos) instanceof LogbookBlockEntity be)) return;
            if (next[0] < 0) next[0] = be.nextSeq();
            for (LogEntry e : be.since(next[0])) {
                c.queueEvent("sqc_log", c.getAttachmentName(), e.seq(), severity(e.severity()), e.text().getString(),
                        LogEntry.clock(e.dayTime()));
            }
            next[0] = be.nextSeq();
        });
    }

    @Override
    public void detach(IComputerAccess computer) {
        CcEvents.unwatch(this, computer);
    }

    private List<LogEntry> entries() throws LuaException {
        if (com.sqc.nuclearsq.data.LogbookBlock.book(level, pos) instanceof LogbookBlockEntity be) return be.entries();
        throw new LuaException("logbook is gone");
    }

    private static String severity(int s) {
        return switch (s) {
            case LogEntry.CLEAR -> "clear";
            case LogEntry.WARN -> "warning";
            case LogEntry.ALARM -> "alarm";
            default -> "info";
        };
    }

    private static Map<String, Object> table(LogEntry e) {
        Map<String, Object> m = new HashMap<>();
        m.put("seq", e.seq());
        m.put("day", LogEntry.day(e.dayTime()));
        m.put("time", LogEntry.clock(e.dayTime()));
        m.put("epoch", e.millis());
        m.put("severity", severity(e.severity()));
        m.put("text", e.text().getString());
        return m;
    }

    @LuaFunction(mainThread = true)
    public final int count() throws LuaException {
        return entries().size();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> get(int n) throws LuaException {
        List<LogEntry> all = entries();
        if (n < 1 || n > all.size()) throw new LuaException("no entry #" + n + " (logbook has " + all.size() + ")");
        return table(all.get(n - 1));
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getLast(int n) throws LuaException {
        List<LogEntry> all = entries();
        int count = Math.max(0, Math.min(Math.min(n, 100), all.size()));
        List<Map<String, Object>> out = new ArrayList<>(count);
        for (LogEntry e : all.subList(all.size() - count, all.size())) out.add(table(e));
        return out;
    }
}
