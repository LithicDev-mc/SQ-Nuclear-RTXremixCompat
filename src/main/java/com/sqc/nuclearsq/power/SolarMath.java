package com.sqc.nuclearsq.power;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class SolarMath {
    private SolarMath() {}

    public static final int SEA = 64, TOP = 192;
    public static final float RAIN_LOSS = 0.6f, THUNDER_LOSS = 0.5f, HEIGHT_BONUS = 0.25f;

    public static float daylight(Level level) {
        float t = level.getTimeOfDay(1f);
        float c = Mth.cos(t * Mth.TWO_PI);
        return c <= 0 ? 0f : Mth.sqrt(c);
    }

    public static final float TRACK_MAX = Mth.PI / 4f;

    public static float trackedDaylight(Level level) {
        float a = level.getTimeOfDay(1f) * Mth.TWO_PI;
        if (a > Mth.PI) a -= Mth.TWO_PI;
        float off = Math.abs(a);
        if (off >= Mth.HALF_PI) return 0f;
        if (off <= TRACK_MAX) return 1f;
        return Mth.sqrt(Mth.cos((off - TRACK_MAX) * Mth.HALF_PI / (Mth.HALF_PI - TRACK_MAX)));
    }

    public static float track(Level level, float partialTick) {
        if (!level.dimensionType().hasSkyLight()) return 0f;
        float a = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        if (a > Mth.PI) a -= Mth.TWO_PI;
        if (Math.abs(a) <= Mth.HALF_PI) return Mth.clamp(a, -TRACK_MAX, TRACK_MAX);
        return Math.signum(a) * TRACK_MAX * (Mth.PI - Math.abs(a)) / Mth.HALF_PI;
    }

    public static float weather(Level level) {
        return (1f - RAIN_LOSS * level.getRainLevel(1f)) * (1f - THUNDER_LOSS * level.getThunderLevel(1f));
    }

    public static float height(int y) {
        return 1f + HEIGHT_BONUS * Mth.clamp((y - SEA) / (float) (TOP - SEA), 0f, 1f);
    }

    public static float factor(Level level, BlockPos sky) {
        return factor(level, sky, false);
    }

    public static float factor(Level level, BlockPos sky, boolean tracked) {
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling() || !level.canSeeSky(sky)) return 0f;
        return (tracked ? trackedDaylight(level) : daylight(level)) * weather(level) * height(sky.getY());
    }
}
