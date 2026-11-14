package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class CoolingPlantTests {
    private CoolingPlantTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, boolean power) {
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 2, 3)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein Controller");
        if (power) be.energy().setStored(m.energy.capacity());
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

    @GameTest(template = FLOOR, batch = "plant_purifier", timeoutTicks = 100)
    public static void purifierCleansSteam(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.STEAM_PURIFIER, true);
        refuses(be, "steam_in", ModFluids.WET_STEAM.source.get());
        fill(be, "steam_in", ModFluids.CONTAMINATED_STEAM.source.get(), 640);
        h.succeedWhen(() -> {
            if (!be.isFormed()) throw new GameTestAssertException("nicht aufgebaut");
            int out = amount(be, "steam_out", ModFluids.STEAM.source.get());
            if (out != 640) throw new GameTestAssertException("Erwartet 640 mB Dampf, ist " + out);
        });
    }

    @GameTest(template = FLOOR, batch = "plant_purifier_clean", timeoutTicks = 100)
    public static void purifierPassesCleanSteam(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.STEAM_PURIFIER, false);
        fill(be, "steam_in", ModFluids.STEAM.source.get(), 640);
        h.runAfterDelay(40, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("nicht aufgebaut");
            if (amount(be, "steam_out", ModFluids.STEAM.source.get()) != 640) throw new GameTestAssertException("Sauberer Dampf geht nicht durch");
            if (be.residue() != 0) throw new GameTestAssertException("Rueckstand aus sauberem Dampf");
            be.tank("steam_out").drain(100000, IFluidHandler.FluidAction.EXECUTE);
            fill(be, "steam_in", ModFluids.CONTAMINATED_STEAM.source.get(), 320);
        });
        h.runAfterDelay(80, () -> {
            if (be.tank("steam_in").getFluidAmount() != 320) throw new GameTestAssertException("Verunreinigter Dampf ohne Strom gereinigt");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "plant_purifier_full", timeoutTicks = 100)
    public static void purifierStopsWhenResidueFull(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.STEAM_PURIFIER, true);
        int max = BigMachine.STEAM_PURIFIER.extra("residue_max");
        be.items().setStackInSlot(0, new ItemStack(CatalogRegistry.ITEM_MAP.get("filter_residue_sqc").get(), max));
        fill(be, "steam_in", ModFluids.CONTAMINATED_STEAM.source.get(), 320);
        h.runAfterDelay(30, () -> {
            if (!be.residueFull()) h.fail("Bleibehaelter sollte voll sein, ist " + be.residue());
            if (be.tank("steam_in").getFluidAmount() != 320) h.fail("Lief trotz vollem Bleibehaelter");
            be.items().setStackInSlot(0, ItemStack.EMPTY);
            h.succeedWhen(() -> {
                if (amount(be, "steam_out", ModFluids.STEAM.source.get()) != 320) throw new GameTestAssertException("Nach dem Leeren nicht weitergelaufen");
            });
        });
    }

    @GameTest(template = FLOOR, batch = "plant_condenser", timeoutTicks = 100)
    public static void condenserHeatsCoolant(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.CONDENSER, true);
        refuses(be, "exhaust_in", ModFluids.STEAM.source.get());
        refuses(be, "coolant_in", ModFluids.HOT_COOLANT.source.get());
        fill(be, "coolant_in", ModFluids.COOLANT.source.get(), 300);
        fill(be, "exhaust_in", ModFluids.EXHAUST_STEAM.source.get(), 400);
        h.succeedWhen(() -> {
            int water = amount(be, "water_out", Fluids.WATER);
            int hot = amount(be, "coolant_out", ModFluids.HOT_COOLANT.source.get());
            if (water != 400 || hot != 300) throw new GameTestAssertException("Erwartet 400 mB Wasser + 300 mB heisses Kuehlmittel, ist " + water + " + " + hot);
        });
    }

    @GameTest(template = FLOOR, batch = "plant_condenser_sodium", timeoutTicks = 100)
    public static void condenserSodiumCoolsDouble(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.CONDENSER, true);
        fill(be, "coolant_in", ModFluids.SODIUM_COOLANT.source.get(), 200);
        fill(be, "exhaust_in", ModFluids.EXHAUST_STEAM.source.get(), 400);
        h.succeedWhen(() -> {
            int water = amount(be, "water_out", Fluids.WATER);
            int hot = amount(be, "coolant_out", ModFluids.HOT_COOLANT.source.get());
            int left = amount(be, "coolant_in", ModFluids.SODIUM_COOLANT.source.get());
            if (water != 400 || hot != 200 || left != 0) throw new GameTestAssertException("Natrium: erwartet 400 Wasser, 200 heiss, 0 uebrig, ist " + water + ", " + hot + ", " + left);
        });
    }

    @GameTest(template = FLOOR, batch = "plant_condenser_cryo", timeoutTicks = 100)
    public static void condenserCryoCoolsFourfold(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.CONDENSER, true);
        fill(be, "coolant_in", ModFluids.CRYO_COOLANT.source.get(), 1000);
        fill(be, "exhaust_in", ModFluids.EXHAUST_STEAM.source.get(), 400);
        h.succeedWhen(() -> {
            int water = amount(be, "water_out", Fluids.WATER);
            int hot = amount(be, "coolant_out", ModFluids.HOT_COOLANT.source.get());
            int left = amount(be, "coolant_in", ModFluids.CRYO_COOLANT.source.get());
            if (water != 400 || hot != 100 || left != 900) throw new GameTestAssertException("Kryo: erwartet 400 Wasser, 100 heiss, 900 uebrig, ist " + water + ", " + hot + ", " + left);
        });
    }

    @GameTest(template = FLOOR, batch = "plant_treatment", timeoutTicks = 100)
    public static void waterTreatmentNeedsPower(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.WATER_TREATMENT, false);
        fill(be, "water_in", Fluids.WATER, 500);
        h.runAfterDelay(25, () -> {
            if (be.tank("water_in").getFluidAmount() != 500) h.fail("Lief ohne Strom");
            be.energy().setStored(BigMachine.WATER_TREATMENT.energy.capacity());
            h.succeedWhen(() -> {
                int out = amount(be, "feedwater_out", ModFluids.FEEDWATER.source.get());
                if (out != 500) throw new GameTestAssertException("Erwartet 500 mB Speisewasser, ist " + out);
            });
        });
    }

    @GameTest(template = FLOOR, batch = "plant_fwpump", timeoutTicks = 100)
    public static void feedwaterPumpKeepsKind(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FEEDWATER_PUMP, true);
        refuses(be, "feedwater_in", Fluids.WATER);
        fill(be, "feedwater_in", ModFluids.PREHEATED_FEEDWATER.source.get(), 720);
        h.succeedWhen(() -> {
            int out = amount(be, "feedwater_out", ModFluids.PREHEATED_FEEDWATER.source.get());
            if (out != 720) throw new GameTestAssertException("Erwartet 720 mB vorgewaermtes Speisewasser, ist " + out);
        });
    }
}
