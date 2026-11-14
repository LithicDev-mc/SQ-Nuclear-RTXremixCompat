package com.sqc.nuclearsq.data;

import java.util.List;

public final class PowerStats {
    private PowerStats() {}

    public static final int ETA_WINDOW = 10;
    public static final double STEADY = 0.5;

    public record Eta(Kind kind, long seconds) {
        public enum Kind { NONE, STEADY, EMPTY_IN, FULL_IN, IS_EMPTY, IS_FULL }
    }

    public static double storageFlow(List<PowerSample> fine) {
        if (fine.isEmpty()) return 0;
        int n = Math.min(ETA_WINDOW, fine.size());
        PowerSample avg = PowerSample.average(fine, fine.size() - n, n);
        return avg.in() - avg.out();
    }

    public static Eta eta(List<PowerSample> fine) {
        if (fine.isEmpty()) return new Eta(Eta.Kind.NONE, 0);
        PowerSample last = fine.get(fine.size() - 1);
        if (last.capacity() <= 0) return new Eta(Eta.Kind.NONE, 0);
        double flow = storageFlow(fine);
        if (flow < -STEADY) {
            if (last.stored() <= 0) return new Eta(Eta.Kind.IS_EMPTY, 0);
            return new Eta(Eta.Kind.EMPTY_IN, Math.round(last.stored() / (-flow) / 20.0));
        }
        if (flow > STEADY) {
            if (last.stored() >= last.capacity()) return new Eta(Eta.Kind.IS_FULL, 0);
            return new Eta(Eta.Kind.FULL_IN, Math.round((last.capacity() - last.stored()) / flow / 20.0));
        }
        return new Eta(last.stored() <= 0 ? Eta.Kind.IS_EMPTY : last.stored() >= last.capacity() ? Eta.Kind.IS_FULL : Eta.Kind.STEADY, 0);
    }

    public static float load(PowerSample s, PowerSummary sum) {
        return sum.peak() <= 0 ? 0f : Math.max(0f, Math.min(1f, s.prod() / (float) sum.peak()));
    }

    public static long max(List<PowerSample> list) {
        long m = 1;
        for (PowerSample s : list) m = Math.max(m, Math.max(s.prod(), s.cons()));
        return m;
    }

    public static String compact(long v) {
        long a = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (a >= 1_000_000_000L) return sign + one(a / 1_000_000_000.0) + "G";
        if (a >= 1_000_000L) return sign + one(a / 1_000_000.0) + "M";
        if (a >= 10_000L) return sign + one(a / 1_000.0) + "k";
        return Long.toString(v);
    }

    private static String one(double v) {
        return v >= 100 ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    public static String duration(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + " h " + String.format(java.util.Locale.ROOT, "%02d", (seconds % 3600) / 60) + " min";
        if (seconds >= 60) return (seconds / 60) + " min " + String.format(java.util.Locale.ROOT, "%02d", seconds % 60) + " s";
        return seconds + " s";
    }
}
