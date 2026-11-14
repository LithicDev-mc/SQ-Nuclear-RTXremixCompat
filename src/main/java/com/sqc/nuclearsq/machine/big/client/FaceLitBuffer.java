package com.sqc.nuclearsq.machine.big.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.machine.big.FaceLight;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class FaceLitBuffer implements VertexConsumer {
    private static final float PIECE = 2f;
    private static final int MAX_CUTS = 16, BLOCKED = -1, UNKNOWN = Integer.MIN_VALUE, MEMO_TICKS = 10;
    private static final Long2IntOpenHashMap MEMO = new Long2IntOpenHashMap();
    private static Level memoLevel;
    private static long memoTime;

    static {
        MEMO.defaultReturnValue(UNKNOWN);
    }

    private VertexConsumer out;
    private final Level level;
    private final Vec3 cam;
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
    private final float[][] p = new float[4][5];
    private final int[] grid = new int[(MAX_CUTS + 1) * (MAX_CUTS + 1)];
    private float nx, ny, nz;
    private int col, ovl, base, n = -1;

    FaceLitBuffer(Level level, Vec3 cam) {
        this.level = level;
        this.cam = cam;
        long t = level.getGameTime() / MEMO_TICKS;
        if (level != memoLevel || t != memoTime) {
            MEMO.clear();
            memoLevel = level;
            memoTime = t;
        }
    }

    FaceLitBuffer to(VertexConsumer out) {
        this.out = out;
        n = -1;
        return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        n = (n + 1) % 4;
        p[n][0] = x;
        p[n][1] = y;
        p[n][2] = z;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        col = a << 24 | r << 16 | g << 8 | b;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        p[n][3] = u;
        p[n][4] = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        ovl = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        base = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        nx = x;
        ny = y;
        nz = z;
        if (n == 3) flush();
        return this;
    }

    private void flush() {
        int na = cuts(0, 1, 3, 2), nb = cuts(0, 3, 1, 2), w = na + 1;
        for (int j = 0; j <= nb; j++) {
            for (int i = 0; i <= na; i++) {
                float s = (i + (i == 0 ? 0.1f : i == na ? -0.1f : 0f)) / na, t = (j + (j == 0 ? 0.1f : j == nb ? -0.1f : 0f)) / nb;
                grid[j * w + i] = lightAt(cam.x + at(0, s, t), cam.y + at(1, s, t), cam.z + at(2, s, t));
            }
        }
        for (int j = 0; j < nb; j++) {
            for (int i = 0; i < na; i++) {
                int best = Math.max(Math.max(grid[j * w + i], grid[j * w + i + 1]), Math.max(grid[(j + 1) * w + i], grid[(j + 1) * w + i + 1]));
                if (best == BLOCKED) best = base;
                for (int k = 0; k < 4; k++) {
                    int gi = i + (k == 1 || k == 2 ? 1 : 0), gj = j + (k >> 1), l = grid[gj * w + gi];
                    if (l == BLOCKED) l = best;
                    float s = (float) gi / na, t = (float) gj / nb;
                    out.addVertex(at(0, s, t), at(1, s, t), at(2, s, t)).setColor(col >> 16 & 255, col >> 8 & 255, col & 255, col >>> 24)
                            .setUv(at(3, s, t), at(4, s, t)).setUv1(ovl & 0xFFFF, ovl >>> 16).setUv2(l & 0xFFFF, l >>> 16).setNormal(nx, ny, nz);
                }
            }
        }
    }

    private int cuts(int a, int b, int c, int d) {
        return Mth.clamp(Mth.ceil(Math.max(dist(a, b), dist(c, d)) / PIECE), 1, MAX_CUTS);
    }

    private float dist(int a, int b) {
        float x = p[a][0] - p[b][0], y = p[a][1] - p[b][1], z = p[a][2] - p[b][2];
        return Mth.sqrt(x * x + y * y + z * z);
    }

    private float at(int c, float s, float t) {
        return (1 - s) * (1 - t) * p[0][c] + s * (1 - t) * p[1][c] + s * t * p[2][c] + (1 - s) * t * p[3][c];
    }

    private int lightAt(double x, double y, double z) {
        for (int k = 0; k < FaceLight.STEPS; k++) {
            double d = 0.5 + k;
            long key = BlockPos.asLong(Mth.floor(x + nx * d), Mth.floor(y + ny * d), Mth.floor(z + nz * d));
            int l = MEMO.get(key);
            if (l == UNKNOWN) {
                mp.set(key);
                l = level.getBlockState(mp).getLightBlock(level, mp) < 15 ? LevelRenderer.getLightColor(level, mp) : BLOCKED;
                MEMO.put(key, l);
            }
            if (l != BLOCKED) return l;
        }
        return BLOCKED;
    }
}
