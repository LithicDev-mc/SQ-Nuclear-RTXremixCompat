package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DataDisplay {
    private DataDisplay() {}

    static final Set<String> HIDDEN = Set.of("enable", "run", "rod_mode", "channel", "overheated", "manual", "working", "alarm",
            "dose_rate", "peak", "threshold", "hottest_x", "hottest_y", "hottest_z", "radius", "priority");

    private static final Map<String, Integer> RANK = Map.ofEntries(
            Map.entry("power", 0), Map.entry("out_rate", 0), Map.entry("fe_rate", 0), Map.entry("rpm", 0),
            Map.entry("dose_rate_usv", 0), Map.entry("dose_rate_max_usv", 1), Map.entry("fill", 0),
            Map.entry("dose_rate_avg_usv", 5), Map.entry("heat", 10), Map.entry("fuel", 12), Map.entry("progress", 14),
            Map.entry("primary_flow", 16), Map.entry("vessel_temp", 18), Map.entry("rate", 18), Map.entry("in_rate", 20),
            Map.entry("line_rate", 22), Map.entry("efficiency", 24), Map.entry("storage_used", 20), Map.entry("jobs", 25),
            Map.entry("types", 26), Map.entry("drives", 27), Map.entry("steam", 28), Map.entry("energy", 30),
            Map.entry("rods", 40), Map.entry("cooled", 41), Map.entry("rod", 60), Map.entry("rod_target", 62),
            Map.entry("throttle", 64), Map.entry("signal", 66), Map.entry("peak_usv", 68), Map.entry("cooling", 70),
            Map.entry("threshold_usv", 72));

    static int rank(String key) {
        Integer r = RANK.get(key);
        if (r != null) return r;
        return key.startsWith("tank.") ? 50 : 80;
    }

    public static List<DataValue> order(DataSource s) {
        List<String> prio = s.priority();
        List<DataValue> out = new ArrayList<>(s.values());
        out.sort(Comparator.comparingInt((DataValue v) -> {
            int p = prio.indexOf(v.key());
            if (p >= 0) return -1000 + p;
            return HIDDEN.contains(v.key()) ? 1000 : rank(v.key());
        }));
        return out;
    }

    public static List<DataValue> pick(DataSource s, int max) {
        List<DataValue> all = order(s), out = new ArrayList<>();
        for (DataValue v : all) {
            if (out.size() >= max) break;
            if (!HIDDEN.contains(v.key())) out.add(v);
        }
        if (out.isEmpty()) for (DataValue v : all) {
            if (out.size() >= max) break;
            out.add(v);
        }
        return out;
    }
}
