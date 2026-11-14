package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.registry.MultiblockRegistry;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;

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
public final class EfficiencyTests {
    private EfficiencyTests() {}

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

    private static void refuses(BigMachineBlockEntity be, String port, Fluid f) {
        if (be.tank(port).fill(new FluidStack(f, 100), IFluidHandler.FluidAction.SIMULATE) != 0) {
            throw new GameTestAssertException(be.machine().id + "." + port + " nimmt falsche Fluessigkeit an");
        }
    }

    private static int amount(BigMachineBlockEntity be, String port, Fluid f) {
        FluidStack fs = be.tank(port).getFluid();
        if (!fs.isEmpty() && !fs.is(f)) throw new GameTestAssertException(be.machine().id + "." + port + ": falsche Fluessigkeit " + fs.getHoverName().getString());
        return fs.getAmount();
    }

    @GameTest(template = FLOOR, batch = "eff_reheater", timeoutTicks = 100)
    public static void reheaterMakesSuperheatedSteam(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.REHEATER, new BlockPos(2, 2, 3));
        refuses(be, "steam_in", ModFluids.WET_STEAM.source.get());
        fill(be, "steam_in", ModFluids.STEAM.source.get(), 400);
        h.succeedWhen(() -> {
            if (be.tank("steam_in").getFluidAmount() > 0) throw new GameTestAssertException("Dampf noch nicht verarbeitet");
            int hot = amount(be, "steam_out", ModFluids.SUPERHEATED_STEAM.source.get());
            int water = amount(be, "water_out", Fluids.WATER);
            if (hot != 320 || water != 80) throw new GameTestAssertException("Erwartet 320 + 80 mB, ist " + hot + " + " + water);
        });
    }

    @GameTest(template = FLOOR, batch = "eff_preheater", timeoutTicks = 100)
    public static void feedwaterHeaterUsesExhaust(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FEEDWATER_HEATER, new BlockPos(2, 2, 3));
        refuses(be, "exhaust_in", ModFluids.STEAM.source.get());
        fill(be, "feedwater_in", ModFluids.FEEDWATER.source.get(), 400);
        fill(be, "exhaust_in", ModFluids.EXHAUST_STEAM.source.get(), 100);
        h.succeedWhen(() -> {
            int warm = amount(be, "feedwater_out", ModFluids.PREHEATED_FEEDWATER.source.get());
            int water = amount(be, "water_out", Fluids.WATER);
            if (warm != 200 || water != 100) throw new GameTestAssertException("Erwartet 200 + 100 mB, ist " + warm + " + " + water);
            if (be.tank("feedwater_in").getFluidAmount() != 200) throw new GameTestAssertException("Ohne Abdampf weitergelaufen");
        });
    }

    @GameTest(template = FLOOR, batch = "eff_turbine", timeoutTicks = 20)
    public static void turbineGetsMoreFromSuperheatedSteam(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 2, 4);
        h.setBlock(p, MultiblockRegistry.TURBINE_CONTROLLER.get());
        TurbineControllerBlockEntity t = h.getBlockEntity(p);
        t.steam().fill(new FluidStack(ModFluids.STEAM.source.get(), 100), IFluidHandler.FluidAction.EXECUTE);
        if (t.fePerMb() != TurbineControllerBlockEntity.FE_PER_MB) throw new GameTestAssertException("Dampf: " + t.fePerMb());
        t.steam().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        if (t.steam().fill(new FluidStack(ModFluids.SUPERHEATED_STEAM.source.get(), 100), IFluidHandler.FluidAction.EXECUTE) != 100) {
            throw new GameTestAssertException("Turbine nimmt keinen ueberhitzten Dampf");
        }
        if (t.fePerMb() != TurbineControllerBlockEntity.SUPER_FE_PER_MB) throw new GameTestAssertException("Ueberhitzt: " + t.fePerMb());
        h.succeed();
    }
}
