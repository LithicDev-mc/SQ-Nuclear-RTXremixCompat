package com.sqc.nuclearsq.oil;

import com.sqc.nuclearsq.NuclearConfig;

import javax.annotation.Nullable;

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
    public static final int LEGACY_REGION = 4;

    public record Plan(int chunkX, int chunkZ, int x, int z, OilClass oilClass, int max, int fill) {}

    public OilWellFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    public static int region() {
        return NuclearConfig.value(NuclearConfig.OIL_WELL_SPACING);
    }

    private static RandomSource random(long seed, int rx, int rz) {
        return RandomSource.create(seed + rx * 341873128712L + rz * 132897987541L + 0x0C1L);
    }

    public static ChunkPos target(long seed, int region, int rx, int rz) {
        int idx = random(seed, rx, rz).nextInt(region * region);
        return new ChunkPos(rx * region + idx % region, rz * region + idx / region);
    }

    public static ChunkPos keptLegacy(long seed, int region, int rx, int rz) {
        ChunkPos t = target(seed, region, rx, rz);
        int minX = rx * region, minZ = rz * region, maxX = minX + region - 1, maxZ = minZ + region - 1;
        ChunkPos best = null;
        long bestD = Long.MAX_VALUE;
        for (int lx = Math.floorDiv(minX, LEGACY_REGION); lx <= Math.floorDiv(maxX, LEGACY_REGION); lx++) {
            for (int lz = Math.floorDiv(minZ, LEGACY_REGION); lz <= Math.floorDiv(maxZ, LEGACY_REGION); lz++) {
                ChunkPos c = target(seed, LEGACY_REGION, lx, lz);
                if (c.x < minX || c.x > maxX || c.z < minZ || c.z > maxZ) continue;
                long dx = c.x - t.x, dz = c.z - t.z, d = dx * dx + dz * dz;
                if (best == null || d < bestD || d == bestD && (c.x < best.x || c.x == best.x && c.z < best.z)) {
                    best = c;
                    bestD = d;
                }
            }
        }
        return best;
    }

    public static boolean keepsLegacy(long seed, int region, ChunkPos cp) {
        ChunkPos k = keptLegacy(seed, region, Math.floorDiv(cp.x, region), Math.floorDiv(cp.z, region));
        return k != null && k.x == cp.x && k.z == cp.z;
    }

    @Nullable
    public static Plan plan(long seed, int region, ChunkPos cp) {
        int rx = Math.floorDiv(cp.x, region), rz = Math.floorDiv(cp.z, region);
        RandomSource r = random(seed, rx, rz);
        int idx = r.nextInt(region * region);
        if (cp.x != rx * region + idx % region || cp.z != rz * region + idx / region) return null;
        OilClass c = OilClass.roll(r);
        int max = (int) Math.round(c.base * (0.85 + 0.30 * r.nextDouble()));
        int fill = (int) Math.round(max * (0.75 + 0.25 * r.nextDouble()));
        int x = cp.getMinBlockX() + 4 + r.nextInt(8), z = cp.getMinBlockZ() + 4 + r.nextInt(8);
        return new Plan(cp.x, cp.z, x, z, c, max, fill);
    }

    static boolean ground(BlockState s) {
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
        Plan plan = plan(level.getSeed(), region(), new ChunkPos(ctx.origin()));
        if (plan == null) return false;
        int x = plan.x(), z = plan.z();
        int y = surface(level, x, z);
        if (y < 0) return false;
        int rad = plan.oilClass().radius;
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
        if (level.getBlockEntity(core) instanceof OilWellBlockEntity be) be.init(plan.oilClass(), plan.max(), plan.fill(), level.getLevel().getGameTime());
        return true;
    }
}
