package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class UraniumChainTests {
    private UraniumChainTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m) {
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 4, 2)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein BlockEntity");
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, net.minecraft.world.level.material.Fluid f, int mb) {
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(be.machine().id + "." + port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static void feedPower(GameTestHelper h, BigMachineBlockEntity be) {
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
    }

    private static void formed(BigMachineBlockEntity be) {
        if (!be.isFormed()) throw new GameTestAssertException(be.machine().id + " nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
    }

    private static Item item(String id) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
        if (it == net.minecraft.world.item.Items.AIR) throw new GameTestAssertException("Item " + id + " nicht registriert");
        return it;
    }

    private static int count(BigMachineBlockEntity be, Item it) {
        int n = 0;
        for (int i = 1; i <= 4; i++) if (be.items().getStackInSlot(i).is(it)) n += be.items().getStackInSlot(i).getCount();
        return n;
    }

    private static BigMachineBlockEntity leaching(GameTestHelper h, int water, int acid) {
        BigMachineBlockEntity be = build(h, BigMachine.LEACHING_PLANT);
        feedPower(h, be);
        if (water > 0) fill(be, "water_in", Fluids.WATER, water);
        if (acid > 0) fill(be, "acid_in", ModFluids.SULFURIC_ACID.source.get(), acid);
        return be;
    }

    @GameTest(template = FLOOR, batch = "uranium_chain", timeoutTicks = 400)
    public static void leachingMakesOxide(GameTestHelper h) {
        BigMachineBlockEntity be = leaching(h, 4000, 2000);
        var in = be.itemsAt("item_in");
        if (in.insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIRT), true).isEmpty()) throw new GameTestAssertException("Anlage nimmt fremde Items an");
        if (!in.insertItem(0, new ItemStack(ReactorRegistry.YELLOWCAKE.get(), 2), false).isEmpty()) throw new GameTestAssertException("Yellowcake passt nicht hinein");
        h.runAfterDelay(300, () -> {
            formed(be);
            ItemStack left = be.items().getStackInSlot(0);
            if (count(be, ReactorRegistry.URANIUM_OXIDE.get()) != 2 || !(left.isEmpty() || left.is(ReactorRegistry.URANIUM_SLUDGE.get()))) {
                throw new GameTestAssertException("Erwartet 2 Uranoxid, ist " + count(be, ReactorRegistry.URANIUM_OXIDE.get()) + " (Eingang "
                        + be.items().getStackInSlot(0) + ", Zustand " + be.crafter().status() + ")");
            }
            if (be.tank("acid_in").getFluidAmount() != 1750 || be.tank("water_in").getFluidAmount() != 3500) {
                throw new GameTestAssertException("Tanks: Saeure " + be.tank("acid_in").getFluidAmount() + " (1750), Wasser " + be.tank("water_in").getFluidAmount() + " (3500)");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_chain", timeoutTicks = 600)
    public static void leachingKeepsSludgeOutOfOutput(GameTestHelper h) {
        BigMachineBlockEntity be = leaching(h, 4000, 2000);
        be.items().setStackInSlot(1, new ItemStack(ReactorRegistry.URANIUM_SLUDGE.get(), 3));
        be.items().setStackInSlot(2, new ItemStack(ReactorRegistry.URANIUM_OXIDE.get(), 5));
        var out = be.itemsAt("item_out");
        if (!out.extractItem(1, 64, true).isEmpty()) throw new GameTestAssertException("Schlamm geht in die Ausgangsleitung");
        if (out.extractItem(2, 64, true).getCount() != 5) throw new GameTestAssertException("Uranoxid nicht abholbar");
        h.runAfterDelay(400, () -> {
            formed(be);
            if (count(be, ReactorRegistry.URANIUM_SLUDGE.get()) != 0 || !be.items().getStackInSlot(0).isEmpty()) {
                throw new GameTestAssertException("Schlamm nicht verarbeitet (Eingang " + be.items().getStackInSlot(0) + ", Ausgaenge " + count(be, ReactorRegistry.URANIUM_SLUDGE.get()) + ")");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_chain", timeoutTicks = 200)
    public static void leachingNeedsAcid(GameTestHelper h) {
        BigMachineBlockEntity be = leaching(h, 4000, 0);
        if (be.tank("acid_in").fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE) != 0) {
            throw new GameTestAssertException("Saeure-Tank nimmt Wasser an");
        }
        be.itemsAt("item_in").insertItem(0, new ItemStack(ReactorRegistry.YELLOWCAKE.get(), 2), false);
        h.runAfterDelay(80, () -> {
            formed(be);
            if (be.crafter().status() != BigCrafter.NO_ACID) throw new GameTestAssertException("Zustand " + be.crafter().status() + " statt KEINE SAEURE");
            if (be.items().getStackInSlot(0).getCount() != 2 || count(be, ReactorRegistry.URANIUM_OXIDE.get()) != 0) {
                throw new GameTestAssertException("Eingang " + be.items().getStackInSlot(0));
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_chain", timeoutTicks = 400)
    public static void leachingReprocessesSludge(GameTestHelper h) {
        BigMachineBlockEntity be = leaching(h, 1000, 1000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(ReactorRegistry.URANIUM_SLUDGE.get(), 3), false);
        h.runAfterDelay(260, () -> {
            formed(be);
            if (count(be, ReactorRegistry.URANIUM_OXIDE.get()) != 1) throw new GameTestAssertException("Erwartet 1 Uranoxid, Zustand " + be.crafter().status());
            if (be.tank("acid_in").getFluidAmount() != 900 || be.tank("water_in").getFluidAmount() != 800) throw new GameTestAssertException("Tankinhalt falsch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_chain", timeoutTicks = 500)
    public static void uf6ConvertsOxide(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.UF6_CONVERTER);
        feedPower(h, be);
        var in = be.itemsAt("item_in");
        if (!in.insertItem(5, new ItemStack(item("fluorite_gem_sqc")), false).isEmpty()) throw new GameTestAssertException("Zusatzplatz nimmt kein Fluorit");
        if (in.insertItem(5, new ItemStack(net.minecraft.world.item.Items.DIRT), true).isEmpty()) throw new GameTestAssertException("Zusatzplatz nimmt Erde an");
        if (!in.insertItem(0, new ItemStack(ReactorRegistry.URANIUM_OXIDE.get(), 2), false).isEmpty()) throw new GameTestAssertException("Uranoxid passt nicht hinein");
        h.runAfterDelay(380, () -> {
            formed(be);
            if (count(be, ReactorRegistry.UF6_CYLINDER.get()) != 1) throw new GameTestAssertException("Erwartet 1 UF6-Zylinder, Zustand " + be.crafter().status());
            if (!be.items().getStackInSlot(0).isEmpty() || !be.items().getStackInSlot(5).isEmpty()) {
                throw new GameTestAssertException("Eingang " + be.items().getStackInSlot(0) + ", Fluorit " + be.items().getStackInSlot(5));
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_chain", timeoutTicks = 200)
    public static void uf6NeedsFluorite(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.UF6_CONVERTER);
        feedPower(h, be);
        be.itemsAt("item_in").insertItem(0, new ItemStack(ReactorRegistry.URANIUM_OXIDE.get(), 2), false);
        h.runAfterDelay(80, () -> {
            formed(be);
            if (be.crafter().status() != BigCrafter.NO_AUX) throw new GameTestAssertException("Zustand " + be.crafter().status() + " statt KEIN FLUORIT");
            if (be.items().getStackInSlot(0).getCount() != 2) throw new GameTestAssertException("Eingang " + be.items().getStackInSlot(0));
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_chain", timeoutTicks = 400)
    public static void centrifugeEnrichesUf6(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.ENRICHMENT_CENTRIFUGE);
        feedPower(h, be);
        be.itemsAt("item_in").insertItem(0, new ItemStack(ReactorRegistry.UF6_CYLINDER.get()), false);
        h.runAfterDelay(300, () -> {
            formed(be);
            int enriched = count(be, ReactorRegistry.ENRICHED_URANIUM.get()), depleted = count(be, item("depleted_uranium_sqc"));
            if (enriched != 2 || depleted != 1) throw new GameTestAssertException("Erwartet 2 + 1, ist " + enriched + " + " + depleted + " (Zustand " + be.crafter().status() + ")");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "uranium_chain_data", timeoutTicks = 20)
    public static void uraniumChainIsInTheDataPack(GameTestHelper h) {
        int leach = 0, uf6 = 0;
        boolean oldCentrifuge = false, newCentrifuge = false, mill = false;
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ReprocessingRecipe r)) continue;
            switch (r.kind()) {
                case LEACHING -> {
                    leach++;
                    if (r.fluids().size() != 2) throw new GameTestAssertException("Laugung ohne Wasser und Saeure: " + holder.id());
                }
                case UF6_CONVERSION -> {
                    uf6++;
                    if ((!r.hasAux() && r.fluids().isEmpty()) || r.results().stream().noneMatch(x -> x.stack().is(ReactorRegistry.UF6_CYLINDER.get()))) throw new GameTestAssertException("UF6-Rezept falsch: " + holder.id());
                }
                case ENRICHMENT -> {
                    oldCentrifuge |= r.ingredient().test(new ItemStack(ReactorRegistry.YELLOWCAKE.get())) && !r.hasAux();
                    newCentrifuge |= r.ingredient().test(new ItemStack(ReactorRegistry.UF6_CYLINDER.get()));
                }
                case ORE_MILLING -> mill |= r.results().stream().anyMatch(x -> x.stack().is(ReactorRegistry.YELLOWCAKE.get()));
                default -> { }
            }
        }
        if (leach < 2) throw new GameTestAssertException("Laugungsrezepte: " + leach);
        if (uf6 < 2) throw new GameTestAssertException("UF6-Rezepte: " + uf6);
        if (!oldCentrifuge) throw new GameTestAssertException("Altes Yellowcake-Rezept der Zentrifuge fehlt");
        if (!newCentrifuge) throw new GameTestAssertException("UF6-Rezept der Zentrifuge fehlt");
        if (!mill) throw new GameTestAssertException("Erzmuehle liefert kein Yellowcake mehr");
        for (String id : new String[] {"leaching_plant_kit_sqc", "uf6_converter_kit_sqc", "leaching_plant_controller_sqc", "uf6_converter_controller_sqc",
                "machine_port_acid_sqc", "sulfuric_acid_bucket_sqc", "uranium_sludge_sqc", "uf6_cylinder_sqc"}) item(id);
        h.succeed();
    }
}
