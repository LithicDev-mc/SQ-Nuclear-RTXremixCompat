package com.sqc.nuclearsq.oil;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class OilWellCleanup {
    private OilWellCleanup() {}

    public static final int SCAN_DEPTH = 24;
    private static final int PER_TICK = 16;
    private static final Queue<Long> PENDING = new ConcurrentLinkedQueue<>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!NuclearConfig.value(NuclearConfig.OIL_REMOVE_OLD_WELLS) || OilWellFeature.region() == OilWellFeature.LEGACY_REGION) return;
        ChunkPos cp = event.getChunk().getPos();
        if (OilWellFeature.plan(level.getSeed(), OilWellFeature.LEGACY_REGION, cp) != null) PENDING.add(cp.toLong());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        for (int i = 0; i < PER_TICK; i++) {
            Long key = PENDING.poll();
            if (key == null) return;
            ChunkPos cp = new ChunkPos(key);
            if (level.hasChunk(cp.x, cp.z)) clean(level, cp);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    public static int clean(ServerLevel level, ChunkPos cp) {
        int region = OilWellFeature.region();
        if (region == OilWellFeature.LEGACY_REGION) return 0;
        OilWellFeature.Plan old = OilWellFeature.plan(level.getSeed(), OilWellFeature.LEGACY_REGION, cp);
        if (old == null || OilWellFeature.keepsLegacy(level.getSeed(), region, cp)) return 0;
        int rad = old.oilClass().radius;
        List<BlockPos> found = new ArrayList<>();
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad + rad) continue;
                BlockPos p = find(level, old.x() + dx, old.z() + dz);
                if (p != null) found.add(p);
            }
        }
        if (found.isEmpty()) return 0;
        for (BlockPos p : found) {
            Block above = level.getBlockState(p.above()).getBlock();
            if (above != OilRegistry.WELL.get() && BuiltInRegistries.BLOCK.getKey(above).getNamespace().equals(NuclearSQ.MODID)) return 0;
        }
        BlockState around = surrounding(level, old.x(), old.z(), rad);
        for (BlockPos p : found) level.setBlock(p, around != null ? around : below(level.getBlockState(p.below())), Block.UPDATE_ALL);
        return found.size();
    }

    @Nullable
    private static BlockPos find(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int bottom = Math.max(level.getMinBuildHeight(), top - SCAN_DEPTH);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y >= bottom; y--) {
            p.setY(y);
            if (level.getBlockState(p).is(OilRegistry.WELL.get())) return p.immutable();
        }
        return null;
    }

    @Nullable
    private static BlockState surrounding(ServerLevel level, int x, int z, int rad) {
        Map<BlockState, Integer> count = new HashMap<>();
        int inner = rad * rad + rad, outer = (rad + 3) * (rad + 3);
        for (int dx = -rad - 3; dx <= rad + 3; dx++) {
            for (int dz = -rad - 3; dz <= rad + 3; dz++) {
                int d = dx * dx + dz * dz;
                if (d <= inner || d > outer || !level.hasChunk((x + dx) >> 4, (z + dz) >> 4)) continue;
                int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x + dx, z + dz) - 1;
                BlockState s = level.getBlockState(new BlockPos(x + dx, y, z + dz));
                if (OilWellFeature.ground(s) && !s.is(OilRegistry.WELL.get()) && !s.hasBlockEntity()) count.merge(s.getBlock().defaultBlockState(), 1, Integer::sum);
            }
        }
        BlockState best = null;
        int n = 0;
        for (var e : count.entrySet()) {
            if (e.getValue() > n) {
                best = e.getKey();
                n = e.getValue();
            }
        }
        return best;
    }

    private static BlockState below(BlockState s) {
        if (s.is(BlockTags.DIRT)) return Blocks.GRASS_BLOCK.defaultBlockState();
        if (s.is(Blocks.SANDSTONE)) return Blocks.SAND.defaultBlockState();
        if (s.is(Blocks.RED_SANDSTONE)) return Blocks.RED_SAND.defaultBlockState();
        if (OilWellFeature.ground(s) && !s.is(OilRegistry.WELL.get())) return s.getBlock().defaultBlockState();
        return Blocks.DIRT.defaultBlockState();
    }
}
