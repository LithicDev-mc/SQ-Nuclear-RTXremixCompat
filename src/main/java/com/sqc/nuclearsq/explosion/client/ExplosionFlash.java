package com.sqc.nuclearsq.explosion.client;

public final class ExplosionFlash {
    private ExplosionFlash() {}

    public static final int IMPACT_HOLD = 20;
    public static final int NEAR_HOLD = 40;
    private static final double NEAR = 0.15;

    public static int total = 1;
    public static int ticksLeft = 0;
    public static int hold = 0;

    public static void start(int ticks, double distance, double range, double look) {
        double k = Math.max(0.0, Math.min(1.0, 1.0 - distance / Math.max(1.0, range)));
        double near = Math.max(0.0, Math.min(1.0, (k - 0.4) / (1.0 - NEAR - 0.4)));
        double seen = 0.6 + 0.4 * Math.max(0.0, look);
        int full = Math.max(1, ticks);
        int h = Math.max(IMPACT_HOLD, (int) Math.round(NEAR_HOLD * near * near));
        int fade = (int) Math.round(Math.max(1, full - NEAR_HOLD) * (0.5 + 0.5 * k) * (seen + (1.0 - seen) * near));
        if (ticksLeft > 0 && ticksLeft > h + fade) return;
        hold = h;
        total = Math.max(1, h + fade);
        ticksLeft = total;
    }

    public static float alpha(float t) {
        float fade = Math.max(1, total - hold);
        return Math.max(0f, Math.min(1f, t / fade));
    }
}
