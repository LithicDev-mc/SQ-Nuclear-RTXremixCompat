package com.sqc.nuclearsq.data;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public record DataValue(String key, Component label, double value, double max, String unit) {
    public float frac() {
        return max > 0 ? Mth.clamp((float) (value / max), 0f, 1f) : 0f;
    }

    public String text() {
        return number(value) + (unit.isEmpty() ? "" : " " + unit);
    }

    public static String number(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) return Long.toString(Math.round(v));
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
