package com.sqc.nuclearsq.explosion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.registry.CatalogRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class FalloutScatterJob implements BlastJob {
    static final int PER_TICK = 150;

    public static final MapCodec<FalloutScatterJob> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Vec3.CODEC.fieldOf("center").forGetter(j -> j.center),
            Codec.INT.fieldOf("inner").forGetter(j -> j.inner),
            Codec.INT.fieldOf("range").forGetter(j -> j.range),
            Codec.INT.fieldOf("count").forGetter(j -> j.count),
            Codec.INT.fieldOf("placed").forGetter(j -> j.placed),
            Codec.INT.fieldOf("tries").forGetter(j -> j.tries)
    ).apply(i, FalloutScatterJob::new));
    public static final Type<FalloutScatterJob> TYPE = new Type<>("fallout", CODEC);

    private final Vec3 center;
    private final int inner, range, count;
    private int placed, tries;

    public FalloutScatterJob(Vec3 center, int inner, int range, int count) {
        this(center, inner, range, count, 0, 0);
    }

    private FalloutScatterJob(Vec3 center, int inner, int range, int count, int placed, int tries) {
        this.center = center;
        this.inner = inner;
        this.range = range;
        this.count = count;
        this.placed = placed;
        this.tries = tries;
    }

    @Override
    public Type<?> type() {
        return TYPE;
    }

    private boolean done() {
        return placed >= count || tries >= count * 4;
    }

    @Override
    public boolean tick(ServerLevel lv, BlastJobs.Budget budget) {
        if (budget.fallout <= 0) return false;
        RandomSource r = lv.random;
        for (int n = 0; n < budget.fallout * 4 && placed < count && tries < count * 4; n++, tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = inner + Math.sqrt(r.nextDouble()) * (range - inner);
            int x = Mth.floor(center.x + Math.cos(a) * d), z = Mth.floor(center.z + Math.sin(a) * d);
            if (!lv.isLoaded(new BlockPos(x, lv.getMinBuildHeight(), z))) continue;
            BlockPos p = new BlockPos(x, lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockPos below = p.below();
            if (!lv.getBlockState(p).canBeReplaced() || !lv.getFluidState(p).isEmpty()) continue;
            if (!lv.getBlockState(below).isFaceSturdy(lv, below, Direction.UP)) continue;
            lv.setBlock(p, CatalogRegistry.FALLOUT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            placed++;
            if (--budget.fallout <= 0) break;
        }
        return done();
    }
}
