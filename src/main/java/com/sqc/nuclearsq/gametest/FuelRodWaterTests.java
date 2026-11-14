package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class FuelRodWaterTests {
    private FuelRodWaterTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "rod_water", timeoutTicks = 100)
    public static void waterFlowsIntoFuelRods(GameTestHelper h) {
        BlockPos source = new BlockPos(3, 1, 7), rod1 = source.east(), rod2 = rod1.east(), head = rod2.east(), after = head.east();
        h.setBlock(rod1, ReactorRegistry.FUEL_ROD_BLOCK.get());
        h.setBlock(rod2, ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED.get());
        h.setBlock(head, ReactorRegistry.FUEL_ROD_HEAD.get());
        h.setBlock(after, Blocks.AIR);
        h.setBlock(source, Blocks.WATER);
        h.succeedWhen(() -> {
            for (BlockPos p : new BlockPos[] {rod1, rod2, head}) {
                h.assertTrue(h.getLevel().getFluidState(h.absolutePos(p)).is(Fluids.WATER), "kein Wasser im Stab bei " + p);
            }
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(after)).is(Fluids.FLOWING_WATER)
                    || h.getLevel().getFluidState(h.absolutePos(after)).is(Fluids.WATER), "Wasser fliesst nicht aus dem Docker weiter");
        });
    }
}
