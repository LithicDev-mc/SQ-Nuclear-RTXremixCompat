package com.sqc.nuclearsq.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class PanelCull {
    private PanelCull() {}

    public static final double MAX_DIST = 64;

    public static boolean skip(Level level, BlockPos pos, AABB box) {
        if (level == null) return false;
        if (com.sqc.nuclearsq.NuclearClientConfig.cameraDistSq(pos) > MAX_DIST * MAX_DIST) return true;
        return !OcclusionCull.visible(level, pos, box);
    }
}
