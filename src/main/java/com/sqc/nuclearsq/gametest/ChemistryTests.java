package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.CoreChemistry;
import com.sqc.nuclearsq.machine.big.DriveCore;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.reactor.RecombinerBlockEntity;
import com.sqc.nuclearsq.reactor.XenonFilterBlockEntity;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ChemistryTests {
    private ChemistryTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity buildDrive(GameTestHelper h) {
        BigMachine m = BigMachine.CONTROL_ROD_DRIVE;
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 4, 2)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Antrieb ohne BlockEntity");
        return be;
    }

    @GameTest(template = FLOOR, batch = "chemistry_gas", timeoutTicks = 400)
    public static void recombinersCountAndRemoveGas(GameTestHelper h) {
        BigMachineBlockEntity drive = buildDrive(h);
        DriveCore core = drive.core();
        BlockPos at = h.absolutePos(new BlockPos(8, 2, 2));
        h.getLevel().setBlockAndUpdate(at, ReactorRegistry.RECOMBINER.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(at.east(), ReactorRegistry.RECOMBINER.get().defaultBlockState());
        if (RecombinerBlockEntity.count(h.getLevel(), at, CoreChemistry.RECOMBINER_RANGE) < 2) throw new GameTestAssertException("Rekombinatoren nicht gezaehlt");
        core.chem().set(60f, 0f);
        h.runAfterDelay(100, () -> {
            if (core.chem().recombiners() < 2) throw new GameTestAssertException("Kern zaehlt " + core.chem().recombiners() + " Rekombinatoren");
            if (core.chem().gas() >= 60f) throw new GameTestAssertException("Gas sinkt nicht: " + core.chem().gas());
            h.getLevel().setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(at.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            if (RecombinerBlockEntity.count(h.getLevel(), at, CoreChemistry.RECOMBINER_RANGE) != 0) throw new GameTestAssertException("Abgebaute Rekombinatoren zaehlen noch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemistry_xenon", timeoutTicks = 100)
    public static void xenonPoisoningStopsAndResumes(GameTestHelper h) {
        CoreChemistry c = new CoreChemistry();
        if (c.powerFactor() != 1f) throw new GameTestAssertException("Frischer Kern gedrosselt");
        c.set(0f, 30f);
        if (c.powerFactor() != 1f) throw new GameTestAssertException("Bei 30 % schon gedrosselt");
        c.set(0f, 65f);
        float mid = c.powerFactor();
        if (mid >= 1f || mid <= 0.6f) throw new GameTestAssertException("Faktor bei 65 %: " + mid);
        c.set(0f, 100f);
        if (!c.poisoned() || c.powerFactor() != 0f) throw new GameTestAssertException("Bei 100 % steht der Reaktor nicht");
        c.set(0f, 80f);
        if (!c.poisoned()) throw new GameTestAssertException("Zwischen 60 und 100 % laeuft er zu frueh wieder an");
        c.set(0f, 59f);
        if (c.poisoned() || c.powerFactor() <= 0f) throw new GameTestAssertException("Unter 60 % kein Wiederanlauf");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "chemistry_ignite", timeoutTicks = 40)
    public static void gasIgnitesHullFirstThenRoom(GameTestHelper h) {
        CoreChemistry c = new CoreChemistry();
        BlockPos p = h.absolutePos(new BlockPos(2, 250, 2));
        net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(p);
        c.set(100f, 0f);
        if (c.tick(h.getLevel(), at, 1, 0f, 1f)) throw new GameTestAssertException("Erste Zuendung sprengt schon den Raum");
        if (!c.hullDamaged()) throw new GameTestAssertException("Huelle nicht beschaedigt");
        if (Math.abs(c.gas() - CoreChemistry.GAS_AFTER_BLAST) > 1f) throw new GameTestAssertException("Gas nach der ersten Zuendung: " + c.gas());
        c.set(100f, 0f);
        if (!c.tick(h.getLevel(), at, 2, 0f, 1f)) throw new GameTestAssertException("Zweite Zuendung sprengt den Raum nicht");
        if (c.hullDamaged() || c.gas() > 1f) throw new GameTestAssertException("Nach der Sprengung: Huelle " + c.hullDamaged() + ", Gas " + c.gas());
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "chemistry_filter", timeoutTicks = 200)
    public static void xenonFilterBlockNeedsPowerAndFilter(GameTestHelper h) {
        BigMachineBlockEntity drive = buildDrive(h);
        BlockPos at = h.absolutePos(new BlockPos(8, 2, 2));
        h.getLevel().setBlockAndUpdate(at, ReactorRegistry.XENON_FILTER.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(at) instanceof XenonFilterBlockEntity f)) throw new GameTestAssertException("Xenon-Filter ohne BlockEntity");
        ItemStack rest = f.items().insertItem(0, new ItemStack(CatalogRegistry.ITEM_MAP.get("carbon_filter_sqc").get(), 6), false);
        if (f.filters() != XenonFilterBlockEntity.SLOTS || rest.getCount() != 2) throw new GameTestAssertException("Filter " + f.filters() + ", Rest " + rest.getCount());
        drive.core().chem().set(0f, 50f);
        h.runAfterDelay(60, () -> {
            if (drive.core().chem().filters() != 0) throw new GameTestAssertException("Filtert ohne Strom");
            f.energy().add(XenonFilterBlockEntity.FE_PER_TICK * 40);
            h.runAfterDelay(30, () -> {
                if (drive.core().chem().filters() != 1) throw new GameTestAssertException("Kern zaehlt " + drive.core().chem().filters() + " Filter");
                if (f.energy().getEnergyStored() >= XenonFilterBlockEntity.FE_PER_TICK * 40) throw new GameTestAssertException("Kein Strom verbraucht");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "zz_chemistry_mox", timeoutTicks = 200)
    public static void moxRodsAreDetectedAndCarryTheirComponent(GameTestHelper h) {
        BigMachineBlockEntity drive = buildDrive(h);
        for (int k = 0; k < 9; k++) {
            BlockPos head = drive.rodBase(drive.shafts()[k]).above(9);
            for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
                BlockPos p = head.below(i);
                h.getLevel().setBlockAndUpdate(p, ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
                if (!(h.getLevel().getBlockEntity(p) instanceof FuelRodBlockEntity f)) throw new GameTestAssertException("Stab ohne BlockEntity");
                f.setMox(true);
                if (f.collectComponents().get(com.sqc.nuclearsq.registry.ModDataComponents.FUEL_MOX.get()) == null) {
                    throw new GameTestAssertException("MOX-Komponente fehlt beim Abbau");
                }
            }
            h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
        while (drive.energy().receiveEnergy(1000, false) > 0) { }
        drive.tank("hydraulic_in").fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        h.onEachTick(() -> {
            while (drive.energy().receiveEnergy(1000, false) > 0) { }
            drive.addPrimaryFlow(4000);
            net.neoforged.neoforge.fluids.capability.templates.FluidTank in = drive.tank("primary_cold_in");
            in.fill(new net.neoforged.neoforge.fluids.FluidStack(com.sqc.nuclearsq.registry.ModFluids.PRIMARY_WATER.source.get(), in.getSpace()),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            net.neoforged.neoforge.fluids.capability.templates.FluidTank out = drive.tank("primary_hot_out");
            out.drain(out.getFluidAmount(), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(25, () -> {
            for (int k = 0; k < 9; k++) drive.setShaftLocked(k, true);
            drive.setRodTarget(15);
        });
        h.runAfterDelay(160, () -> {
            if (drive.moxShare() < 0.99f) throw new GameTestAssertException("MOX-Anteil " + drive.moxShare());
            drive.scram();
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemistry_recipes", timeoutTicks = 20)
    public static void moxRecipesExist(GameTestHelper h) {
        boolean core = false, recombiner = false, filter = false;
        int mox = 0;
        for (var holder : h.getLevel().getRecipeManager().getAllRecipesFor(com.sqc.nuclearsq.registry.AssemblyRegistry.TYPE.get())) {
            ItemStack out = holder.value().getResultItem(h.getLevel().registryAccess());
            if (out.is(CatalogRegistry.ITEM_MAP.get("mox_core_sqc").get())) core = true;
            if (out.is(ReactorRegistry.RECOMBINER_ITEM.get())) recombiner = true;
            if (out.is(CatalogRegistry.ITEM_MAP.get("carbon_filter_sqc").get())) filter = true;
            if (out.has(com.sqc.nuclearsq.registry.ModDataComponents.FUEL_MOX.get())) mox++;
        }
        if (!core || !recombiner || !filter || mox != 4) {
            throw new GameTestAssertException("Rezepte: MOX-Kern " + core + ", Rekombinator " + recombiner + ", Filter " + filter + ", MOX-Huellen " + mox + "/4");
        }
        h.succeed();
    }
}
