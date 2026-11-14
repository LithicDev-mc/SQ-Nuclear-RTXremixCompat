package com.sqc.nuclearsq.explosion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class BlastShield {
    private BlastShield() {}

    private static final Map<String, Long2BooleanOpenHashMap> CACHE = new HashMap<>();

    public static boolean blocked(ServerLevel lv, BlockPos center, int cx, int cz) {
        Long2BooleanOpenHashMap m = CACHE.computeIfAbsent(lv.dimension().location() + "|" + center.asLong(), k -> new Long2BooleanOpenHashMap());
        long key = ChunkPos.asLong(cx, cz);
        if (m.containsKey(key)) return m.get(key);
        BlockPos p = new BlockPos((cx << 4) + 8, Mth.clamp(center.getY(), lv.getMinBuildHeight(), lv.getMaxBuildHeight() - 1), (cz << 4) + 8);
        Explosion ex = new Explosion(lv, null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 16f, false, Explosion.BlockInteraction.DESTROY);
        ex.getToBlow().add(p);
        NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(lv, ex, new ArrayList<>()));
        boolean blocked = !ex.getToBlow().contains(p);
        m.put(key, blocked);
        return blocked;
    }

    static void clear() {
        CACHE.clear();
    }
}
