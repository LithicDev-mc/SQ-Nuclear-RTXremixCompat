package com.sqc.nuclearsq.oil;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class OilWellFeature extends Feature<NoneFeatureConfiguration> {
    public static final int REGION = 4;

    public OilWellFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    private static boolean ground(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.GRAVEL);
    }

    private static int surface(WorldGenLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        if (level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1 != y) return -1;
        return ground(level.getBlockState(new BlockPos(x, y, z))) ? y : -1;
    }

    public static void dropStainEntity(ChunkAccess chunk, BlockPos pos) {
        BlockState s = chunk.getBlockState(pos);
        if (s.is(OilRegistry.WELL.get()) && !s.getValue(OilWellBlock.CORE)) chunk.removeBlockEntity(pos);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        ChunkPos cp = new ChunkPos(ctx.origin());
        int rx = Math.floorDiv(cp.x, REGION), rz = Math.floorDiv(cp.z, REGION);
        RandomSource r = RandomSource.create(level.getSeed() + rx * 341873128712L + rz * 132897987541L + 0x0C1L);
        int idx = r.nextInt(REGION * REGION);
        if (cp.x != rx * REGION + idx % REGION || cp.z != rz * REGION + idx / REGION) return false;
        OilClass c = OilClass.roll(r);
        int max = (int) Math.round(c.base * (0.85 + 0.30 * r.nextDouble()));
        int fill = (int) Math.round(max * (0.75 + 0.25 * r.nextDouble()));
        int x = cp.getMinBlockX() + 4 + r.nextInt(8), z = cp.getMinBlockZ() + 4 + r.nextInt(8);
        int y = surface(level, x, z);
        if (y < 0) return false;
        int rad = c.radius;
        BlockState stain = OilRegistry.WELL.get().defaultBlockState().setValue(OilWellBlock.CORE, false);
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad + rad) continue;
                int sy = surface(level, x + dx, z + dz);
                if (sy < 0) continue;
                BlockPos p = new BlockPos(x + dx, sy, z + dz);
                level.setBlock(p, stain, 2);
                dropStainEntity(level.getChunk(p), p);
            }
        }
        BlockPos core = new BlockPos(x, y, z);
        level.setBlock(core, OilRegistry.WELL.get().defaultBlockState(), 2);
        if (level.getBlockEntity(core) instanceof OilWellBlockEntity be) be.init(c, max, fill, level.getLevel().getGameTime());
        return true;
    }
}
