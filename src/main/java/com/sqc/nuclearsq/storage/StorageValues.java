package com.sqc.nuclearsq.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class StorageValues {
    private StorageValues() {}

    public record Drive(long items, int types, int fePerTick) {}

    public record Compute(int cpu, int ram, int fePerTick) {}

    public record Demand(int cpu, int ram) {}

    public record Assembler(int[] speedPct, int[] powerPct, int baseLanes, int maxLanes, int baseSize, int craftsPerGb, int stallTicks) {
        public int tempoMax() {
            return speedPct.length - 1;
        }

        public int speed(int level) {
            return speedPct[Math.max(0, Math.min(tempoMax(), level))];
        }

        public int power(int level) {
            return powerPct[Math.max(0, Math.min(tempoMax(), level))];
        }
    }

    public record Production(int[] cpu, int[] ram, int[] normalSpeed, int[] normalPower, int[] turboSpeed, int[] turboPower) {
        public int stages() {
            return cpu.length;
        }
    }

    private static Map<String, Drive> drives;
    private static Production production;
    private static Assembler assembler;
    private static Map<String, Compute> modules;
    private static Map<String, Demand> demand;

    public static Drive drive(String id) {
        if (drives == null) load();
        Drive d = drives.get(id);
        if (d == null) throw new IllegalStateException("storage_values.json: Festplatte " + id + " fehlt");
        return d;
    }

    public static Compute module(String id) {
        if (drives == null) load();
        Compute c = modules.get(id);
        if (c == null) throw new IllegalStateException("storage_values.json: Rechenmodul " + id + " fehlt");
        return c;
    }

    @javax.annotation.Nullable
    public static Demand demand(String blockId) {
        if (drives == null) load();
        return demand.get(blockId);
    }

    public static Assembler assembler() {
        if (drives == null) load();
        return assembler;
    }

    public static Production production() {
        if (drives == null) load();
        return production;
    }

    public static final int MB_PER_GB = 1024;

    private static int mb(com.google.gson.JsonElement e) {
        return (int) Math.round(e.getAsDouble() * MB_PER_GB);
    }

    private static int[] mbs(com.google.gson.JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = mb(a.get(i));
        return out;
    }

    public static String gb(int mb) {
        if (mb % MB_PER_GB == 0) return Integer.toString(mb / MB_PER_GB);
        String s = String.format(java.util.Locale.ROOT, "%.2f", mb / (double) MB_PER_GB);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static int[] ints(com.google.gson.JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
        return out;
    }

    private static synchronized void load() {
        if (drives != null) return;
        Map<String, Drive> m = new HashMap<>();
        Map<String, Compute> mods = new HashMap<>();
        Map<String, Demand> need = new HashMap<>();
        try (InputStream in = StorageValues.class.getResourceAsStream("/data/nuclearsq/storage_values.json")) {
            if (in == null) throw new IllegalStateException("storage_values.json fehlt");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.getAsJsonObject("drives").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                m.put(e.getKey(), new Drive(o.get("items").getAsLong(), o.get("types").getAsInt(), o.get("fe_per_tick").getAsInt()));
            }
            for (var e : root.getAsJsonObject("compute_modules").entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                JsonObject o = e.getValue().getAsJsonObject();
                mods.put(e.getKey(), new Compute(o.get("cpu").getAsInt(), mb(o.get("ram")), o.get("fe_per_tick").getAsInt()));
            }
            for (var e : root.getAsJsonObject("compute_demand").entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                JsonObject o = e.getValue().getAsJsonObject();
                need.put(e.getKey(), new Demand(o.get("cpu").getAsInt(), mb(o.get("ram"))));
            }
            JsonObject as = root.getAsJsonObject("assembler");
            int[] speed = ints(as.getAsJsonArray("tempo_speed_pct")), power = ints(as.getAsJsonArray("tempo_power_pct"));
            if (speed.length < 2 || speed.length != power.length) throw new IllegalStateException("storage_values.json: tempo_speed_pct und tempo_power_pct passen nicht");
            assembler = new Assembler(speed, power, Math.max(1, as.get("base_lanes").getAsInt()), Math.max(1, as.get("max_lanes").getAsInt()),
                    Math.max(1, as.get("base_size").getAsInt()), Math.max(1, as.get("crafts_per_gb").getAsInt()), Math.max(20, as.get("stall_ticks").getAsInt()));
            JsonObject pr = root.getAsJsonObject("production");
            int[] pc = ints(pr.getAsJsonArray("cpu")), pm = mbs(pr.getAsJsonArray("ram"));
            int[][] cols = {ints(pr.getAsJsonArray("normal_speed_pct")), ints(pr.getAsJsonArray("normal_power_pct")),
                    ints(pr.getAsJsonArray("turbo_speed_pct")), ints(pr.getAsJsonArray("turbo_power_pct"))};
            for (int[] c : cols) {
                if (c.length != pc.length) throw new IllegalStateException("storage_values.json: production hat Spalten unterschiedlicher Laenge");
            }
            if (pc.length < 1 || pm.length != pc.length) throw new IllegalStateException("storage_values.json: production cpu und ram passen nicht");
            production = new Production(pc, pm, cols[0], cols[1], cols[2], cols[3]);
        } catch (IOException e) {
            throw new IllegalStateException("storage_values.json nicht lesbar: " + e.getMessage(), e);
        }
        modules = mods;
        demand = need;
        drives = m;
    }
}
