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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class WaterTreatmentTests {
    private WaterTreatmentTests() {}

    private static final BigMachine M = BigMachine.WATER_TREATMENT;

    @GameTest(template = "floor", batch = "water_treatment_both", timeoutTicks = 200)
    public static void bothSidesInAndFourOutputs(GameTestHelper h) {
        BlockPos ctrl = h.absolutePos(new BlockPos(3, 2, 3)).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("kein Controller");
        be.energy().setStored(M.energy.capacity());
        h.runAfterDelay(10, () -> {
            for (int i = 5; i <= 8; i++) be.tank("water_in_" + i).fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
            h.succeedWhen(() -> {
                if (!be.isFormed()) throw new GameTestAssertException("nicht aufgebaut");
                for (String out : new String[] {"feedwater_out", "feedwater_out_2", "feedwater_out_3", "feedwater_out_4"}) {
                    if (be.tank(out) == null || be.tank(out).getFluidAmount() <= 0) throw new GameTestAssertException(out + " leer");
                }
            });
        });
    }

    @GameTest(template = "floor", batch = "water_treatment_pump", timeoutTicks = 300)
    public static void treatmentTakesWaterDirectlyFromPump(GameTestHelper h) {
        BigMachine pump = BigMachine.INTAKE_PUMP;
        BlockPos pc = h.absolutePos(new BlockPos(3, 6, 3)).subtract(pump.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (int x : new int[] {2, 3, 5, 6}) for (int z : new int[] {1, 2}) {
            h.getLevel().setBlockAndUpdate(pump.structure.world(pc, 0, x, 0, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        }
        for (MultiblockStructure.Cell c : pump.structure.cells(pc, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(pc) instanceof BigMachineBlockEntity pbe)) throw new GameTestAssertException("keine Pumpe");
        pbe.energy().setStored(pump.energy.capacity());
        java.util.Set<BlockPos> want = new java.util.HashSet<>();
        for (int x = 0; x <= 1; x++) for (int y = 5; y <= 6; y++) want.add(pump.structure.world(pc, 0, x, y, 9));
        BigMachineBlockEntity[] tbe = new BigMachineBlockEntity[1];
        for (int rot = 0; rot < 4 && tbe[0] == null; rot++) {
            BlockPos tc = pump.structure.world(pc, 0, 0, 5, 9).subtract(M.structure.world(BlockPos.ZERO, rot, 0, 1, 2));
            java.util.Set<BlockPos> in = new java.util.HashSet<>();
            for (int y = 1; y <= 2; y++) for (int z = 2; z <= 3; z++) in.add(M.structure.world(tc, rot, 0, y, z));
            int minX = in.stream().mapToInt(BlockPos::getX).min().getAsInt();
            tc = tc.east(pump.structure.world(pc, 0, 0, 0, 9).getX() - minX);
            in.clear();
            for (int y = 1; y <= 2; y++) for (int z = 2; z <= 3; z++) in.add(M.structure.world(tc, rot, 0, y, z));
            boolean ok = in.equals(want);
            for (MultiblockStructure.Cell c : M.structure.cells(tc, rot)) ok &= c.pos().getZ() >= pump.structure.world(pc, 0, 0, 0, 9).getZ();
            if (!ok) continue;
            for (MultiblockStructure.Cell c : M.structure.cells(tc, rot)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
            if (h.getLevel().getBlockEntity(tc) instanceof BigMachineBlockEntity b) tbe[0] = b;
        }
        if (tbe[0] == null) throw new GameTestAssertException("keine passende Drehung");
        tbe[0].energy().setStored(M.energy.capacity());
        h.succeedWhen(() -> {
            if (!tbe[0].isFormed() || !pbe.isFormed()) throw new GameTestAssertException("nicht aufgebaut");
            if (tbe[0].tank("feedwater_out").getFluidAmount() <= 0) throw new GameTestAssertException("kein Speisewasser");
        });
    }

    @GameTest(template = "floor", batch = "water_treatment_top", timeoutTicks = 200)
    public static void topSmallPipeInputs(GameTestHelper h) {
        BlockPos ctrl = h.absolutePos(new BlockPos(3, 2, 3)).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("kein Controller");
        if (be.tank("water_in_9") == null || be.tank("water_in_10") == null) throw new GameTestAssertException("Eingaenge oben fehlen");
        be.energy().setStored(M.energy.capacity());
        h.runAfterDelay(10, () -> {
            for (int i = 9; i <= 10; i++) be.tank("water_in_" + i).fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
            h.succeedWhen(() -> {
                if (!be.isFormed()) throw new GameTestAssertException("nicht aufgebaut");
                for (String out : new String[] {"feedwater_out", "feedwater_out_2", "feedwater_out_3", "feedwater_out_4"}) {
                    if (be.tank(out) == null || be.tank(out).getFluidAmount() <= 0) throw new GameTestAssertException(out + " leer");
                }
            });
        });
    }

    @GameTest(template = "floor", batch = "water_treatment_condensate_first", timeoutTicks = 200)
    public static void condensateBeforePumpWater(GameTestHelper h) {
        BlockPos ctrl = h.absolutePos(new BlockPos(3, 2, 3)).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("kein Controller");
        be.energy().setStored(M.energy.capacity());
        h.runAfterDelay(10, () -> {
            be.tank("water_in_2").fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
            be.tank("water_in_9").fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
            h.runAfterDelay(5, () -> {
                if (be.tank("water_in_9").getFluidAmount() >= be.tank("water_in_2").getFluidAmount())
                    throw new GameTestAssertException("Pumpenwasser zuerst verbraucht: Kondensat " + be.tank("water_in_9").getFluidAmount() + ", Pumpe " + be.tank("water_in_2").getFluidAmount());
                h.succeed();
            });
        });
    }
}
