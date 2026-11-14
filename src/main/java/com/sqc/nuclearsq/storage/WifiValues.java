package com.sqc.nuclearsq.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class WifiValues {
    private WifiValues() {}

    public record Adapter(int[] range, int fePerTick, int buffer, int maxReceive, int chargeRadius, int chargeRate) {
        public int maxAntennas() {
            return range.length - 1;
        }

        public int rangeFor(int antennas) {
            return range[Math.max(0, Math.min(antennas, range.length - 1))];
        }
    }

    public record Terminal(int capacity, int openCost, int craftCost, int maxReceive) {}

    private static Adapter adapter;
    private static Terminal terminal;

    public static Adapter adapter() {
        if (adapter == null) load();
        return adapter;
    }

    public static Terminal terminal() {
        if (terminal == null) load();
        return terminal;
    }

    private static synchronized void load() {
        if (adapter != null) return;
        try (InputStream in = WifiValues.class.getResourceAsStream("/data/nuclearsq/wifi_values.json")) {
            if (in == null) throw new IllegalStateException("wifi_values.json fehlt");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject a = root.getAsJsonObject("adapter"), t = root.getAsJsonObject("terminal");
            JsonArray r = a.getAsJsonArray("range");
            int[] range = new int[r.size()];
            for (int i = 0; i < range.length; i++) range[i] = r.get(i).getAsInt();
            terminal = new Terminal(t.get("capacity").getAsInt(), t.get("open_cost").getAsInt(), t.get("craft_cost").getAsInt(),
                    t.get("max_receive").getAsInt());
            adapter = new Adapter(range, a.get("fe_per_tick").getAsInt(), a.get("buffer").getAsInt(), a.get("max_receive").getAsInt(),
                    a.get("charge_radius").getAsInt(), a.get("charge_rate").getAsInt());
        } catch (IOException e) {
            throw new IllegalStateException("wifi_values.json nicht lesbar: " + e.getMessage(), e);
        }
    }
}
