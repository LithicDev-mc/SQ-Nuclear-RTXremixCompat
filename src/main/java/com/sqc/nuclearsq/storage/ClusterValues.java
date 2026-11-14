package com.sqc.nuclearsq.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class ClusterValues {
    private ClusterValues() {}

    public record Machine(int cpu, int ramMb, int fePerTick) {}

    public record Core(int fePerTick, int buffer, int maxReceive, int tank) {}

    public record Coprocessor(int cpu, int fePerTick) {}

    public record Ram(int gb, int fePerTick) {}

    public record Heat(double perFe, double passive, double coolTarget, double coolPerMb, double throttleFrom, int minPct,
                       double shutdown, double restart) {}

    private static int maxSize;
    private static Machine machine;
    private static Core core;
    private static Coprocessor coprocessor;
    private static Ram[] ram;
    private static Heat heat;

    public static int maxSize() {
        ensure();
        return maxSize;
    }

    public static Machine machine() {
        ensure();
        return machine;
    }

    public static Core core() {
        ensure();
        return core;
    }

    public static Coprocessor coprocessor() {
        ensure();
        return coprocessor;
    }

    public static Ram ram(int tier) {
        ensure();
        return ram[Math.max(0, Math.min(tier, ram.length - 1))];
    }

    public static int ramTiers() {
        ensure();
        return ram.length;
    }

    public static Heat heat() {
        ensure();
        return heat;
    }

    private static void ensure() {
        if (core == null) load();
    }

    private static synchronized void load() {
        if (core != null) return;
        try (InputStream in = ClusterValues.class.getResourceAsStream("/data/nuclearsq/cluster_values.json")) {
            if (in == null) throw new IllegalStateException("cluster_values.json fehlt");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject c = root.getAsJsonObject("core"), p = root.getAsJsonObject("coprocessor"), h = root.getAsJsonObject("heat");
            JsonArray r = root.getAsJsonArray("ram");
            Ram[] rams = new Ram[r.size()];
            for (int i = 0; i < rams.length; i++) {
                JsonObject o = r.get(i).getAsJsonObject();
                rams[i] = new Ram(o.get("gb").getAsInt(), o.get("fe_per_tick").getAsInt());
            }
            coprocessor = new Coprocessor(p.get("cpu").getAsInt(), p.get("fe_per_tick").getAsInt());
            heat = new Heat(h.get("per_fe").getAsDouble(), h.get("passive").getAsDouble(), h.get("cool_target").getAsDouble(),
                    h.get("cool_per_mb").getAsDouble(), h.get("throttle_from").getAsDouble(), h.get("min_pct").getAsInt(),
                    h.get("shutdown").getAsDouble(), h.get("restart").getAsDouble());
            JsonObject mc = root.getAsJsonObject("machine");
            machine = new Machine(mc.get("cpu").getAsInt(), mc.get("ram_gb").getAsInt() * StorageValues.MB_PER_GB, mc.get("fe_per_tick").getAsInt());
            ram = rams;
            maxSize = root.get("max_size").getAsInt();
            core = new Core(c.get("fe_per_tick").getAsInt(), c.get("buffer").getAsInt(), c.get("max_receive").getAsInt(), c.get("tank").getAsInt());
        } catch (IOException e) {
            throw new IllegalStateException("cluster_values.json nicht lesbar: " + e.getMessage(), e);
        }
    }
}
