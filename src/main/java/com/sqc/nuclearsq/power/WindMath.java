package com.sqc.nuclearsq.power;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class WindMath {
    private WindMath() {}

    public static final int LOW = 40, HIGH = 160;
    public static final float MIN_HEIGHT = 0.30f, MIN_OPEN = 0.20f;
    public static final float CUT_IN = 0.04f;

    public static float height(int hubY) {
        return MIN_HEIGHT + (1f - MIN_HEIGHT) * Mth.clamp((hubY - LOW) / (float) (HIGH - LOW), 0f, 1f);
    }

    public static float gust(long gameTime) {
        double t = gameTime;
        double g = 0.5 + 0.30 * Math.sin(t * Math.PI * 2 / 2400.0) + 0.14 * Math.sin(t * Math.PI * 2 / 800.0 + 1.3)
                + 0.06 * Math.sin(t * Math.PI * 2 / 9600.0 + 0.4);
        return 0.2f + 0.8f * Mth.clamp((float) g, 0f, 1f);
    }

    public static float wind(Level level, long gameTime) {
        float w = gust(gameTime) + 0.2f * level.getRainLevel(1f) + 0.3f * level.getThunderLevel(1f);
        return Math.min(1f, w);
    }

    public static boolean hasWind(Level level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
    }

    private static final int[][] DIRS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    public static float openness(Level level, BlockPos hub, int range, int skip) {
        if (range <= skip) return 1f;
        float sum = 0;
        int rays = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy : new int[] {0, -3}) {
            for (int[] d : DIRS) {
                int free = 0;
                for (int i = skip; i <= range; i++) {
                    p.set(hub.getX() + d[0] * i, hub.getY() + dy, hub.getZ() + d[1] * i);
                    if (!level.isLoaded(p)) {
                        free = range - skip + 1;
                        break;
                    }
                    BlockState st = level.getBlockState(p);
                    if (!st.isAir() && !st.getCollisionShape(level, p).isEmpty()) break;
                    free++;
                }
                sum += free / (float) (range - skip + 1);
                rays++;
            }
        }
        return sum / rays;
    }

    public static float surround(float open) {
        return MIN_OPEN + (1f - MIN_OPEN) * Mth.clamp(open, 0f, 1f);
    }

    public static boolean covered(Level level, BlockPos top) {
        return !level.canSeeSky(top.above());
    }

    public static int rate(int peak, float height, float surround, float wind) {
        float f = height * surround * wind;
        return f < CUT_IN ? 0 : Math.round(peak * Math.min(1f, f));
    }
}
