package com.sqc.nuclearsq.reactor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RecombinerBlockEntity extends BlockEntity {
    private static final Map<ResourceKey<Level>, Set<Long>> ACTIVE = new ConcurrentHashMap<>();

    public RecombinerBlockEntity(BlockPos pos, BlockState state) {
        super(ReactorRegistry.RECOMBINER_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register(level, worldPosition);
    }

    public static void register(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) ACTIVE.computeIfAbsent(sl.dimension(), k -> ConcurrentHashMap.newKeySet()).add(pos.asLong());
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) {
            Set<Long> set = ACTIVE.get(sl.dimension());
            if (set != null) set.remove(worldPosition.asLong());
        }
        super.setRemoved();
    }

    public static int count(ServerLevel level, BlockPos center, int range) {
        Set<Long> set = ACTIVE.get(level.dimension());
        if (set == null) return 0;
        int n = 0;
        for (long l : set) {
            BlockPos p = BlockPos.of(l);
            if (Math.abs(p.getX() - center.getX()) <= range && Math.abs(p.getY() - center.getY()) <= range
                    && Math.abs(p.getZ() - center.getZ()) <= range) n++;
        }
        return n;
    }
}
