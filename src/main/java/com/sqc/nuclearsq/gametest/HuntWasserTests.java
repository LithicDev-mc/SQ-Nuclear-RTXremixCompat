package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class HuntWasserTests {
    private HuntWasserTests() {}

    private static final BigMachine M = BigMachine.WATER_TREATMENT;
    private static final String[] OUTS = {"feedwater_out", "feedwater_out_2", "feedwater_out_3", "feedwater_out_4"};

    @GameTest(template = "floor", batch = "hunt_wasser_pipe", timeoutTicks = 400)
    public static void condensateStarvesWhenPipeRefillsMain(GameTestHelper h) {
        BlockPos ctrl = h.absolutePos(new BlockPos(3, 2, 3)).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("kein Controller");
        int[] t = {0};
        h.onEachTick(() -> {
            be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false);
            if (!be.isFormed()) return;
            t[0]++;
            be.tank("water_in").fill(new FluidStack(Fluids.WATER, 640), IFluidHandler.FluidAction.EXECUTE);
            be.tank("water_in_9").fill(new FluidStack(Fluids.WATER, 4), IFluidHandler.FluidAction.EXECUTE);
            int want = 40;
            for (String o : OUTS) want -= be.tank(o).drain(want, IFluidHandler.FluidAction.EXECUTE).getAmount();
        });
        h.runAfterDelay(300, () -> {
            int cond = be.tank("water_in_9").getFluidAmount();
            if (t[0] < 200) throw new GameTestAssertException("zu wenige Ticks aufgebaut: " + t[0]);
            if (cond > 50) throw new GameTestAssertException("Kondensat nicht angenommen: water_in_9 = " + cond + " mB nach " + t[0] + " Ticks, water_in = " + be.tank("water_in").getFluidAmount());
            h.succeed();
        });
    }
}
