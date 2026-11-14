package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class AcidPlantTests {
    private AcidPlantTests() {}

    private static final String FLOOR = "floor";
    private static final TagKey<Item> SULFUR = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:dusts/sulfur"));

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m) {
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 4, 2)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein BlockEntity");
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, Fluid f, int mb) {
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(be.machine().id + "." + port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static void formed(BigMachineBlockEntity be) {
        if (!be.isFormed()) throw new GameTestAssertException(be.machine().id + " nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
    }

    private static Item item(String id) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
        if (it == Items.AIR) throw new GameTestAssertException("Item " + id + " nicht registriert");
        return it;
    }

    private static int out(BigMachineBlockEntity be, java.util.function.Predicate<ItemStack> p) {
        int n = 0;
        for (int i = 1; i <= 4; i++) if (p.test(be.items().getStackInSlot(i))) n += be.items().getStackInSlot(i).getCount();
        return n;
    }

    @GameTest(template = FLOOR, batch = "acid_plant", timeoutTicks = 500)
    public static void acidPlantRoastsOre(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.ACID_PLANT);
        var in = be.itemsAt("item_in");
        if (in.insertItem(0, new ItemStack(Items.DIRT), true).isEmpty()) throw new GameTestAssertException("Anlage nimmt Erde an");
        if (!in.insertItem(0, new ItemStack(Items.RAW_IRON, 4), false).isEmpty()) throw new GameTestAssertException("Roheisen passt nicht hinein");
        h.runAfterDelay(320, () -> {
            formed(be);
            int sulfur = out(be, s -> s.is(SULFUR)), dust = out(be, s -> s.is(item("iron_dust_sqc")));
            if (sulfur != 3 || dust != 2) throw new GameTestAssertException("Erwartet 3 Schwefel + 2 Eisenstaub, ist " + sulfur + " + " + dust + " (Zustand " + be.crafter().status() + ")");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "acid_plant", timeoutTicks = 300)
    public static void acidPlantMakesSulfuricAcid(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.ACID_PLANT);
        fill(be, "water_in", Fluids.WATER, 1000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("sulfur_dust_sqc")), false);
        h.runAfterDelay(200, () -> {
            formed(be);
            if (be.tank("acid_out").getFluidAmount() != 500 || !be.tank("acid_out").getFluid().is(ModFluids.SULFURIC_ACID.source.get())) {
                throw new GameTestAssertException("Erwartet 500 mB Schwefelsaeure, ist " + be.tank("acid_out").getFluid() + " (Zustand " + be.crafter().status() + ")");
            }
            if (be.tank("water_in").getFluidAmount() != 600 || !be.items().getStackInSlot(0).isEmpty()) throw new GameTestAssertException("Wasser oder Eingang falsch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "acid_plant", timeoutTicks = 300)
    public static void acidPlantMakesHydrofluoricAcid(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.ACID_PLANT);
        fill(be, "acid_out", ModFluids.SULFURIC_ACID.source.get(), 500);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("fluorite_gem_sqc")), false);
        h.runAfterDelay(240, () -> {
            formed(be);
            if (be.tank("hf_out").getFluidAmount() != 500 || !be.tank("hf_out").getFluid().is(ModFluids.HYDROFLUORIC_ACID.source.get())) {
                throw new GameTestAssertException("Erwartet 500 mB Flusssaeure, ist " + be.tank("hf_out").getFluid() + " (Zustand " + be.crafter().status() + ")");
            }
            if (be.tank("acid_out").getFluidAmount() != 250) throw new GameTestAssertException("Schwefelsaeure " + be.tank("acid_out").getFluidAmount() + " statt 250");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "acid_plant", timeoutTicks = 200)
    public static void acidPlantStopsWhenTankIsFull(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.ACID_PLANT);
        if (be.tank("acid_out").fill(new FluidStack(ModFluids.HYDROFLUORIC_ACID.source.get(), 100), IFluidHandler.FluidAction.SIMULATE) != 0
                || be.tank("hf_out").fill(new FluidStack(ModFluids.SULFURIC_ACID.source.get(), 100), IFluidHandler.FluidAction.SIMULATE) != 0) {
            throw new GameTestAssertException("Ein Saeure-Tank nimmt die falsche Saeure an");
        }
        fill(be, "water_in", Fluids.WATER, 1000);
        fill(be, "acid_out", ModFluids.SULFURIC_ACID.source.get(), be.tank("acid_out").getCapacity());
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("sulfur_dust_sqc")), false);
        h.runAfterDelay(60, () -> {
            formed(be);
            if (be.crafter().status() != BigCrafter.OUTPUT_FULL) throw new GameTestAssertException("Zustand " + be.crafter().status() + " statt AUSGANG VOLL");
            if (be.items().getStackInSlot(0).getCount() != 1 || be.tank("water_in").getFluidAmount() != 1000) throw new GameTestAssertException("Es wurde etwas verbraucht");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "acid_plant", timeoutTicks = 500)
    public static void uf6UsesHydrofluoricAcid(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.UF6_CONVERTER);
        fill(be, "hf_in", ModFluids.HYDROFLUORIC_ACID.source.get(), 600);
        be.itemsAt("item_in").insertItem(0, new ItemStack(ReactorRegistry.URANIUM_OXIDE.get(), 2), false);
        h.runAfterDelay(380, () -> {
            formed(be);
            if (out(be, s -> s.is(ReactorRegistry.UF6_CYLINDER.get())) != 1) throw new GameTestAssertException("Erwartet 1 UF6-Zylinder, Zustand " + be.crafter().status());
            if (be.tank("hf_in").getFluidAmount() != 300) throw new GameTestAssertException("Flusssaeure " + be.tank("hf_in").getFluidAmount() + " statt 300");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "acid_plant_data", timeoutTicks = 20)
    public static void acidPlantIsInTheDataPack(GameTestHelper h) {
        int roast = 0;
        boolean contact = false, hf = false, uf6Hf = false;
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ReprocessingRecipe r)) continue;
            if (r.kind() == ReprocessingRecipe.Kind.ACID_PLANT) {
                if (!r.results().isEmpty()) roast++;
                contact |= r.fluidResults().stream().anyMatch(o -> o.port().equals("acid_out")) && r.fluids().stream().anyMatch(c -> c.port().equals("water_in"));
                hf |= r.fluidResults().stream().anyMatch(o -> o.port().equals("hf_out")) && r.fluids().stream().anyMatch(c -> c.port().equals("acid_out"));
            } else if (r.kind() == ReprocessingRecipe.Kind.UF6_CONVERSION) {
                uf6Hf |= r.fluids().stream().anyMatch(c -> c.port().equals("hf_in")) && !r.hasAux();
            }
        }
        if (roast < 3) throw new GameTestAssertException("Roestrezepte: " + roast);
        if (!contact) throw new GameTestAssertException("Kontaktverfahren fehlt");
        if (!hf) throw new GameTestAssertException("Flusssaeure-Rezept fehlt");
        if (!uf6Hf) throw new GameTestAssertException("UF6-Rezept mit Flusssaeure fehlt");
        for (String id : new String[] {"acid_plant_kit_sqc", "acid_plant_controller_sqc", "sulfur_dust_sqc"}) item(id);
        if (BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "hydrofluoric_acid")) == Fluids.EMPTY) {
            throw new GameTestAssertException("Flusssaeure nicht registriert");
        }
        h.succeed();
    }
}
