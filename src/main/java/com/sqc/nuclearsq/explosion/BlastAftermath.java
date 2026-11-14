package com.sqc.nuclearsq.explosion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

public final class BlastAftermath {
    private BlastAftermath() {}

    private static final Predicate<BlockState> FRAGILE = st -> st.is(Tags.Blocks.GLASS_BLOCKS) || st.is(Tags.Blocks.GLASS_PANES)
            || st.is(Blocks.LANTERN) || st.is(Blocks.SOUL_LANTERN) || st.is(BlockTags.LEAVES);
    private static final float SHATTER_NEAR = 0.95f, SHATTER_FAR = 0.15f, LEAVES_SHARE = 0.7f;
    private static final int SHATTER_DOWN = 16, SHATTER_UP = 48;
    private static final int EFFECTS_PER_SECTION = 6;
    static final long SHATTER_BUDGET_NS = 3_000_000L;
    private static final int SHATTER_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
    private static final int LEAF_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    static final class ShatterJob implements BlastJob {
        static final MapCodec<ShatterJob> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.fieldOf("center").forGetter(j -> j.center),
                Codec.INT.fieldOf("radius").forGetter(j -> j.radius),
                Codec.INT.fieldOf("range").forGetter(j -> j.range),
                Codec.INT.fieldOf("index").forGetter(j -> j.index),
                Codec.INT.fieldOf("age").forGetter(j -> j.age)
        ).apply(i, ShatterJob::new));
        static final Type<ShatterJob> TYPE = new Type<>("shatter", CODEC);

        final BlockPos center;
        final int radius, range;
        final long[][] sections;
        int index, age;

        ShatterJob(BlockPos center, int radius, int range, int index, int age) {
            this.center = center;
            this.radius = radius;
            this.range = range;
            this.sections = ringSections(center, radius, range);
            this.index = Math.min(index, sections.length);
            this.age = age;
        }

        @Override
        public Type<?> type() { return TYPE; }

        @Override
        public boolean tick(ServerLevel level, BlastJobs.Budget budget) {
            if (budget.shatterStopped) return false;
            tickShatter(level, this, budget);
            return index >= sections.length;
        }
    }

    private static long[][] ringSections(BlockPos c, int r, int range) {
        List<long[]> out = new ArrayList<>();
        long r2 = (long) range * range;
        for (int sx = (c.getX() - range) >> 4; sx <= (c.getX() + range) >> 4; sx++)
            for (int sz = (c.getZ() - range) >> 4; sz <= (c.getZ() + range) >> 4; sz++) {
                long nx = gap(c.getX(), sx), nz = gap(c.getZ(), sz);
                long near2 = nx * nx + nz * nz;
                if (near2 > r2) continue;
                long fx = Math.max(Math.abs((sx << 4) - c.getX()), Math.abs((sx << 4) + 15 - c.getX()));
                long fz = Math.max(Math.abs((sz << 4) - c.getZ()), Math.abs((sz << 4) + 15 - c.getZ()));
                if (fx * fx + fz * fz < (long) r * r) continue;
                for (int sy = (c.getY() - SHATTER_DOWN) >> 4; sy <= (c.getY() + SHATTER_UP) >> 4; sy++)
                    out.add(new long[]{SectionPos.asLong(sx, sy, sz), (long) Math.sqrt(near2)});
            }
        out.sort(Comparator.comparingLong(a -> a[1]));
        return out.toArray(long[][]::new);
    }

    private static long gap(int v, int s) {
        int lo = s << 4, hi = lo + 15;
        return v < lo ? lo - v : (v > hi ? v - hi : 0);
    }

    private static final Predicate<BlockState> LOOSE = st -> st.is(BlockTags.MINEABLE_WITH_AXE) || st.is(BlockTags.LEAVES)
            || st.is(BlockTags.WOOL) || st.is(BlockTags.WOOL_CARPETS) || st.is(BlockTags.STAIRS) || st.is(BlockTags.SLABS)
            || st.is(BlockTags.WALLS) || st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS) || st.is(BlockTags.STONE_BRICKS)
            || st.is(Tags.Blocks.COBBLESTONES) || st.is(Tags.Blocks.GLASS_BLOCKS) || st.is(Tags.Blocks.GLASS_PANES)
            || st.is(Tags.Blocks.CONCRETES) || st.is(Blocks.BRICKS) || st.is(Blocks.SNOW);
    static final double FLATTEN_REACH = 1.35;
    private static final int FLATTEN_DEPTH = 64;
    private static final int FLATTEN_STUMP = 10;
    static final long FLATTEN_BUDGET_NS = 6_000_000L;
    private static final int FLATTEN_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE;

    static final class FlattenJob implements BlastJob {
        static final MapCodec<FlattenJob> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.fieldOf("center").forGetter(j -> j.center),
                Codec.INT.fieldOf("radius").forGetter(j -> j.radius),
                Codec.INT.fieldOf("range").forGetter(j -> j.range),
                Codec.INT.fieldOf("index").forGetter(j -> j.index),
                Codec.INT.fieldOf("age").forGetter(j -> j.age)
        ).apply(i, FlattenJob::new));
        static final Type<FlattenJob> TYPE = new Type<>("flatten", CODEC);

        final BlockPos center;
        final int radius, range;
        final long[][] chunks;
        int index, age;

        FlattenJob(BlockPos center, int radius, int range, int index, int age) {
            this.center = center;
            this.radius = radius;
            this.range = range;
            this.chunks = ringChunks(center, radius, range);
            this.index = Math.min(index, chunks.length);
            this.age = age;
        }

        @Override
        public Type<?> type() { return TYPE; }

        @Override
        public boolean tick(ServerLevel level, BlastJobs.Budget budget) {
            if (budget.flattenStopped) return false;
            tickFlatten(level, this, budget);
            return index >= chunks.length;
        }
    }

    private static long[][] ringChunks(BlockPos c, int r, int range) {
        List<long[]> out = new ArrayList<>();
        for (int sx = (c.getX() - range) >> 4; sx <= (c.getX() + range) >> 4; sx++)
            for (int sz = (c.getZ() - range) >> 4; sz <= (c.getZ() + range) >> 4; sz++) {
                long nx = gap(c.getX(), sx), nz = gap(c.getZ(), sz);
                if (nx * nx + nz * nz <= (long) range * range)
                    out.add(new long[]{ChunkPos.asLong(sx, sz), (long) Math.sqrt(nx * nx + nz * nz)});
            }
        out.sort(Comparator.comparingLong(a -> a[1]));
        return out.toArray(long[][]::new);
    }

    private static void tickFlatten(ServerLevel lv, FlattenJob job, BlastJobs.Budget budget) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long deadline = budget.flattenDeadline();
        job.age++;
        double front = job.age * NuclearExplosion.SHOCK_SPEED;
        RandomSource rnd = lv.random;
        int cx = job.center.getX(), cz = job.center.getZ();
        int[] found = new int[FLATTEN_DEPTH + 1];
        while (job.index < job.chunks.length && job.chunks[job.index][1] <= front) {
            if (System.nanoTime() > deadline) {
                budget.flattenStopped = true;
                return;
            }
            long cp = job.chunks[job.index++][0];
            int sx = ChunkPos.getX(cp), sz = ChunkPos.getZ(cp);
            if (!lv.hasChunk(sx, sz) || BlastShield.blocked(lv, job.center, sx, sz)) continue;
            LevelChunk chunk = lv.getChunk(sx, sz);
            int effects = 0;
            for (int i = 0; i < 256; i++) {
                int x = (sx << 4) + (i & 15), z = (sz << 4) + (i >> 4);
                double d = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
                if (d <= job.radius || d > job.range) continue;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int bottom = Math.max(lv.getMinBuildHeight(), top - FLATTEN_DEPTH), n = 0, y = top;
                for (; y >= bottom; y--) {
                    BlockState st = chunk.getBlockState(p.set(x, y, z));
                    if (st.isAir()) continue;
                    if (!LOOSE.test(st) && !st.getCollisionShape(lv, p).isEmpty()) break;
                    if (st.getFluidState().isEmpty() && st.getDestroySpeed(lv, p) >= 0
                            && !BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace().equals(com.sqc.nuclearsq.NuclearSQ.MODID))
                        found[n++] = y;
                }
                double f = (d - job.radius) / Math.max(1, job.range - job.radius);
                int keep = y + (f < 0.5 ? 0 : rnd.nextInt(1 + (int) ((f - 0.5) * 2 * FLATTEN_STUMP)));
                for (int k = 0; k < n && found[k] > keep; k++) {
                    BlockState st = chunk.getBlockState(p.set(x, found[k], z));
                    if (effects < EFFECTS_PER_SECTION && rnd.nextInt(8) == 0 && !st.getCollisionShape(lv, p).isEmpty()) {
                        lv.levelEvent(2001, p, Block.getId(st));
                        effects++;
                    }
                    if (st.hasBlockEntity()) Clearable.tryClear(lv.getBlockEntity(p));
                    lv.setBlock(p, Blocks.AIR.defaultBlockState(), FLATTEN_FLAGS);
                }
            }
        }
    }

    private static final float[] GLOW_CORE = {0.07f, 0.07f, 0.03f, 0.01f};
    private static final BlockState[] GLOW_BLOCKS = {
            Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
            Blocks.BASALT.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
    };
    public static boolean glowEveryColumnForTests = false;
    private static final int GLOW_COLUMNS_PER_TICK = 800;
    static final long GLOW_BUDGET_NS = 2_000_000L;
    static final int SMOKE_TICKS = 1800;
    private static final float SMOKE_RATE = 0.8f;

    static final class GlowJob implements BlastJob {
        static final MapCodec<GlowJob> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.fieldOf("center").forGetter(j -> j.center),
                Codec.INT.fieldOf("radius").forGetter(j -> j.radius),
                Codec.INT.fieldOf("index").forGetter(j -> j.index),
                Codec.INT.fieldOf("age").forGetter(j -> j.age)
        ).apply(i, GlowJob::new));
        static final Type<GlowJob> TYPE = new Type<>("glow", CODEC);

        final BlockPos center;
        final int radius, seed, reach, side;
        int index, age;

        GlowJob(BlockPos center, int radius, int index, int age) {
            this.center = center;
            this.radius = radius;
            this.seed = NuclearExplosion.craterSeed(center);
            this.reach = (int) Math.ceil(radius * NuclearExplosion.RIM_WIDTH * 1.12) + 1;
            this.side = 2 * reach + 1;
            this.index = index;
            this.age = age;
        }

        boolean converted() { return index >= side * side; }

        @Override
        public Type<?> type() { return TYPE; }

        @Override
        public boolean changesBlocks() { return !converted(); }

        @Override
        public boolean tick(ServerLevel level, BlastJobs.Budget budget) {
            if (!converted()) {
                convert(level, this, budget.glowDeadline());
                return false;
            }
            if (age++ < SMOKE_TICKS) {
                smoke(level, this);
                return false;
            }
            return true;
        }
    }

    static void shatter(ServerLevel level, Vec3 c, int radius, int range) {
        if (range <= radius) return;
        BlastJobs.add(level, new ShatterJob(BlockPos.containing(c), radius, range, 0, 0));
    }

    static void flatten(ServerLevel level, Vec3 c, int radius, int range) {
        if (range <= radius) return;
        BlastJobs.add(level, new FlattenJob(BlockPos.containing(c), radius, range, 0, 0));
    }

    static void glow(ServerLevel level, BlockPos center, int radius) {
        BlastJobs.add(level, new GlowJob(center, radius, 0, 0));
    }

    private static void tickShatter(ServerLevel lv, ShatterJob job, BlastJobs.Budget budget) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long deadline = budget.shatterDeadline();
        job.age++;
        double front = job.age * NuclearExplosion.SHOCK_SPEED;
        RandomSource rnd = lv.random;
        int cx = job.center.getX(), cz = job.center.getZ();
        while (job.index < job.sections.length && job.sections[job.index][1] <= front) {
            if (System.nanoTime() > deadline) {
                budget.shatterStopped = true;
                return;
            }
            long sp = job.sections[job.index++][0];
            int sx = SectionPos.x(sp), sy = SectionPos.y(sp), sz = SectionPos.z(sp);
            if (sy < lv.getMinSection() || sy >= lv.getMaxSection() || !lv.hasChunk(sx, sz) || BlastShield.blocked(lv, job.center, sx, sz)) continue;
            LevelChunk chunk = lv.getChunk(sx, sz);
            LevelChunkSection sec = chunk.getSection(lv.getSectionIndexFromSectionY(sy));
            if (sec.hasOnlyAir() || !sec.maybeHas(FRAGILE)) continue;
            int effects = 0;
            for (int i = 0; i < 4096; i++) {
                int lx = i & 15, lz = (i >> 4) & 15, ly = i >> 8;
                BlockState st = sec.getBlockState(lx, ly, lz);
                if (st.isAir() || !FRAGILE.test(st)) continue;
                int x = (sx << 4) + lx, z = (sz << 4) + lz;
                double d = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
                if (d > job.range) continue;
                double f = Math.max(0.0, (d - job.radius) / (job.range - job.radius));
                boolean leaves = st.is(BlockTags.LEAVES);
                float chance = (float) (SHATTER_NEAR + (SHATTER_FAR - SHATTER_NEAR) * f) * (leaves ? LEAVES_SHARE : 1f);
                if (rnd.nextFloat() >= chance) continue;
                p.set(x, (sy << 4) + ly, z);
                if (effects < EFFECTS_PER_SECTION) {
                    lv.levelEvent(2001, p, Block.getId(st));
                    effects++;
                }
                lv.setBlock(p, Blocks.AIR.defaultBlockState(), leaves ? LEAF_FLAGS : SHATTER_FLAGS);
            }
            if (effects > 0) lv.playSound(null, p, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.5f, 0.8f + rnd.nextFloat() * 0.4f);
        }
    }

    private static BlockPos craterFloor(ServerLevel lv, GlowJob job, int x, int z) {
        if (!lv.hasChunk(x >> 4, z >> 4) || BlastShield.blocked(lv, job.center, x >> 4, z >> 4)) return null;
        int cy = job.center.getY(), bottom = Math.max(lv.getMinBuildHeight(), cy - job.radius - 2);
        int top = Math.min(lv.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1,
                cy + (int) Math.ceil(job.radius * NuclearExplosion.RIM_DEPTH * 1.25) + 1);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        while (top >= bottom && lv.getBlockState(p.setY(top)).isAir()) top--;
        if (top < bottom) return null;
        int dx = x - job.center.getX(), dz = z - job.center.getZ();
        if (!NuclearExplosion.inCrater(job.radius, job.seed, x, z, dx, top + 1 - cy, dz, 0)) return null;
        return new BlockPos(x, top, z);
    }

    private static void convert(ServerLevel lv, GlowJob job, long deadline) {
        RandomSource rnd = lv.random;
        int total = job.side * job.side;
        double rim = job.radius * NuclearExplosion.RIM_WIDTH;
        long reach2 = (long) job.reach * job.reach;
        for (int n = 0; n < GLOW_COLUMNS_PER_TICK && job.index < total; n++, job.index++) {
            if ((n & 31) == 31 && System.nanoTime() > deadline) return;
            int dx = job.index / job.side - job.reach, dz = job.index % job.side - job.reach;
            long h2 = (long) dx * dx + (long) dz * dz;
            if (h2 > reach2) continue;
            BlockPos p = craterFloor(lv, job, job.center.getX() + dx, job.center.getZ() + dz);
            if (p == null) continue;
            BlockState st = lv.getBlockState(p);
            if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty() || st.getDestroySpeed(lv, p) < 0) continue;
            double f = Math.sqrt(h2) / rim;
            float k = f < 0.5 ? 1f : (float) Math.max(0.2, 1.0 - (f - 0.5) * 1.6);
            float roll = glowEveryColumnForTests ? 0f : rnd.nextFloat();
            for (int i = 0; i < GLOW_BLOCKS.length; i++) {
                roll -= GLOW_CORE[i] * k;
                if (roll < 0) {
                    lv.setBlock(p, GLOW_BLOCKS[i], Block.UPDATE_CLIENTS);
                    break;
                }
            }
        }
    }

    private static void smoke(ServerLevel lv, GlowJob job) {
        RandomSource rnd = lv.random;
        float left = 1f - job.age / (float) SMOKE_TICKS;
        float area = Math.min(4f, (job.radius / (float) NuclearExplosion.BASE_RADIUS) * (job.radius / (float) NuclearExplosion.BASE_RADIUS));
        float rate = SMOKE_RATE * left * left * Math.max(0.2f, area);
        int n = (int) rate + (rnd.nextFloat() < rate - (int) rate ? 1 : 0);
        for (int k = 0; k < n; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = job.radius * 0.9 * Math.sqrt(rnd.nextDouble());
            BlockPos p = craterFloor(lv, job, job.center.getX() + (int) Math.round(Math.cos(a) * d), job.center.getZ() + (int) Math.round(Math.sin(a) * d));
            if (p == null) continue;
            Vec3 at = new Vec3(p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5);
            NuclearExplosion.sendFar(lv, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 1, 0.3, 0.1, 0.3);
            if (rnd.nextInt(4) == 0) NuclearExplosion.sendFar(lv, ParticleTypes.LARGE_SMOKE, at, 2, 0.4, 0.2, 0.4);
        }
    }
}
