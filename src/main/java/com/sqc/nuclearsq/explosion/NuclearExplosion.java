package com.sqc.nuclearsq.explosion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.explosion.bomb.FallingBombEntity;
import com.sqc.nuclearsq.network.ExplosionFlashPayload;
import com.sqc.nuclearsq.network.MushroomCloudPayload;
import com.sqc.nuclearsq.radiation.RadiationManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class NuclearExplosion {
    private NuclearExplosion() {}

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final int BASE_RADIUS = 50;
    public static final int MAX_RADIUS = 320;
    public static final double SHOCK_SPEED = 8.0;
    public static final double C_CRATER = 35, C_LIGHT = 75, C_FALLOUT = 150;
    public static final int STEAM_RADIUS = 12;
    private static final double STEAM_LIFT = 0.5;
    private static final double STEAM_DRY = 1.5;
    private static final int STEAM_DEBRIS = 32;
    private static final String DEBRIS_TAG = "nuclearsq_debris";
    private static final float NEAR_SOUND_VOLUME = 5f;
    private static final double PARTICLE_RANGE = 512;

    public record Blast(int radius, float scale, float maxDamage, double damageRange, int flashTicks, boolean heavy) {
        public static final Codec<Blast> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("radius").forGetter(Blast::radius),
                Codec.FLOAT.fieldOf("scale").forGetter(Blast::scale),
                Codec.FLOAT.fieldOf("max_damage").forGetter(Blast::maxDamage),
                Codec.DOUBLE.fieldOf("damage_range").forGetter(Blast::damageRange),
                Codec.INT.fieldOf("flash_ticks").forGetter(Blast::flashTicks),
                Codec.BOOL.fieldOf("heavy").forGetter(Blast::heavy)
        ).apply(i, Blast::new));

        public double flashRange() { return 320 * scale; }
        public double cloudRange() { return 700 * scale; }
        public int burnWidth() { return Math.round(25 * scale); }

        public double yield() { return Math.pow(scale * BASE_RADIUS / C_CRATER, 3); }

        public int falloutRadius() {
            return (int) Math.min(NuclearConfig.FALLOUT_MAX_RADIUS.get(), Math.round(C_FALLOUT * Math.pow(this.yield(), 0.40)));
        }

        public static Blast reactor() {
            float s = ofYield(NuclearConfig.REACTOR_YIELD.get() * NuclearConfig.blastFactor(), false).scale();
            int r = Math.max(1, Math.round(STEAM_RADIUS * s));
            return new Blast(r, s, 60f * s, 2.5 * r, 0, false);
        }

        public static Blast bomb() {
            return ofYield(NuclearConfig.BOMB_YIELD.get() * NuclearConfig.blastFactor(), true);
        }

        public static Blast largeBomb() {
            return ofYield(NuclearConfig.LARGE_BOMB_YIELD.get() * NuclearConfig.blastFactor(), true);
        }

        public static Blast tsarBomb() {
            return ofYield(NuclearConfig.TSAR_BOMB_YIELD.get() * NuclearConfig.blastFactor(), true);
        }

        public static Blast ofYield(double yield, boolean heavy) {
            double cbrt = Math.cbrt(yield);
            double craterR = C_CRATER * cbrt;
            int r = (int) Math.min(MAX_RADIUS, Math.round(craterR));
            float s = (float) (craterR / BASE_RADIUS);
            int flash = heavy ? Math.round(160 * Math.min(s, 1.6f)) : 160;
            return new Blast(r, s, 200f * s, C_LIGHT * cbrt, flash, heavy);
        }
    }

    public static final double RIM_WIDTH = 1.3;
    public static final double RIM_DEPTH = 0.32;
    private static final int RIM_CELL = 9;

    static double rimNoise(int x, int z, int seed) {
        int cx = Math.floorDiv(x, RIM_CELL), cz = Math.floorDiv(z, RIM_CELL);
        double fx = (x - cx * RIM_CELL) / (double) RIM_CELL, fz = (z - cz * RIM_CELL) / (double) RIM_CELL;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(cx, cz, seed), b = hash(cx + 1, cz, seed), c = hash(cx, cz + 1, seed), d = hash(cx + 1, cz + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    private static double hash(int x, int z, int seed) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ seed * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    public static boolean inCrater(int r, int seed, int x, int z, long dx, long dy, long dz, int margin) {
        long rs = r - margin;
        if (dx * dx + dy * dy + dz * dz <= rs * rs) return true;
        double n = rimNoise(x, z, seed);
        double rr = r * RIM_WIDTH * (0.86 + 0.26 * n) - margin;
        double hh = r * RIM_DEPTH * (0.75 + 0.5 * rimNoise(x + 4711, z - 1337, seed)) - margin;
        if (rr <= 0 || hh <= 0) return false;
        double ex = (dx * dx + dz * dz) / (rr * rr), ey = (dy * dy) / (hh * hh);
        return ex + ey <= 1.0;
    }

    public static int craterSeed(BlockPos center) {
        return center.hashCode();
    }

    static final class CraterJob implements BlastJob {
        static final MapCodec<CraterJob> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.fieldOf("center").forGetter(j -> j.center),
                Codec.INT.fieldOf("radius").forGetter(j -> j.radius),
                Codec.INT.fieldOf("section").forGetter(j -> j.section)
        ).apply(i, CraterJob::new));
        static final Type<CraterJob> TYPE = new Type<>("crater", CODEC);

        final BlockPos center;
        final int radius, seed;
        final long[] sections;
        int section;
        int local = 0;
        boolean direct = false;
        int waited = 0;
        final LongOpenHashSet dirty = new LongOpenHashSet();
        int ticksSinceFlush = 0;
        final Long2IntOpenHashMap lastSection = new Long2IntOpenHashMap();
        long[] relightQueue;
        int relightIndex;
        java.util.concurrent.CompletableFuture<?> lightWait;

        CraterJob(BlockPos center, int radius, int section) {
            this.center = center;
            this.radius = radius;
            this.seed = craterSeed(center);
            this.sections = sectionsOf(center, radius);
            this.section = Math.min(section, sections.length);
            this.lastSection.defaultReturnValue(-1);
            for (int i = 0; i < sections.length; i++)
                lastSection.put(ChunkPos.asLong(SectionPos.x(sections[i]), SectionPos.z(sections[i])), i);
        }

        @Override
        public Type<?> type() { return TYPE; }

        @Override
        public boolean tick(ServerLevel level, BlastJobs.Budget budget) {
            boolean done = !budget.craterStopped && tickCrater(level, this, budget);
            if (!done && ticksSinceFlush >= FLUSH_TICKS) flush(level, this, FLUSH_CHUNKS, false);
            return done;
        }

        @Override
        public void onStopping(ServerLevel level) {
            flush(level, this, Integer.MAX_VALUE, true);
        }
    }

    private static long[] sectionsOf(BlockPos c, int r) {
        List<long[]> found = new ArrayList<>();
        long r2 = (long) r * r;
        double rimR = r * RIM_WIDTH * 1.12 + 1, rimH = r * RIM_DEPTH * 1.25 + 1;
        int h = (int) Math.ceil(rimR);
        for (int sx = (c.getX() - h) >> 4; sx <= (c.getX() + h) >> 4; sx++)
            for (int sy = (c.getY() - r) >> 4; sy <= (c.getY() + r) >> 4; sy++)
                for (int sz = (c.getZ() - h) >> 4; sz <= (c.getZ() + h) >> 4; sz++) {
                    long dx = axisGap(c.getX(), sx), dy = axisGap(c.getY(), sy), dz = axisGap(c.getZ(), sz);
                    boolean sphere = dx * dx + dy * dy + dz * dz <= r2;
                    boolean rim = (dx * dx + dz * dz) / (rimR * rimR) + (dy * dy) / (rimH * rimH) <= 1.0;
                    if (!sphere && !rim) continue;
                    long mx = (sx << 4) + 8 - c.getX(), my = (sy << 4) + 8 - c.getY(), mz = (sz << 4) + 8 - c.getZ();
                    found.add(new long[]{SectionPos.asLong(sx, sy, sz), mx * mx + mz * mz, ChunkPos.asLong(sx, sz), my * my});
                }
        found.sort(Comparator.<long[]>comparingLong(a -> a[1]).thenComparingLong(a -> a[2]).thenComparingLong(a -> a[3]));
        return found.stream().mapToLong(a -> a[0]).toArray();
    }

    private static long axisGap(int v, int s) {
        int lo = s << 4, hi = lo + 15;
        return v < lo ? lo - v : (v > hi ? v - hi : 0);
    }

    private static final float BURN_DESTROY = 0.35f;
    private static final float BURN_IGNITE = 0.6f;
    private static final int BURN_COLUMNS_PER_TICK = 400;
    private static final int BURN_DOWN = 20, BURN_UP = 60;
    static final long BURN_BUDGET_NS = 6_000_000L;
    private static final float SCORCH_INNER = 0.95f, SCORCH_OUTER = 0.35f;
    private static final Direction[] FIRE_DIRS = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
    private static final int INNER_FLAGS = REMOVE_FLAGS | Block.UPDATE_KNOWN_SHAPE;
    private static final int SLOW_BLOCK_COST = 3;
    private static final int FLUSH_TICKS = 1;
    private static final int FLUSH_CHUNKS = 64;
    private static final int RELIGHT_CHUNKS = 16;
    private static final int SMALL_CRATER_SECTIONS = 200;
    private static final Heightmap.Types[] HEIGHTMAPS = ChunkStatus.FINAL_HEIGHTMAPS.toArray(Heightmap.Types[]::new);

    static final class BurnJob implements BlastJob {
        static final MapCodec<BurnJob> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.fieldOf("center").forGetter(j -> j.center),
                Codec.INT.fieldOf("inner").forGetter(j -> j.inner),
                Codec.INT.fieldOf("width").forGetter(j -> j.outer - j.inner),
                Codec.INT.fieldOf("index").forGetter(j -> j.index)
        ).apply(i, BurnJob::new));
        static final Type<BurnJob> TYPE = new Type<>("burn", CODEC);

        final BlockPos center;
        final int inner, outer, side;
        int index;
        int waited = 0;

        BurnJob(BlockPos center, int inner, int width, int index) {
            this.center = center;
            this.inner = inner;
            this.outer = inner + width;
            this.side = 2 * outer + 1;
            this.index = index;
        }

        @Override
        public Type<?> type() { return TYPE; }

        @Override
        public boolean tick(ServerLevel level, BlastJobs.Budget budget) {
            if (budget.burnStopped) return false;
            long deadline = budget.burnDeadline();
            tickBurn(level, this, deadline);
            if (System.nanoTime() > deadline) budget.burnStopped = true;
            return index >= side * side;
        }
    }

    static final class ShockJob implements BlastJob {
        static final MapCodec<ShockJob> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Vec3.CODEC.fieldOf("center").forGetter(j -> j.center),
                Blast.CODEC.fieldOf("blast").forGetter(j -> j.blast),
                UUIDUtil.CODEC.listOf().fieldOf("hit").forGetter(j -> List.copyOf(j.hit)),
                Codec.INT.fieldOf("age").forGetter(j -> j.age)
        ).apply(i, ShockJob::new));
        static final Type<ShockJob> TYPE = new Type<>("shock", CODEC);

        final Vec3 center;
        final Blast blast;
        final Set<UUID> hit = new LinkedHashSet<>();
        int age;

        ShockJob(Vec3 center, Blast blast, List<UUID> hit, int age) {
            this.center = center;
            this.blast = blast;
            this.hit.addAll(hit);
            this.age = age;
        }

        double front() { return age * SHOCK_SPEED; }

        @Override
        public Type<?> type() { return TYPE; }

        @Override
        public boolean tick(ServerLevel level, BlastJobs.Budget budget) {
            age++;
            hitShock(level, this);
            return front() > blast.damageRange();
        }
    }

    private static final TicketType<ChunkPos> BLAST_TICKET = TicketType.create("nuclearsq_blast", Comparator.comparingLong(ChunkPos::toLong), 100);
    private static final int MAX_WAIT = 200;

    private static final int PREFETCH_EVERY = 20;
    private static final int PREFETCH_SECTIONS = 1024, PREFETCH_ROWS = 16;

    private static void requestChunk(ServerLevel lv, int cx, int cz) {
        ChunkPos cp = new ChunkPos(cx, cz);
        lv.getChunkSource().addRegionTicket(BLAST_TICKET, cp, 0, cp);
    }

    private static void prefetchCrater(ServerLevel lv, CraterJob job) {
        LongOpenHashSet seen = new LongOpenHashSet();
        int end = Math.min(job.sections.length, job.section + 1 + PREFETCH_SECTIONS);
        for (int i = job.section + 1; i < end && seen.size() < NuclearConfig.CHUNK_LOADS.get(); i++) {
            long sp = job.sections[i];
            int sx = SectionPos.x(sp), sz = SectionPos.z(sp);
            if (lv.hasChunk(sx, sz) || !seen.add(ChunkPos.asLong(sx, sz))) continue;
            requestChunk(lv, sx, sz);
        }
    }

    private static void prefetchBurn(ServerLevel lv, BurnJob job) {
        LongOpenHashSet seen = new LongOpenHashSet();
        long out2 = (long) job.outer * job.outer;
        int total = job.side * job.side, end = (int) Math.min(total, job.index + 1 + (long) PREFETCH_ROWS * job.side);
        for (int i = job.index + 1; i < end && seen.size() < NuclearConfig.CHUNK_LOADS.get(); i++) {
            int dx = i / job.side - job.outer, dz = i % job.side - job.outer;
            if ((long) dx * dx + (long) dz * dz > out2) continue;
            int cx = (job.center.getX() + dx) >> 4, cz = (job.center.getZ() + dz) >> 4;
            if (lv.hasChunk(cx, cz) || !seen.add(ChunkPos.asLong(cx, cz))) continue;
            requestChunk(lv, cx, cz);
        }
    }

    public static void detonate(ServerLevel level, Vec3 c) {
        detonate(level, c, Blast.reactor(), true);
    }

    public static void detonate(ServerLevel level, Vec3 c0, Blast blast) {
        if (NuclearConfig.BOMBS_ENABLED.get()) detonate(level, c0, blast, false);
    }

    private static void detonate(ServerLevel level, Vec3 c0, Blast blast, boolean steam) {
        Vec3 c = com.sqc.nuclearsq.compat.sable.SableCompat.toWorld(level, c0);
        if (NeoForge.EVENT_BUS.post(new NuclearDetonationEvent(level, c, blast)).isCanceled()) return;
        Vec3 core = c;
        if (steam) {
            c = c.add(0, blast.radius() * STEAM_LIFT, 0);
            sendFar(level, ParticleTypes.CLOUD, c, 120, blast.radius() * 0.4, blast.radius(), blast.radius() * 0.4);
            sendFar(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c, 60, blast.radius() * 0.3, blast.radius() * 0.5, blast.radius() * 0.3);
        } else {
            PacketDistributor.sendToPlayersNear(level, null, c.x, c.y, c.z, blast.flashRange(), new ExplosionFlashPayload(blast.flashTicks(), c.x, c.y, c.z, (float) blast.flashRange()));
            PacketDistributor.sendToPlayersNear(level, null, c.x, c.y, c.z, blast.cloudRange(),
                    new MushroomCloudPayload(c.x, c.y, c.z, blast.scale(), blast.heavy()));
        }

        level.explode(null, c.x, c.y, c.z, blast.heavy() ? 10.0f : 6.0f, Level.ExplosionInteraction.NONE);
        double spread = steam ? blast.radius() * 0.5 : 30 * blast.scale();
        sendFar(level, ParticleTypes.EXPLOSION_EMITTER, c, Math.min(40, Math.round(12 * blast.scale() * blast.scale())), spread, spread, spread);
        if (!steam) sendFar(level, ParticleTypes.FLASH, c, blast.heavy() ? 16 : 6, 2.0 * blast.scale(), 2.0 * blast.scale(), 2.0 * blast.scale());
        if (blast.heavy()) {
            level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 2, c.z, 24, 6, 3, 6, 0.0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, NEAR_SOUND_VOLUME, 0.5f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, NEAR_SOUND_VOLUME, 0.5f);
        }

        if (NuclearConfig.DESTROY_BLOCKS.get()) com.sqc.nuclearsq.kit.KitMarks.nuke(level, BlockPos.containing(c), blast.radius());
        if (NuclearConfig.DESTROY_BLOCKS.get()) vaporize(level, c, blast.radius());
        if (steam && NuclearConfig.DESTROY_BLOCKS.get()) {
            evaporate(level, BlockPos.containing(core), (int) Math.round(blast.radius() * STEAM_DRY));
            debris(level, BlockPos.containing(c), blast.radius());
        }

        ShockJob shock = new ShockJob(c, blast, List.of(), 0);
        hitShock(level, shock);
        BlastJobs.add(level, shock);

        RadiationManager.addCore(level, core, blast.scale());
        RadiationManager.scatterFallout(level, core, blast.radius(), blast.scale(), blast.falloutRadius());
        AshFall.start(level, c, blast.radius());

        BlockPos center = BlockPos.containing(c);
        if (NuclearConfig.DESTROY_BLOCKS.get()) {
            LOGGER.info("Nuclear detonation at {}: crater radius {}, light damage range {}, fallout radius {}, yield {}",
                    center, blast.radius(), Math.round(blast.damageRange()), blast.falloutRadius(), Math.round(blast.yield() * 100) / 100.0);
            BlastJobs.add(level, new CraterJob(center, blast.radius(), 0));
            BlastJobs.add(level, new BurnJob(center, (int) Math.round(blast.radius() * RIM_WIDTH), steam ? blast.radius() / 2 : blast.burnWidth(), 0));
            BlastAftermath.shatter(level, c, (int) Math.round(blast.radius() * RIM_WIDTH), (int) Math.ceil(blast.damageRange()));
            BlastAftermath.flatten(level, c, (int) Math.round(blast.radius() * RIM_WIDTH * 0.85),
                    (int) Math.min(Math.ceil(blast.damageRange()), Math.round(blast.radius() * RIM_WIDTH * BlastAftermath.FLATTEN_REACH)));
        }
    }

    static void sendFar(ServerLevel level, ParticleOptions type, Vec3 c, int count, double dx, double dy, double dz) {
        double r2 = PARTICLE_RANGE * PARTICLE_RANGE;
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(c) <= r2) level.sendParticles(p, type, true, c.x, c.y, c.z, count, dx, dy, dz, 0.0);
    }

    private static void evaporate(ServerLevel level, BlockPos center, int r) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            int x = center.getX() + dx, z = center.getZ() + dz;
            if (!level.hasChunk(x >> 4, z >> 4) || BlastShield.blocked(level, center, x >> 4, z >> 4)) continue;
            for (int dy = -r; dy <= r; dy++) {
                if (dx * dx + dy * dy + dz * dz > r * r) continue;
                p.set(x, center.getY() + dy, z);
                BlockState st = level.getBlockState(p);
                if (!st.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) continue;
                if (st.hasProperty(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED))
                    level.setBlock(p, st.setValue(com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED, false), Block.UPDATE_CLIENTS);
                else if (st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || st.canBeReplaced())
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void debris(ServerLevel level, BlockPos center, int r) {
        int seed = craterSeed(center);
        var rnd = level.random;
        for (int i = 0, n = 0; i < STEAM_DEBRIS * 8 && n < STEAM_DEBRIS; i++) {
            int dx = rnd.nextInt(2 * r + 1) - r, dy = rnd.nextInt(2 * r + 1) - r, dz = rnd.nextInt(2 * r + 1) - r;
            BlockPos p = center.offset(dx, dy, dz);
            BlockState st = level.getBlockState(p);
            if (st.isAir() || st.hasBlockEntity() || st.getDestroySpeed(level, p) < 0 || !st.getFluidState().isEmpty()
                    || "nuclearsq".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace())
                    || !inCrater(r, seed, p.getX(), p.getZ(), dx, dy, dz, 1) || BlastShield.blocked(level, center, p.getX() >> 4, p.getZ() >> 4))
                continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(level, p, st);
            double a = rnd.nextDouble() * Math.PI * 2, v = 0.5 + rnd.nextDouble() * 0.7;
            fb.setDeltaMovement(Math.cos(a) * v, 0.7 + rnd.nextDouble() * 0.7, Math.sin(a) * v);
            fb.dropItem = false;
            fb.addTag(DEBRIS_TAG);
            fb.hurtMarked = true;
            n++;
        }
    }

    private static void vaporize(ServerLevel level, Vec3 c, int r) {
        BlockPos center = BlockPos.containing(c);
        int seed = craterSeed(center);
        double reach = r * RIM_WIDTH * 1.12 + 1;
        AABB box = new AABB(c.x - reach, c.y - r, c.z - reach, c.x + reach, c.y + r * RIM_DEPTH * 1.25 + 1, c.z + reach);
        for (Entity e : level.getEntities((Entity) null, box, NuclearExplosion::vaporizes)) {
            BlockPos p = e.blockPosition();
            if (inCrater(r, seed, p.getX(), p.getZ(), p.getX() - center.getX(), p.getY() - center.getY(), p.getZ() - center.getZ(), 0)
                    && !BlastShield.blocked(level, center, p.getX() >> 4, p.getZ() >> 4))
                e.discard();
        }
    }

    private static boolean vaporizes(Entity e) {
        if (e instanceof FallingBombEntity || e.getTags().contains(DEBRIS_TAG)) return false;
        return e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof HangingEntity
                || e instanceof VehicleEntity || e instanceof FallingBlockEntity || e instanceof Projectile;
    }

    private static void hitShock(ServerLevel level, ShockJob job) {
        double range = job.blast.damageRange(), front = Math.min(range, job.front());
        Vec3 c = job.center;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(front))) {
            double d = e.position().distanceTo(c);
            if (d > front || !job.hit.add(e.getUUID())) continue;
            double f = 1.0 - d / range;
            e.hurt(level.damageSources().explosion(null, null), (float) (job.blast.maxDamage() * f));
            if (job.blast.heavy() && d > 0.1) {
                Vec3 push = e.position().subtract(c).normalize().scale(4.0 * f);
                e.push(push.x, 0.6 * f + push.y * 0.3, push.z);
                e.hurtMarked = true;
            }
        }
    }

    public static boolean changingBlocks() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && BlastJobs.changingBlocks(server);
    }

    private static boolean tickCrater(ServerLevel lv, CraterJob job, BlastJobs.Budget budget) {
        long deadline = budget.craterDeadline();
        int perTick = budget.craterPerTick();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        job.ticksSinceFlush++;
        int cx = job.center.getX(), cy = job.center.getY(), cz = job.center.getZ();
        for (; job.section < job.sections.length; job.section++, job.local = 0, job.direct = false) {
            long sp = job.sections[job.section];
            int sx = SectionPos.x(sp), sy = SectionPos.y(sp), sz = SectionPos.z(sp);
            if (sy < lv.getMinSection() || sy >= lv.getMaxSection()) continue;
            if (!lv.hasChunk(sx, sz)) {
                if (job.waited++ < MAX_WAIT) {
                    requestChunk(lv, sx, sz);
                    if (job.waited % PREFETCH_EVERY == 1) prefetchCrater(lv, job);
                    return false;
                }
                job.waited = 0;
                continue;
            }
            job.waited = 0;
            if (BlastShield.blocked(lv, job.center, sx, sz)) continue;
            LevelChunk chunk = lv.getChunk(sx, sz);
            LevelChunkSection sec = chunk.getSection(lv.getSectionIndexFromSectionY(sy));
            if (job.local == 0 && sec.hasOnlyAir()) continue;
            int bx = sx << 4, by = sy << 4, bz = sz << 4;
            if (job.local == 0 && !job.direct && sectionInsideSphere(job.radius, cx, cy, cz, bx, by, bz)
                    && !sec.maybeHas(st -> needsCarefulRemoval(lv, st))) {
                if (budget.craterBlocks >= perTick || System.nanoTime() > deadline) {
                    budget.craterStopped = true;
                    return false;
                }
                chunk.getSections()[lv.getSectionIndexFromSectionY(sy)] = new LevelChunkSection(
                        new net.minecraft.world.level.chunk.PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, air,
                                net.minecraft.world.level.chunk.PalettedContainer.Strategy.SECTION_STATES),
                        sec.getBiomes());
                budget.craterBlocks += SECTION_CLEAR_COST;
                markDirty(job, chunk);
                lv.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(sp), true);
                continue;
            }
            for (; job.local < 4096; job.local++) {
                if (budget.craterBlocks >= perTick || ((job.local & 255) == 255 && System.nanoTime() > deadline)) {
                    if (job.direct) markDirty(job, chunk);
                    budget.craterStopped = true;
                    return false;
                }
                int lx = job.local & 15, lz = (job.local >> 4) & 15, ly = job.local >> 8;
                long dx = bx + lx - cx, dy = by + ly - cy, dz = bz + lz - cz;
                BlockState st = sec.getBlockState(lx, ly, lz);
                if (st.isAir() || !inCrater(job.radius, job.seed, bx + lx, bz + lz, dx, dy, dz, 0)) continue;
                boolean inner = inCrater(job.radius, job.seed, bx + lx, bz + lz, dx, dy, dz, 1);
                budget.craterBlocks++;
                p.set(bx + lx, by + ly, bz + lz);
                if (st.getDestroySpeed(lv, p) < 0) continue;
                if (inner && !st.hasBlockEntity() && PoiTypes.forState(st).isEmpty()) {
                    if (st.getBlock() instanceof com.sqc.nuclearsq.radiation.FalloutBlock) {
                        com.sqc.nuclearsq.radiation.RadiationManager.forgetFallout(lv, p);
                    }
                    sec.setBlockState(lx, ly, lz, air);
                    if (st.getLightEmission(lv, p) > 0) lv.getChunkSource().getLightEngine().checkBlock(p);
                    job.direct = true;
                    continue;
                }
                budget.craterBlocks += SLOW_BLOCK_COST;
                if (st.hasBlockEntity()) Clearable.tryClear(lv.getBlockEntity(p));
                lv.setBlock(p, air, inner ? INNER_FLAGS : REMOVE_FLAGS);
            }
            if (job.direct) {
                markDirty(job, chunk);
                if (sec.hasOnlyAir()) lv.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(sp), true);
            }
        }
        if (!job.dirty.isEmpty()) {
            flush(lv, job, FLUSH_CHUNKS, false);
            return false;
        }
        if (!relight(lv, job)) return false;
        vaporize(lv, Vec3.atCenterOf(job.center), job.radius);
        BlastAftermath.glow(lv, job.center, job.radius);
        LOGGER.info("Nuclear crater at {} finished ({} sections)", job.center, job.sections.length);
        return true;
    }

    private static final int SECTION_CLEAR_COST = 128;

    private static boolean sectionInsideSphere(int r, int cx, int cy, int cz, int bx, int by, int bz) {
        long mx = Math.max(Math.abs(bx - cx), Math.abs(bx + 15 - cx));
        long my = Math.max(Math.abs(by - cy), Math.abs(by + 15 - cy));
        long mz = Math.max(Math.abs(bz - cz), Math.abs(bz + 15 - cz));
        long rs = r - 1;
        return mx * mx + my * my + mz * mz <= rs * rs;
    }

    private static boolean needsCarefulRemoval(ServerLevel lv, BlockState st) {
        return st.hasBlockEntity() || st.getLightEmission() > 0 || PoiTypes.forState(st).isPresent()
                || st.getBlock() instanceof com.sqc.nuclearsq.radiation.FalloutBlock
                || st.getDestroySpeed(lv, BlockPos.ZERO) < 0;
    }

    private static void markDirty(CraterJob job, LevelChunk chunk) {
        if (job.dirty.add(chunk.getPos().toLong())) chunk.setUnsaved(true);
    }

    private static void flush(ServerLevel lv, CraterJob job, int max, boolean force) {
        if (job.dirty.isEmpty()) return;
        boolean small = job.sections.length <= SMALL_CRATER_SECTIONS;
        if (small) {
            max = Integer.MAX_VALUE;
            force = true;
        }
        if (!force && job.lightWait != null && !job.lightWait.isDone()) return;
        job.ticksSinceFlush = 0;
        var light = lv.getChunkSource().getLightEngine();
        int sent = 0;
        ChunkPos last = null;
        LongIterator it = job.dirty.iterator();
        while (it.hasNext() && sent < max) {
            long key = it.nextLong();
            ChunkPos pos = new ChunkPos(key);
            if (!lv.hasChunk(pos.x, pos.z)) {
                it.remove();
                continue;
            }
            if (!force && job.lastSection.get(key) >= job.section) continue;
            it.remove();
            sent++;
            last = pos;
            LevelChunk chunk = lv.getChunk(pos.x, pos.z);
            updateHeightmaps(chunk);
            chunk.initializeLightSources();
            light.propagateLightSources(pos);
            List<ServerPlayer> players = lv.getChunkSource().chunkMap.getPlayers(pos, false);
            if (!players.isEmpty()) {
                var packet = new ClientboundLevelChunkWithLightPacket(chunk, light, null, null);
                for (ServerPlayer player : players) player.connection.send(packet);
            }
        }
        if (last != null) job.lightWait = light.waitForPendingTasks(last.x, last.z);
    }

    private static boolean relight(ServerLevel lv, CraterJob job) {
        var light = lv.getChunkSource().getLightEngine();
        boolean small = job.sections.length <= SMALL_CRATER_SECTIONS;
        if (!small && job.lightWait != null && !job.lightWait.isDone()) return false;
        if (job.relightQueue == null) {
            LongOpenHashSet chunks = new LongOpenHashSet();
            for (long sp : job.sections)
                for (int ox = -1; ox <= 1; ox++)
                    for (int oz = -1; oz <= 1; oz++) chunks.add(ChunkPos.asLong(SectionPos.x(sp) + ox, SectionPos.z(sp) + oz));
            job.relightQueue = chunks.toLongArray();
            job.relightIndex = 0;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int end = small ? job.relightQueue.length : Math.min(job.relightQueue.length, job.relightIndex + RELIGHT_CHUNKS);
        for (; job.relightIndex < end; job.relightIndex++) {
            ChunkPos pos = new ChunkPos(job.relightQueue[job.relightIndex]);
            if (!lv.hasChunk(pos.x, pos.z)) continue;
            LevelChunk chunk = lv.getChunk(pos.x, pos.z);
            chunk.initializeLightSources();
            var sources = chunk.getSkyLightSources();
            for (int lz = 0; lz < 16; lz++)
                for (int lx = 0; lx < 16; lx++) {
                    int y = sources.getLowestSourceY(lx, lz);
                    if (y == Integer.MIN_VALUE || y < lv.getMinBuildHeight()) continue;
                    light.checkBlock(p.set(pos.getMinBlockX() + lx, y, pos.getMinBlockZ() + lz));
                }
            light.propagateLightSources(pos);
            light.waitForPendingTasks(pos.x, pos.z).thenRun(() -> lv.getServer().execute(() -> {
                if (!lv.hasChunk(pos.x, pos.z)) return;
                List<ServerPlayer> players = lv.getChunkSource().chunkMap.getPlayers(pos, false);
                if (players.isEmpty()) return;
                var packet = new ClientboundLightUpdatePacket(pos, light, null, null);
                for (ServerPlayer player : players) player.connection.send(packet);
            }));
            job.lightWait = light.waitForPendingTasks(pos.x, pos.z);
        }
        return job.relightIndex >= job.relightQueue.length;
    }

    private static void updateHeightmaps(LevelChunk chunk) {
        int minY = chunk.getMinBuildHeight(), bits = Mth.ceillog2(chunk.getHeight() + 1);
        int types = HEIGHTMAPS.length;
        BitStorage[] data = new BitStorage[types];
        for (int t = 0; t < types; t++)
            data[t] = new SimpleBitStorage(bits, 256, chunk.getOrCreateHeightmapUnprimed(HEIGHTMAPS[t]).getRawData());
        int top = chunk.getHighestFilledSectionIndex();
        int[] height = new int[types];
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                Arrays.fill(height, minY);
                int open = types;
                for (int si = top; si >= 0 && open > 0; si--) {
                    LevelChunkSection sec = chunk.getSection(si);
                    if (sec.hasOnlyAir()) continue;
                    int base = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
                    for (int ly = 15; ly >= 0 && open > 0; ly--) {
                        BlockState st = sec.getBlockState(x, ly, z);
                        if (st.isAir()) continue;
                        for (int t = 0; t < types; t++)
                            if (height[t] == minY && HEIGHTMAPS[t].isOpaque().test(st)) {
                                height[t] = base + ly + 1;
                                open--;
                            }
                    }
                }
                for (int t = 0; t < types; t++) data[t].set(x + z * 16, height[t] - minY);
            }
    }

    private static boolean scorch(ServerLevel lv, BlockPos p, BlockState st, net.minecraft.util.RandomSource rnd) {
        BlockState above = lv.getBlockState(p.above());
        if (!above.isAir() && !above.canBeReplaced()) return false;
        BlockState to;
        if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.MYCELIUM) || st.is(Blocks.PODZOL) || st.is(Blocks.MOSS_BLOCK)
                || st.is(Blocks.DIRT_PATH)) {
            to = rnd.nextFloat() < 0.6f ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        } else if (st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW)) {
            to = Blocks.AIR.defaultBlockState();
        } else if (st.is(Blocks.ICE) || st.is(Blocks.PACKED_ICE) || st.is(Blocks.BLUE_ICE) || st.is(Blocks.FROSTED_ICE)) {
            to = lv.dimensionType().ultraWarm() ? Blocks.AIR.defaultBlockState() : Fluids.WATER.defaultFluidState().createLegacyBlock();
        } else {
            return false;
        }
        lv.setBlock(p, to, to.isAir() ? REMOVE_FLAGS : Block.UPDATE_ALL);
        return true;
    }

    private static void tickBurn(ServerLevel lv, BurnJob job, long deadline) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        var rnd = lv.random;
        long in2 = (long) job.inner * job.inner, out2 = (long) job.outer * job.outer;
        int total = job.side * job.side;
        int cy = job.center.getY();
        int yMin = Math.max(lv.getMinBuildHeight(), cy - BURN_DOWN);
        int yMax = Math.min(lv.getMaxBuildHeight() - 1, cy + BURN_UP);
        long reach = Math.max(cy - yMin, yMax - cy);
        long skip2 = in2 - reach * reach;
        for (int n = 0; n < BURN_COLUMNS_PER_TICK && job.index < total; job.index++) {
            int dx = job.index / job.side - job.outer, dz = job.index % job.side - job.outer;
            long h2 = (long) dx * dx + (long) dz * dz;
            if (h2 > out2 || h2 <= skip2) continue;
            if (n > 0 && (n & 15) == 0 && System.nanoTime() > deadline) break;
            n++;
            int x = job.center.getX() + dx, z = job.center.getZ() + dz;
            if (!lv.isLoaded(p.set(x, cy, z))) {
                if (job.waited++ < MAX_WAIT) {
                    requestChunk(lv, x >> 4, z >> 4);
                    if (job.waited % PREFETCH_EVERY == 1) prefetchBurn(lv, job);
                    break;
                }
                job.waited = 0;
                continue;
            }
            job.waited = 0;
            if (BlastShield.blocked(lv, job.center, x >> 4, z >> 4)) continue;
            float scorch = SCORCH_INNER - (SCORCH_INNER - SCORCH_OUTER)
                    * (float) Mth.clamp((Math.sqrt(h2) - job.inner) / Math.max(1, job.outer - job.inner), 0.0, 1.0);
            for (int y = yMin; y <= yMax; y++) {
                int dy = y - cy;
                long d2 = h2 + (long) dy * dy;
                if (d2 <= in2 || d2 > out2) continue;
                p.set(x, y, z);
                BlockState st = lv.getBlockState(p);
                if (st.isAir()) continue;
                if (rnd.nextFloat() < scorch && scorch(lv, p, st, rnd)) continue;
                if (st.getFlammability(lv, p, Direction.UP) <= 0) continue;
                if (rnd.nextFloat() < BURN_DESTROY) {
                    lv.setBlock(p, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
                } else if (rnd.nextFloat() < BURN_IGNITE) {
                    for (Direction dir : FIRE_DIRS) {
                        q.setWithOffset(p, dir);
                        if (lv.getBlockState(q).isAir()) {
                            BlockState fire = Blocks.FIRE.defaultBlockState();
                            if (fire.canSurvive(lv, q)) lv.setBlock(q, fire, Block.UPDATE_ALL);
                            break;
                        }
                    }
                }
            }
        }
    }
}
