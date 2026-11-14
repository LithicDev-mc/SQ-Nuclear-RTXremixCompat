package com.sqc.nuclearsq.compat.cc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.ServerRack;
import com.sqc.nuclearsq.storage.StorageAccess;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageTerminalMenu;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

public class StoragePeripheral implements IPeripheral {
    private static final int MAX_PAGE = 500;
    private static final int DEFAULT_PAGE = 100;

    private final Level level;
    private final BlockPos pos;

    public StoragePeripheral(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Override
    public String getType() {
        return "sqc_storage";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof StoragePeripheral p && p.level == level && p.pos.equals(pos);
    }

    private StorageAccess access() throws LuaException {
        if (level.isClientSide || level.getServer() == null) throw new LuaException("storage not available");
        return StorageNet.access(level, pos);
    }

    static String keyOf(ItemKey key) {
        String id = BuiltInRegistries.ITEM.getKey(key.proto().getItem()).toString();
        return key.proto().getComponentsPatch().isEmpty() ? id : id + "#" + Integer.toHexString(key.hashCode());
    }

    private record Row(String key, String label, long count, long reserved) {}

    private static List<Row> rows(StorageAccess s) {
        Map<ItemKey, Long> reserved = s.listReserved();
        List<Row> out = new ArrayList<>();
        s.list().forEach((k, n) -> out.add(new Row(keyOf(k), k.proto().getHoverName().getString(), n, reserved.getOrDefault(k, 0L))));
        out.sort((a, b) -> {
            int c = a.label.compareToIgnoreCase(b.label);
            return c != 0 ? c : a.key.compareTo(b.key);
        });
        return out;
    }

    private static Map<String, Object> map(Row r) {
        Map<String, Object> m = new HashMap<>();
        m.put("key", r.key);
        m.put("name", r.key.contains("#") ? r.key.substring(0, r.key.indexOf('#')) : r.key);
        m.put("label", r.label);
        m.put("count", r.count);
        m.put("reserved", r.reserved);
        m.put("available", r.count - r.reserved);
        return m;
    }

    private static List<Map<String, Object>> page(List<Row> rows, int offset, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = Math.max(0, offset); i < rows.size() && out.size() < limit; i++) out.add(map(rows.get(i)));
        return out;
    }

    private static int limit(Optional<Integer> limit) {
        return Math.max(1, Math.min(MAX_PAGE, limit.orElse(DEFAULT_PAGE)));
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getInfo() throws LuaException {
        StorageAccess s = access();
        Map<String, Object> m = new HashMap<>();
        m.put("racks", s.racks());
        m.put("onlineRacks", s.onlineRacks());
        m.put("drives", s.drives());
        m.put("used", s.used());
        m.put("capacity", s.capacity());
        m.put("types", s.list().size());
        m.put("complete", s.complete());
        return m;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getRacks() throws LuaException {
        if (level.isClientSide || level.getServer() == null) throw new LuaException("storage not available");
        List<Map<String, Object>> out = new ArrayList<>();
        int n = 0;
        for (BigMachineBlockEntity be : StorageNet.racks(level, pos)) {
            ServerRack r = be.rack();
            ServerRack.Space sp = r.space();
            Map<String, Object> m = new HashMap<>();
            m.put("index", ++n);
            m.put("x", be.getBlockPos().getX());
            m.put("y", be.getBlockPos().getY());
            m.put("z", be.getBlockPos().getZ());
            m.put("online", r.online());
            m.put("priority", r.priority());
            m.put("drives", sp.drives());
            m.put("used", sp.used());
            m.put("capacity", sp.capacity());
            m.put("types", sp.types());
            m.put("typeCapacity", sp.typeCapacity());
            out.add(m);
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public final int getItemCount() throws LuaException {
        return access().list().size();
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> list(Optional<Integer> offset, Optional<Integer> limit) throws LuaException {
        return page(rows(access()), offset.orElse(0), limit(limit));
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> find(String query, Optional<Integer> limit) throws LuaException {
        String q = query.toLowerCase(Locale.ROOT);
        List<Row> hits = new ArrayList<>();
        for (Row r : rows(access())) {
            if (r.label.toLowerCase(Locale.ROOT).contains(q) || r.key.toLowerCase(Locale.ROOT).contains(q)) hits.add(r);
        }
        return page(hits, 0, limit(limit));
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getItem(String key) throws LuaException {
        for (Row r : rows(access())) {
            if (r.key.equals(key)) return map(r);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("name", key.contains("#") ? key.substring(0, key.indexOf('#')) : key);
        m.put("label", key);
        m.put("count", 0L);
        m.put("reserved", 0L);
        m.put("available", 0L);
        return m;
    }

    @LuaFunction(mainThread = true)
    public final List<String> getCraftables() throws LuaException {
        access();
        List<String> out = new ArrayList<>();
        for (net.minecraft.world.item.ItemStack st : StorageTerminalMenu.craftables(level, pos)) {
            out.add(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
        }
        java.util.Collections.sort(out);
        return out;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> order(String item, int amount) throws LuaException {
        access();
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(item);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) throw new LuaException("unknown item '" + item + "'");
        if (amount < 1) throw new LuaException("amount must be at least 1");
        StorageTerminalMenu.Order r = StorageTerminalMenu.placeOrder(level, pos,
                new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(id)), amount);
        Map<String, Object> m = new HashMap<>();
        m.put("ok", r.ok());
        m.put("message", r.message().getString());
        return m;
    }
}
