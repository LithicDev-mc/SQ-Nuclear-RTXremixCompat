package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BreederCore;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.power.RtgBlock;
import com.sqc.nuclearsq.power.RtgBlockEntity;
import com.sqc.nuclearsq.power.RtgCapsuleItem;
import com.sqc.nuclearsq.registry.AssemblyRegistry;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.registry.PowerRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class Plutonium238Tests {
    private Plutonium238Tests() {}

    private static final String FLOOR = "floor";

    private static Item item(String id) {
        return CatalogRegistry.ITEM_MAP.get(id).get();
    }

    @GameTest(template = FLOOR, batch = "zz_pu238_recipes", timeoutTicks = 20)
    public static void pu238ChainIsInTheDataPack(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        boolean ash = false, bred = false;
        for (var holder : rm.getRecipes()) {
            if (!(holder.value() instanceof com.sqc.nuclearsq.reprocessing.ReprocessingRecipe r)
                    || r.kind() != com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.REPROCESSING) continue;
            ash |= r.ingredient().test(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("nuclearsq:nuclear_ash_sqc")))) && r.results().stream().anyMatch(x -> x.stack().is(item("neptunium_oxide_sqc")));
            bred |= r.ingredient().test(new ItemStack(item("bred_neptunium_sqc"))) && r.results().get(0).stack().is(item("plutonium_238_oxide_sqc"));
        }
        if (!ash) throw new GameTestAssertException("Asche liefert kein Neptunium-Oxid");
        if (!bred) throw new GameTestAssertException("Erbruetetes Neptunium gibt kein Plutonium-238-Oxid");
        boolean blanket = false, capsule = false;
        for (var holder : rm.getAllRecipesFor(AssemblyRegistry.TYPE.get())) {
            ItemStack out = holder.value().getResultItem(h.getLevel().registryAccess());
            blanket |= out.is(item("neptunium_blanket_sqc"));
            capsule |= out.is(PowerRegistry.PLUTONIUM_CAPSULE.get());
        }
        if (!blanket || !capsule) throw new GameTestAssertException("Rezepte: Neptunium-Mantel " + blanket + ", Plutonium-Kapsel " + capsule);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "zz_pu238_breed", timeoutTicks = 200)
    public static void breederBreedsNeptuniumBlanket(GameTestHelper h) {
        BigMachine m = BigMachine.FAST_BREEDER;
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 4, 2)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Brueter ohne BlockEntity");
        while (be.energy().receiveEnergy(100000, false) > 0) { }
        be.tank("sodium_in").fill(new FluidStack(ModFluids.SODIUM_COOLANT.source.get(), 20000), IFluidHandler.FluidAction.EXECUTE);
        be.tank("feedwater_in").fill(new FluidStack(ModFluids.FEEDWATER.source.get(), 8000), IFluidHandler.FluidAction.EXECUTE);
        be.items().setStackInSlot(0, new ItemStack(item("uranium_core_sqc")));
        ItemStack np = new ItemStack(item("neptunium_blanket_sqc"));
        if (!BreederCore.accepts(BreederCore.BLANKET_FIRST, np) || BreederCore.accepts(0, np)) throw new GameTestAssertException("Neptunium-Mantel passt nicht auf den Mantelplatz");
        be.items().setStackInSlot(BreederCore.BLANKET_FIRST, np);
        be.breeder().setBlanketProgress(0, be.breeder().blanketTicks() - 2);
        h.runAfterDelay(60, () -> {
            if (BreederCore.outputCount(be.items()) != 1 || !be.items().getStackInSlot(BreederCore.OUT_FIRST).is(item("bred_neptunium_sqc"))) {
                throw new GameTestAssertException("Kein Erbruetetes Neptunium im Ausgang: " + be.items().getStackInSlot(BreederCore.OUT_FIRST));
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "zz_pu238_rtg", timeoutTicks = 120)
    public static void plutoniumCapsuleDoublesThePower(GameTestHelper h) {
        BlockPos sink = new BlockPos(2, 2, 3), at = new BlockPos(3, 2, 3);
        BigMachineBlockEntity bat = TestSinks.battery(h, sink, Direction.EAST);
        RtgBlock.place(h.getLevel(), h.absolutePos(at));
        if (!(h.getLevel().getBlockEntity(h.absolutePos(at)) instanceof RtgBlockEntity r)) throw new GameTestAssertException("Kein RTG");
        ItemStack cap = new ItemStack(PowerRegistry.PLUTONIUM_CAPSULE.get());
        if (!(cap.getItem() instanceof RtgCapsuleItem c) || c.stepTicks() != RtgBlockEntity.STEP_TICKS_PU238 || c.peak() != RtgBlockEntity.PEAK_PU238) {
            throw new GameTestAssertException("Kapsel-Werte stimmen nicht");
        }
        if (c.rateFor(RtgBlockEntity.HALF_LIFE_STEPS) != RtgBlockEntity.PEAK_PU238 / 2) throw new GameTestAssertException("Halbwertszeit der Pu-Kapsel");
        if (!r.insert(cap)) throw new GameTestAssertException("Kapsel passt nicht");
        h.runAfterDelay(50, () -> {
            if (r.rate() != RtgBlockEntity.PEAK_PU238) throw new GameTestAssertException("Leistung " + r.rate() + " statt " + RtgBlockEntity.PEAK_PU238);
            if (bat.energy().getEnergyStored() < RtgBlockEntity.PEAK_PU238 * 10) throw new GameTestAssertException("Akku bekam nur " + bat.energy().getEnergyStored());
            h.succeed();
        });
    }
}
