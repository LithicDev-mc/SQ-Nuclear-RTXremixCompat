package com.sqc.nuclearsq.machine.big;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

public final class FaceLight {
    public static final int STEPS = 4;

    private FaceLight() {}

    public static BlockPos sample(BlockGetter level, double cx, double cy, double cz, float nx, float ny, float nz) {
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-4) return null;
        double ux = nx / l, uy = ny / l, uz = nz / l;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int k = 0; k < STEPS; k++) {
            double d = 0.5 + k;
            mp.set(Math.floor(cx + ux * d), Math.floor(cy + uy * d), Math.floor(cz + uz * d));
            if (level.getBlockState(mp).getLightBlock(level, mp) < 15) return mp.immutable();
        }
        return null;
    }
}
