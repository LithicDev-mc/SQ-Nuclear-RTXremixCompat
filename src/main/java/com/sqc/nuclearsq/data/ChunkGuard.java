package com.sqc.nuclearsq.data;

import java.util.Collection;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class ChunkGuard {
    private ChunkGuard() {}

    private static volatile Predicate<BlockPos> hidden;

    public static void hide(Predicate<BlockPos> unloaded) {
        hidden = unloaded;
    }

    public static void clear() {
        hidden = null;
    }

    public static boolean loaded(Level level, BlockPos pos) {
        Predicate<BlockPos> h = hidden;
        if (h != null && h.test(pos)) return false;
        return level.isLoaded(pos);
    }

    public static boolean allLoaded(Level level, Collection<BlockPos> positions) {
        for (BlockPos p : positions) if (!loaded(level, p)) return false;
        return true;
    }
}
