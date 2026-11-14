package com.sqc.nuclearsq.oil;

import net.minecraft.util.RandomSource;

public enum OilClass {
    SMALL("small", 2_000_000, 2, 0.45),
    MEDIUM("medium", 6_000_000, 3, 0.30),
    LARGE("large", 12_000_000, 3, 0.18),
    HUGE("huge", 24_000_000, 4, 0.07);

    public final String id;
    public final int base;
    public final int radius;
    private final double share;

    OilClass(String id, int base, int radius, double share) {
        this.id = id;
        this.base = base;
        this.radius = radius;
        this.share = share;
    }

    public static OilClass roll(RandomSource r) {
        double d = r.nextDouble();
        for (OilClass c : values()) {
            d -= c.share;
            if (d < 0) return c;
        }
        return HUGE;
    }
}
