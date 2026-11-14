package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BreederCore;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class BreederTests {
    private BreederTests() {}

    private static final String FLOOR = "floor";

    private static Item item(String id) {
        return CatalogRegistry.ITEM_MAP.get(id).get();
    }

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

    private static void charge(BigMachineBlockEntity be) {
        while (be.energy().receiveEnergy(100000, false) > 0) { }
    }

    private static void formed(BigMachineBlockEntity be) {
        if (!be.isFormed()) throw new GameTestAssertException(be.machine().id + " nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
    }

    private static BigMachineBlockEntity breeder(GameTestHelper h, int sodium, int core, int blanket) {
        BigMachineBlockEntity be = build(h, BigMachine.FAST_BREEDER);
        charge(be);
        if (sodium > 0) fill(be, "sodium_in", ModFluids.SODIUM_COOLANT.source.get(), sodium);
        fill(be, "feedwater_in", ModFluids.FEEDWATER.source.get(), 8000);
        if (core > 0) be.items().setStackInSlot(0, new ItemStack(item("uranium_core_sqc"), core));
        if (blanket > 0) be.items().setStackInSlot(BreederCore.BLANKET_FIRST, new ItemStack(item("breeder_blanket_sqc"), blanket));
        return be;
    }

    @GameTest(template = FLOOR, batch = "breeder_ports", timeoutTicks = 60)
    public static void breederPortsFollowRules(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FAST_BREEDER);
        var tank = be.fluidsAt("sodium_in");
        if (tank.fill(new FluidStack(ModFluids.COOLANT.source.get(), 100), IFluidHandler.FluidAction.SIMULATE) != 0) throw new GameTestAssertException("Kreislauf nimmt Kuehlmittel an");
        if (tank.fill(new FluidStack(ModFluids.SODIUM_COOLANT.source.get(), 100), IFluidHandler.FluidAction.SIMULATE) != 100) throw new GameTestAssertException("Kreislauf nimmt kein Natrium an");
        var in = be.itemsAt("item_in");
        ItemStack core = new ItemStack(item("uranium_core_sqc")), blanket = new ItemStack(item("breeder_blanket_sqc"));
        if (!in.insertItem(0, core, true).isEmpty()) throw new GameTestAssertException("Treiberplatz nimmt keinen Kern");
        if (in.insertItem(0, blanket, true).isEmpty()) throw new GameTestAssertException("Treiberplatz nimmt Brutmantel");
        if (!in.insertItem(BreederCore.BLANKET_FIRST, blanket, true).isEmpty()) throw new GameTestAssertException("Mantelplatz nimmt keinen Brutmantel");
        if (in.insertItem(BreederCore.BLANKET_FIRST, core, true).isEmpty()) throw new GameTestAssertException("Mantelplatz nimmt Kern");
        if (in.insertItem(BreederCore.OUT_FIRST, blanket, true).isEmpty()) throw new GameTestAssertException("Ausgangsplatz nimmt Items vom Eingang");
        if (in.insertItem(0, new ItemStack(Blocks.DIRT), true).isEmpty()) throw new GameTestAssertException("Eingang nimmt Erde");
        be.items().setStackInSlot(0, core.copy());
        be.items().setStackInSlot(BreederCore.OUT_FIRST, new ItemStack(item("bred_blanket_sqc")));
        if (!in.extractItem(0, 1, true).isEmpty()) throw new GameTestAssertException("Eingang gibt Kern heraus");
        var out = be.itemsAt("item_out");
        if (!out.extractItem(0, 1, true).isEmpty()) throw new GameTestAssertException("Ausgang gibt Treiberkern heraus");
        if (out.extractItem(BreederCore.OUT_FIRST, 1, true).isEmpty()) throw new GameTestAssertException("Ausgang gibt Erbruetetes Element nicht her");
        if (!out.insertItem(BreederCore.OUT_FIRST + 1, blanket, true).equals(blanket)) throw new GameTestAssertException("Ausgang nimmt Items an");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "breeder_run", timeoutTicks = 120)
    public static void breederNeedsSodiumAndDriver(GameTestHelper h) {
        BigMachineBlockEntity dry = breeder(h, 0, 1, 1);
        h.runAfterDelay(40, () -> {
            formed(dry);
            if (!dry.stateKey().equals("no_sodium")) throw new GameTestAssertException("Zustand " + dry.stateKey() + " statt no_sodium");
            if (dry.tank("wet_steam_out").getFluidAmount() != 0) throw new GameTestAssertException("Dampf ohne Natrium");
            if (!dry.items().getStackInSlot(0).is(item("uranium_core_sqc"))) throw new GameTestAssertException("Kern ohne Natrium angebrannt");
            fill(dry, "sodium_in", ModFluids.SODIUM_COOLANT.source.get(), 20000);
        });
        h.runAfterDelay(80, () -> {
            if (dry.tank("wet_steam_out").getFluidAmount() <= 0) throw new GameTestAssertException("Kein Dampf mit Natrium und Kern (Zustand " + dry.stateKey() + ")");
            if (!dry.items().getStackInSlot(0).isEmpty()) throw new GameTestAssertException("Kern nicht eingesetzt");
            if (dry.breeder().activeDrivers() != 1) throw new GameTestAssertException("Treiber " + dry.breeder().activeDrivers());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "breeder_run", timeoutTicks = 80)
    public static void breederWithoutDriverIdles(GameTestHelper h) {
        BigMachineBlockEntity be = breeder(h, 20000, 0, 1);
        h.runAfterDelay(40, () -> {
            formed(be);
            if (!be.stateKey().equals("no_driver")) throw new GameTestAssertException("Zustand " + be.stateKey() + " statt no_driver");
            if (be.tank("wet_steam_out").getFluidAmount() != 0) throw new GameTestAssertException("Dampf ohne Treiber");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "breeder_run", timeoutTicks = 200)
    public static void breederBreedsBlanket(GameTestHelper h) {
        BigMachineBlockEntity be = breeder(h, 20000, 1, 2);
        be.breeder().setBlanketProgress(0, be.breeder().blanketTicks() - 2);
        h.runAfterDelay(60, () -> {
            formed(be);
            int steam = be.tank("wet_steam_out").getFluidAmount();
            if (steam < 60 * 20 || steam > 60 * 60) throw new GameTestAssertException("Dampf " + steam + " mB nach 60 Ticks (erwartet 60 mB/t)");
            if (be.tank("feedwater_in").getFluidAmount() != 8000 - steam) throw new GameTestAssertException("Speisewasser passt nicht zum Dampf");
            ItemStack in = be.items().getStackInSlot(BreederCore.BLANKET_FIRST);
            if (in.getCount() != 1) throw new GameTestAssertException("Brutmantel " + in.getCount() + " statt 1 uebrig");
            if (BreederCore.outputCount(be.items()) != 1 || !be.items().getStackInSlot(BreederCore.OUT_FIRST).is(item("bred_blanket_sqc"))) {
                throw new GameTestAssertException("Kein Erbruetetes Element im Ausgang");
            }
            if (be.breeder().blanketPercent(1) != 0 && be.items().getStackInSlot(BreederCore.BLANKET_FIRST + 1).isEmpty()) throw new GameTestAssertException("Fortschritt ohne Element");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "breeder_run", timeoutTicks = 100)
    public static void breederPlutoniumDriverIsFaster(GameTestHelper h) {
        BigMachineBlockEntity be = breeder(h, 20000, 1, 1);
        be.items().setStackInSlot(1, new ItemStack(item("plutonium_core_sqc"), 1));
        h.runAfterDelay(40, () -> {
            formed(be);
            float f = be.breeder().flux();
            if (be.breeder().activeDrivers() != 2 || Math.abs(f - 2.25f / 4) > 0.01f) throw new GameTestAssertException("Fluss " + f + " mit " + be.breeder().activeDrivers() + " Treibern");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "breeder_run", timeoutTicks = 120)
    public static void breederSodiumLeak(GameTestHelper h) {
        BigMachineBlockEntity be = breeder(h, 20000, 1, 1);
        be.breeder().startLeak();
        h.runAfterDelay(50, () -> {
            formed(be);
            if (!be.stateKey().equals("sodium_leak")) throw new GameTestAssertException("Zustand " + be.stateKey() + " statt sodium_leak");
            int sodium = be.tank("sodium_in").getFluidAmount();
            if (sodium > 20000 - 40 * 30 || sodium < 20000 - 40 * 60) throw new GameTestAssertException("Natrium " + sodium + " nach dem Leck");
            if (be.repair()) throw new GameTestAssertException("Wartung im Betrieb");
            be.setEnabled(false);
            if (!be.repair() || be.breeder().leak() || be.wearPercent() != 0) throw new GameTestAssertException("Wartung dichtet das Leck nicht");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "breeder_run", timeoutTicks = 60)
    public static void breederStatesAreListed(GameTestHelper h) {
        java.util.List<String> states = java.util.Arrays.asList(BigMachineBlockEntity.STATES);
        for (String k : new String[] {"no_sodium", "no_driver", "sodium_leak"}) if (!states.contains(k)) throw new GameTestAssertException("Zustand " + k + " fehlt");
        h.succeed();
    }

    private static void feedPower(GameTestHelper h, BigMachineBlockEntity be) {
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
    }

    @GameTest(template = FLOOR, batch = "weapons_plant", timeoutTicks = 1000)
    public static void weaponsPlantMakesMetal(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.WEAPONS_PROCESSING);
        feedPower(h, be);
        var in = be.itemsAt("item_in");
        if (in.insertItem(0, new ItemStack(Blocks.DIRT), true).isEmpty()) throw new GameTestAssertException("Anlage nimmt fremde Items an");
        if (!in.insertItem(0, new ItemStack(item("plutonium_oxide_sqc"), 4), false).isEmpty()) throw new GameTestAssertException("Oxid passt nicht hinein");
        h.runAfterDelay(860, () -> {
            formed(be);
            int metal = 0;
            for (int i = 1; i <= 4; i++) if (be.items().getStackInSlot(i).is(item("plutonium_metal_sqc"))) metal += be.items().getStackInSlot(i).getCount();
            if (metal != 1 || !be.items().getStackInSlot(0).isEmpty()) {
                throw new GameTestAssertException("Erwartet 1 Metall, ist " + metal + " (Eingang " + be.items().getStackInSlot(0) + ", Zustand " + be.crafter().status() + ")");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "weapons_plant", timeoutTicks = 800)
    public static void weaponsPlantMakesCore(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.WEAPONS_PROCESSING);
        feedPower(h, be);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("plutonium_metal_sqc"), 2), false);
        h.runAfterDelay(660, () -> {
            formed(be);
            int cores = 0;
            for (int i = 1; i <= 4; i++) if (be.items().getStackInSlot(i).is(item("weapons_plutonium_core_sqc"))) cores += be.items().getStackInSlot(i).getCount();
            if (cores != 1) throw new GameTestAssertException("Erwartet 1 Waffenkern, ist " + cores + " (Zustand " + be.crafter().status() + ")");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "breeder_recipes", timeoutTicks = 20)
    public static void breederChainIsInTheDataPack(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        Item du = item("depleted_uranium_sqc"), bred = item("bred_blanket_sqc"), oxide = item("plutonium_oxide_sqc");
        boolean centrifuge = false, bredOxide = false, core = false, metal = false, weapons = false;
        for (var holder : rm.getRecipes()) {
            if (!(holder.value() instanceof com.sqc.nuclearsq.reprocessing.ReprocessingRecipe r)) continue;
            boolean hasDu = r.results().stream().anyMatch(x -> x.stack().is(du));
            switch (r.kind()) {
                case ENRICHMENT -> centrifuge |= hasDu;
                case REPROCESSING -> {
                    bredOxide |= r.ingredient().test(new ItemStack(bred)) && r.results().stream().anyMatch(x -> x.stack().is(oxide)) && hasDu;
                    core |= r.ingredient().test(new ItemStack(oxide)) && r.count() == 4 && r.results().get(0).stack().is(item("plutonium_core_sqc"));
                }
                case WEAPONS_PROCESSING -> {
                    metal |= r.ingredient().test(new ItemStack(oxide)) && r.results().get(0).stack().is(item("plutonium_metal_sqc"));
                    weapons |= r.ingredient().test(new ItemStack(item("plutonium_metal_sqc"))) && r.results().get(0).stack().is(item("weapons_plutonium_core_sqc"));
                }
                default -> { }
            }
        }
        if (!centrifuge) throw new GameTestAssertException("Zentrifuge liefert kein abgereichertes Uran");
        if (!bredOxide) throw new GameTestAssertException("Erbruetetes Element ergibt kein Plutoniumoxid");
        if (!core) throw new GameTestAssertException("Plutonium-Kern nicht aus 4 Plutoniumoxid");
        if (!metal || !weapons) throw new GameTestAssertException("Waffenanlage: Metall " + metal + ", Kern " + weapons);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "breeder_recipes", timeoutTicks = 20)
    public static void bombsNeedWeaponsCores(GameTestHelper h) {
        var seen = new java.util.HashMap<String, java.util.List<String>>();
        for (var holder : com.sqc.nuclearsq.assembler.AssemblyRecipe.all(h.getLevel().getRecipeManager())) {
            var r = holder.value();
            var needs = new java.util.ArrayList<String>();
            for (var n : r.ingredients()) for (ItemStack s : n.ingredient().getItems()) needs.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath() + "x" + n.count());
            seen.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.result().getItem()).getPath(), needs);
        }
        for (String id : new String[] {"fast_breeder_controller_sqc", "weapons_processing_controller_sqc", "breeder_blanket_sqc"}) {
            if (!seen.containsKey(id)) throw new GameTestAssertException(id + " nicht im Reaktor-Assembler");
        }
        if (!seen.get("nuclear_bomb_large_sqc").contains("weapons_plutonium_core_sqcx1")) throw new GameTestAssertException("Grosse Bombe: " + seen.get("nuclear_bomb_large_sqc"));
        if (!seen.get("nuclear_bomb_tsar_sqc").contains("weapons_plutonium_core_sqcx4")) throw new GameTestAssertException("Zar-Bombe: " + seen.get("nuclear_bomb_tsar_sqc"));
        if (seen.get("nuclear_bomb_tsar_sqc").stream().anyMatch(s -> s.startsWith("plutonium_core_sqc"))) throw new GameTestAssertException("Zar-Bombe braucht noch Plutonium-Kerne");
        h.succeed();
    }
}
