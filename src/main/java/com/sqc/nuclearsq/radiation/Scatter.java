package com.sqc.nuclearsq.radiation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class Scatter {
    private Scatter() {}

    private static final int R = RadiationValues.SCATTER_RANGE;
    private static final int SIDE = 2 * R + 1;
    private static final int REBUILD_TICKS = 20;
    private static final int UNUSED_TICKS = 600;
    private static final int MAX_FIELDS = 256;
    private static final double CUTOFF = 1e-9;
    private static final double STEP_COST = 0.02;

    private record Key(ServerLevel level, long pos) {}

    private static final class Field {
        final float[] value = new float[SIDE * SIDE * SIDE];
        long built, used;
        boolean dirty;
    }

    private static final Map<Key, Field> FIELDS = new HashMap<>();

    public static double share(ServerLevel lv, BlockPos source, Vec3 at) {
        BlockPos t = BlockPos.containing(at);
        int dx = t.getX() - source.getX(), dy = t.getY() - source.getY(), dz = t.getZ() - source.getZ();
        if (Math.abs(dx) > R || Math.abs(dy) > R || Math.abs(dz) > R) return 0;
        long now = lv.getGameTime();
        Key k = new Key(lv, source.asLong());
        Field f = FIELDS.get(k);
        if (f == null || f.dirty && now - f.built >= REBUILD_TICKS) {
            if (f == null && FIELDS.size() >= MAX_FIELDS) prune(now);
            f = build(lv, source, f);
            f.built = now;
            f.dirty = false;
            FIELDS.put(k, f);
        }
        f.used = now;
        return f.value[index(dx, dy, dz)];
    }

    public static void markDirty(ServerLevel lv, BlockPos pos) {
        if (FIELDS.isEmpty()) return;
        for (var e : FIELDS.entrySet()) {
            if (e.getKey().level != lv) continue;
            long s = e.getKey().pos;
            if (Math.abs(BlockPos.getX(s) - pos.getX()) <= R && Math.abs(BlockPos.getY(s) - pos.getY()) <= R
                    && Math.abs(BlockPos.getZ(s) - pos.getZ()) <= R) e.getValue().dirty = true;
        }
    }

    private static void prune(long now) {
        FIELDS.values().removeIf(f -> now - f.used > UNUSED_TICKS);
        if (FIELDS.size() >= MAX_FIELDS) FIELDS.clear();
    }

    static void clear() {
        FIELDS.clear();
    }

    private static int index(int dx, int dy, int dz) {
        return ((dx + R) * SIDE + (dy + R)) * SIDE + (dz + R);
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static Field build(ServerLevel lv, BlockPos src, Field reuse) {
        Field f = reuse != null ? reuse : new Field();
        int size = f.value.length;
        Arrays.fill(f.value, 0f);
        double[] best = new double[size], logT = new double[size], lg = new double[size];
        Arrays.fill(best, Double.MAX_VALUE);
        Arrays.fill(lg, Double.NaN);
        double[] hc = new double[1024];
        int[] hi = new int[1024];
        int hn = 1, start = index(0, 0, 0);
        best[start] = 0;
        f.value[start] = 1f;
        hi[0] = start;
        double maxCost = -Math.log(CUTOFF);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int settled = 0;
        while (hn > 0 && settled < RadiationValues.SCATTER_MAX_NODES) {
            double c = hc[0], lc = hc[--hn];
            int idx = hi[0], li = hi[hn], k = 0;
            for (int ch; (ch = 2 * k + 1) < hn; k = ch) {
                if (ch + 1 < hn && hc[ch + 1] < hc[ch]) ch++;
                if (hc[ch] >= lc) break;
                hc[k] = hc[ch];
                hi[k] = hi[ch];
            }
            hc[k] = lc;
            hi[k] = li;
            if (c > best[idx]) continue;
            settled++;
            int x = idx / (SIDE * SIDE) - R, y = idx / SIDE % SIDE - R, z = idx % SIDE - R;
            for (int[] d : DIRS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (Math.abs(nx) > R || Math.abs(ny) > R || Math.abs(nz) > R) continue;
                int ni = index(nx, ny, nz);
                if (ni == start) continue;
                double l = lg[ni];
                if (l != l) {
                    p.set(src.getX() + nx, src.getY() + ny, src.getZ() + nz);
                    lg[ni] = l = lv.isLoaded(p) ? Math.log(Shielding.transmission(lv.getBlockState(p))) : Double.POSITIVE_INFINITY;
                }
                if (l > 0) continue;
                double nl = logT[idx] + l;
                double cost = c + (logT[idx] - nl) + STEP_COST;
                if (cost >= best[ni] || cost > maxCost) continue;
                best[ni] = cost;
                logT[ni] = nl;
                f.value[ni] = (float) (Math.exp(-cost) / Math.max(1.0, nx * nx + ny * ny + nz * nz));
                if (hn == hc.length) {
                    hc = Arrays.copyOf(hc, hn * 2);
                    hi = Arrays.copyOf(hi, hn * 2);
                }
                int j = hn++;
                for (int up; j > 0 && hc[up = (j - 1) >> 1] > cost; j = up) {
                    hc[j] = hc[up];
                    hi[j] = hi[up];
                }
                hc[j] = cost;
                hi[j] = ni;
            }
        }
        return f;
    }
}
