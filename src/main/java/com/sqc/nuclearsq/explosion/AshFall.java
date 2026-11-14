package com.sqc.nuclearsq.explosion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.registry.CatalogRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class AshFall {
    private AshFall() {}

    private static final int DELAY = 420, DURATION = 1000;
    static final int PER_TICK = 160;

    static final class Job implements BlastJob {
        static final MapCodec<Job> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Vec3.CODEC.fieldOf("center").forGetter(j -> j.center),
                Codec.INT.fieldOf("inner").forGetter(j -> j.inner),
                Codec.INT.fieldOf("range").forGetter(j -> j.range),
                Codec.INT.fieldOf("age").forGetter(j -> j.age)
        ).apply(i, Job::new));
        static final Type<Job> TYPE = new Type<>("ash", CODEC);

        final Vec3 center;
        final int inner, range;
        int age;

        Job(Vec3 center, int inner, int range, int age) {
            this.center = center; this.inner = inner; this.range = range; this.age = age;
        }

        @Override
        public Type<?> type() { return TYPE; }

        @Override
        public boolean changesBlocks() { return false; }

        @Override
        public boolean tick(ServerLevel level, BlastJobs.Budget budget) {
            if (++age > DELAY + DURATION) return true;
            if (age < DELAY || budget.ash <= 0) return false;
            float ramp = Math.min(1f, (age - DELAY) / 200f);
            int n = Math.max(1, Math.round(budget.ash * ramp));
            budget.ash -= n;
            for (int k = 0; k < n; k++) drop(level, this);
            return false;
        }
    }

    public static void start(ServerLevel level, Vec3 center, int craterRadius) {
        if (!NuclearConfig.ASH_LAYER.get()) return;
        int inner = (int) Math.round(craterRadius * NuclearExplosion.RIM_WIDTH) + 2;
        BlastJobs.add(level, new Job(center, inner, Math.max(inner + 40, (int) Math.round(craterRadius * 3.4)), 0));
    }

    private static void drop(ServerLevel lv, Job job) {
        RandomSource r = lv.random;
        double a = r.nextDouble() * Math.PI * 2;
        double f = Math.pow(r.nextDouble(), 1.3);
        double d = job.inner + f * (job.range - job.inner);
        int x = Mth.floor(job.center.x + Math.cos(a) * d), z = Mth.floor(job.center.z + Math.sin(a) * d);
        if (!lv.isLoaded(new BlockPos(x, lv.getMinBuildHeight(), z))) return;
        int maxLayers = f < 0.3 ? 3 : (f < 0.65 ? 2 : 1);
        BlockPos p = new BlockPos(x, lv.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        Block ash = CatalogRegistry.ASH.get();
        BlockState here = lv.getBlockState(p);
        if (here.is(ash)) {
            int layers = here.getValue(AshLayerBlock.LAYERS);
            if (layers < maxLayers) lv.setBlock(p, here.setValue(AshLayerBlock.LAYERS, layers + 1), Block.UPDATE_CLIENTS);
            return;
        }
        if (!here.canBeReplaced() || !lv.getFluidState(p).isEmpty() || !here.getFluidState().isEmpty()) return;
        BlockState state = ash.defaultBlockState();
        if (!state.canSurvive(lv, p)) return;
        lv.setBlock(p, state, Block.UPDATE_CLIENTS);
    }
}
