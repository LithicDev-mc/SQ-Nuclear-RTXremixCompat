package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class HuntVorwaermerTests {
    private HuntVorwaermerTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein Controller");
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, Fluid f, int mb) {
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(be.machine().id + "." + port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static int anyFeedwaterOut(BigMachineBlockEntity be) {
        FluidStack fs = be.tank("feedwater_out").getFluid();
        if (fs.isEmpty()) return 0;
        if (!fs.is(ModFluids.FEEDWATER.source.get()) && !fs.is(ModFluids.PREHEATED_FEEDWATER.source.get())) {
            throw new GameTestAssertException("feedwater_out: falsche Fluessigkeit " + fs.getHoverName().getString());
        }
        return fs.getAmount();
    }

    @GameTest(template = FLOOR, batch = "hunt_preheater", timeoutTicks = 100)
    public static void heaterPassesFeedwaterWithoutExhaust(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FEEDWATER_HEATER, new BlockPos(2, 2, 3));
        fill(be, "feedwater_in", ModFluids.FEEDWATER.source.get(), 400);
        h.runAfterDelay(40, () -> {
            int out = anyFeedwaterOut(be);
            if (out != 400) throw new GameTestAssertException("Ohne Abdampf kam " + out + " statt 400 mB Speisewasser an (Kaltstart blockiert)");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "hunt_preheater", timeoutTicks = 100)
    public static void heaterPassesFeedwaterWithFullCondensate(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FEEDWATER_HEATER, new BlockPos(2, 2, 3));
        int cap = be.tank("water_out").getCapacity();
        fill(be, "water_out", Fluids.WATER, cap);
        fill(be, "feedwater_in", ModFluids.FEEDWATER.source.get(), 400);
        fill(be, "exhaust_in", ModFluids.EXHAUST_STEAM.source.get(), 200);
        h.runAfterDelay(40, () -> {
            int out = anyFeedwaterOut(be);
            if (out != 400) throw new GameTestAssertException("Mit vollem Kondensattank kam " + out + " statt 400 mB Speisewasser an");
            h.succeed();
        });
    }
}
