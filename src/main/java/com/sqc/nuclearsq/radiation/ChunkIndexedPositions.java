package com.sqc.nuclearsq.radiation;

import java.util.function.LongConsumer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class ChunkIndexedPositions {
    private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();
    private int size;

    public static long chunkOf(long packed) {
        return ChunkPos.asLong(BlockPos.getX(packed) >> 4, BlockPos.getZ(packed) >> 4);
    }

    public boolean add(long p) {
        LongOpenHashSet in = byChunk.get(chunkOf(p));
        if (in == null) byChunk.put(chunkOf(p), in = new LongOpenHashSet());
        if (!in.add(p)) return false;
        size++;
        return true;
    }

    public boolean remove(long p) {
        long c = chunkOf(p);
        LongOpenHashSet in = byChunk.get(c);
        if (in == null || !in.remove(p)) return false;
        if (in.isEmpty()) byChunk.remove(c);
        size--;
        return true;
    }

    public boolean contains(long p) {
        LongOpenHashSet in = byChunk.get(chunkOf(p));
        return in != null && in.contains(p);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        byChunk.clear();
        size = 0;
    }

    public LongSet chunks() {
        return byChunk.keySet();
    }

    public long[] toLongArray() {
        long[] out = new long[size];
        int i = 0;
        for (LongOpenHashSet in : byChunk.values()) {
            LongIterator it = in.iterator();
            while (it.hasNext()) out[i++] = it.nextLong();
        }
        return out;
    }

    public void forNear(Vec3 p, double reach, LongConsumer action) {
        if (byChunk.isEmpty()) return;
        int x0 = ((int) Math.floor(p.x - reach)) >> 4, x1 = ((int) Math.floor(p.x + reach)) >> 4;
        int z0 = ((int) Math.floor(p.z - reach)) >> 4, z1 = ((int) Math.floor(p.z + reach)) >> 4;
        long span = (long) (x1 - x0 + 1) * (z1 - z0 + 1);
        if (span <= byChunk.size()) {
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    LongOpenHashSet in = byChunk.get(ChunkPos.asLong(cx, cz));
                    if (in == null) continue;
                    LongIterator it = in.iterator();
                    while (it.hasNext()) action.accept(it.nextLong());
                }
            }
            return;
        }
        for (var e : byChunk.long2ObjectEntrySet()) {
            long c = e.getLongKey();
            int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
            if (cx < x0 || cx > x1 || cz < z0 || cz > z1) continue;
            LongIterator it = e.getValue().iterator();
            while (it.hasNext()) action.accept(it.nextLong());
        }
    }
}
