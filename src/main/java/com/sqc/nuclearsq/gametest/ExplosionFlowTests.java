package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.explosion.BlastAftermath;
import com.sqc.nuclearsq.explosion.NuclearExplosion;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ExplosionFlowTests {
    private ExplosionFlowTests() {}

    private static final String FLOOR = "floor";

    static void detonate(GameTestHelper h, BlockPos center, NuclearExplosion.Blast blast) {
        boolean ash = NuclearConfig.ASH_LAYER.get();
        NuclearConfig.ASH_LAYER.set(false);
        try {
            NuclearExplosion.detonate(h.getLevel(), Vec3.atCenterOf(center), blast);
        } finally {
            NuclearConfig.ASH_LAYER.set(ash);
        }
    }

    static void settled() {
        if (NuclearExplosion.changingBlocks()) throw new GameTestAssertException("explosion still changing blocks");
    }

    @GameTest(template = FLOOR, batch = "blast_shock", timeoutTicks = 100)
    public static void shockwaveArrivesLater(GameTestHelper h) {
        var pig = h.spawn(EntityType.PIG, new BlockPos(13, 4, 13));
        pig.setNoAi(true);
        float full = pig.getHealth();
        BlockPos center = h.absolutePos(new BlockPos(1, 5, 1));
        double d = pig.position().distanceTo(Vec3.atCenterOf(center));
        if (d <= 2 * NuclearExplosion.SHOCK_SPEED) throw new GameTestAssertException("pig too close for the test: " + d);
        detonate(h, center, new NuclearExplosion.Blast(1, 0.01f, 5f, 40.0, 0, false));
        h.runAfterDelay(1, () -> {
            if (pig.getHealth() < full) throw new GameTestAssertException("pig hit before the shock front arrived");
        });
        h.succeedWhen(() -> {
            settled();
            if (pig.getHealth() >= full) throw new GameTestAssertException("shock front never hit the pig");
        });
    }

    @GameTest(template = FLOOR, batch = "blast_vaporize", timeoutTicks = 100)
    public static void craterVaporizesItems(GameTestHelper h) {
        BlockPos mid = new BlockPos(7, 3, 7);
        BlockPos center = h.absolutePos(mid);
        ItemEntity inside = h.spawnItem(Items.DIAMOND, new BlockPos(7, 4, 7));
        ItemEntity outside = h.spawnItem(Items.EMERALD, new BlockPos(1, 5, 1));
        inside.setNoGravity(true);
        outside.setNoGravity(true);
        detonate(h, center, new NuclearExplosion.Blast(4, 0.01f, 0f, 1.0, 0, false));
        if (!inside.isRemoved()) throw new GameTestAssertException("item inside the crater survived");
        if (outside.isRemoved()) throw new GameTestAssertException("item outside the crater was removed");
        h.succeedWhen(ExplosionFlowTests::settled);
    }

    @GameTest(template = FLOOR, batch = "blast_scorch", timeoutTicks = 100)
    public static void burnZoneScorchesGrass(GameTestHelper h) {
        BlockPos mid = new BlockPos(7, 3, 7);
        BlockPos[] grass = {
                new BlockPos(12, 3, 7), new BlockPos(2, 3, 7), new BlockPos(7, 3, 12), new BlockPos(7, 3, 2),
                new BlockPos(10, 3, 11), new BlockPos(4, 3, 3), new BlockPos(11, 3, 4), new BlockPos(3, 3, 10),
        };
        for (BlockPos p : grass) h.setBlock(p, Blocks.GRASS_BLOCK);
        detonate(h, h.absolutePos(mid), new NuclearExplosion.Blast(3, 0.12f, 0f, 1.0, 0, false));
        h.succeedWhen(() -> {
            settled();
            int burnt = 0;
            for (BlockPos p : grass) {
                var st = h.getBlockState(p);
                if (st.is(Blocks.DIRT) || st.is(Blocks.COARSE_DIRT)) burnt++;
                else if (!st.is(Blocks.GRASS_BLOCK)) throw new GameTestAssertException("unexpected block at " + p + ": " + st);
            }
            if (burnt == 0) throw new GameTestAssertException("no grass scorched in the burn zone");
        });
    }

    @GameTest(template = FLOOR, batch = "blast_shatter", timeoutTicks = 100)
    public static void shockwaveShattersGlass(GameTestHelper h) {
        BlockPos mid = new BlockPos(7, 4, 7);
        BlockPos[] glass = {
                new BlockPos(14, 4, 7), new BlockPos(0, 4, 7), new BlockPos(7, 4, 14), new BlockPos(7, 4, 0),
                new BlockPos(12, 4, 12), new BlockPos(2, 4, 2), new BlockPos(12, 4, 2), new BlockPos(2, 4, 12),
        };
        for (BlockPos p : glass) h.setBlock(p, Blocks.GLASS);
        detonate(h, h.absolutePos(mid), new NuclearExplosion.Blast(2, 0.01f, 0f, 13.0, 0, false));
        h.succeedWhen(() -> {
            settled();
            int broken = 0;
            for (BlockPos p : glass) if (h.getBlockState(p).isAir()) broken++;
            if (broken == 0) throw new GameTestAssertException("shock wave broke no glass");
        });
    }

    @GameTest(template = FLOOR, batch = "blast_glow", timeoutTicks = 200)
    public static void craterFloorGlows(GameTestHelper h) {
        BlockPos mid = new BlockPos(7, 3, 7);
        BlastAftermath.glowEveryColumnForTests = true;
        detonate(h, h.absolutePos(mid), new NuclearExplosion.Blast(3, 0.01f, 0f, 1.0, 0, false));
        h.succeedWhen(() -> {
            settled();
            BlastAftermath.glowEveryColumnForTests = false;
            int glow = 0;
            for (int x = 3; x <= 11; x++)
                for (int y = 0; y <= 3; y++)
                    for (int z = 3; z <= 11; z++) {
                        BlockState st = h.getBlockState(new BlockPos(x, y, z));
                        if (st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.OBSIDIAN) || st.is(Blocks.BASALT) || st.is(Blocks.TUFF)) glow++;
                    }
            if (glow == 0) throw new GameTestAssertException("crater floor did not glow");
        });
    }

    @GameTest(template = FLOOR, batch = "blast_light", timeoutTicks = 200)
    public static void craterFloorIsLit(GameTestHelper h) {
        BlockPos mid = new BlockPos(7, 3, 7);
        detonate(h, h.absolutePos(mid), new NuclearExplosion.Blast(6, 0.01f, 0f, 1.0, 0, false));
        int[] quiet = {0};
        h.succeedWhen(() -> {
            settled();
            if (++quiet[0] < 20) throw new GameTestAssertException("waiting for the light engine");
            for (int x = 3; x <= 11; x++)
                for (int z = 3; z <= 11; z++) {
                    int y = 4;
                    while (y > 0 && h.getBlockState(new BlockPos(x, y, z)).isAir()) y--;
                    int sky = h.getLevel().getBrightness(LightLayer.SKY, h.absolutePos(new BlockPos(x, y + 1, z)));
                    if (sky < 13) throw new GameTestAssertException("dark crater floor at " + x + "," + (y + 1) + "," + z + ": sky " + sky);
                }
        });
    }

    @GameTest(template = FLOOR, batch = "blast_steam", timeoutTicks = 100)
    public static void reactorSteamBlastDriesWater(GameTestHelper h) {
        double yield = NuclearConfig.REACTOR_YIELD.get();
        int falloutMax = NuclearConfig.FALLOUT_MAX_RADIUS.get();
        boolean ash = NuclearConfig.ASH_LAYER.get();
        BlockPos water = new BlockPos(7, 4, 9), slab = new BlockPos(9, 4, 7);
        NuclearConfig.REACTOR_YIELD.set(0.0000029);
        NuclearConfig.FALLOUT_MAX_RADIUS.set(10);
        NuclearConfig.ASH_LAYER.set(false);
        try {
            NuclearExplosion.Blast b = NuclearExplosion.Blast.reactor();
            if (b.radius() != 1 || b.flashTicks() != 0 || b.heavy()) throw new GameTestAssertException("reactor blast " + b);
            h.setBlock(water, Blocks.WATER);
            h.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
            Vec3 core = Vec3.atCenterOf(h.absolutePos(new BlockPos(7, 4, 7)));
            NuclearExplosion.detonate(h.getLevel(), core);
            com.sqc.nuclearsq.radiation.RadiationWorldData.get(h.getLevel()).removeCoresAt(core);
        } finally {
            NuclearConfig.REACTOR_YIELD.set(yield);
            NuclearConfig.FALLOUT_MAX_RADIUS.set(falloutMax);
            NuclearConfig.ASH_LAYER.set(ash);
        }
        h.succeedWhen(() -> {
            settled();
            if (!h.getLevel().getFluidState(h.absolutePos(water)).isEmpty()) throw new GameTestAssertException("water did not boil away");
            if (!h.getLevel().getFluidState(h.absolutePos(slab)).isEmpty()) throw new GameTestAssertException("waterlogged block still holds water");
            BlockPos c = h.absolutePos(new BlockPos(7, 4, 7));
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-16, -8, -16), c.offset(16, 16, 16)))
                if (h.getLevel().getBlockState(p).getBlock() instanceof com.sqc.nuclearsq.radiation.FalloutBlock)
                    h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        });
    }
}
