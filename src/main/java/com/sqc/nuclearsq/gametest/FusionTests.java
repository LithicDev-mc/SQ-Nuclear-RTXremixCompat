package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.machine.big.FusionCore;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.Map;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class FusionTests {
    private FusionTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": Controller ohne BlockEntity");
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, Fluid f, int mb) {
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(be.machine().id + "." + port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static void charge(BigMachineBlockEntity be) {
        while (be.energy().receiveEnergy(24000, false) > 0) { }
        if (be.fusion() == null || be.fusion().burning()) return;
        long all = be.fusion().ignitionCost(), want = all - 267L * be.machine().extra("ignition_fe_t");
        if (be.fusion().ignitionPercent() >= 100 * want / all) return;
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        be.fusion().save(t);
        t.getCompound("fusion").putLong("charge", want);
        be.fusion().load(t);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }

    @GameTest(template = FLOOR, batch = "fusion_1", timeoutTicks = 100)
    public static void fusionMachinesBuild(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        int cells = BigMachine.FUSION_REACTOR.structure.cells(BlockPos.ZERO, 0).size();
        if (cells != 1893) fail("Tokamak hat " + cells + " Felder statt 1893");
        if (BigMachine.FUSION_REACTOR.ports.size() != 14) fail("Tokamak hat " + BigMachine.FUSION_REACTOR.ports.size() + " Anschluesse");
        for (ModFluids.Entry e : new ModFluids.Entry[] {ModFluids.DEUTERIUM, ModFluids.TRITIUM, ModFluids.HELIUM}) {
            if (!e.gas() || e.bucket != null) fail(e.id + " ist kein Gas ohne Eimer");
        }
        if (!BigPortKind.GAS.isFluid()) fail("Gas-Anschluss ist kein Fluessigkeits-Port");
        h.runAfterDelay(30, () -> {
            if (!fu.isFormed()) {
                StringBuilder bad = new StringBuilder();
                for (MultiblockStructure.Cell c : BigMachine.FUSION_REACTOR.structure.cells(fu.getBlockPos(), 0)) {
                    if (h.getLevel().getBlockState(c.pos()).getBlock() != c.block()) bad.append(' ').append(c.pos().toShortString()).append('=').append(h.getLevel().getBlockState(c.pos()).getBlock()).append('/').append(c.block());
                }
                fail("Tokamak wird nicht erkannt (" + fu.matched() + "/" + fu.total() + ")" + bad);
            }
            int shown = 0;
            for (MultiblockStructure.Cell c : BigMachine.FUSION_REACTOR.structure.cells(fu.getBlockPos(), 0)) {
                var st = h.getLevel().getBlockState(c.pos());
                if (st.hasProperty(com.sqc.nuclearsq.reactor.ReactorProps.FORMED) && !st.getValue(com.sqc.nuclearsq.reactor.ReactorProps.FORMED)) shown++;
                else if (!st.hasProperty(com.sqc.nuclearsq.reactor.ReactorProps.FORMED) && c.pos() != fu.getBlockPos()) shown++;
            }
            if (shown > 0) fail("Tokamak gebildet, aber " + shown + " Bausteine noch sichtbar");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_11", timeoutTicks = 60)
    public static void terminalOpensWithoutOverrun(GameTestHelper h) {
        int count = com.sqc.nuclearsq.machine.DeviceMenu.count(com.sqc.nuclearsq.machine.DeviceMenu.BIG);
        for (BigMachine m : BigMachine.values()) {
            int rows = m == BigMachine.FUSION_REACTOR ? BigMachineBlockEntity.FUSION_PORTS.length : Math.min(m.ports.size(), BigMachineBlockEntity.MAX_TERM_PORTS);
            int last = BigMachineBlockEntity.TERM_PORTS + BigMachineBlockEntity.PORT_VALUES * Math.max(rows, 1) - 1;
            if (last >= count) fail(m.id + ": Terminal liest Datenfeld " + last + " von " + count);
            if (m == BigMachine.FUSION_REACTOR && BigMachineBlockEntity.FUSION_PORTS[rows - 1] >= m.ports.size()) fail("Tokamak: Anschluss-Nummer ausserhalb");
        }
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        h.runAfterDelay(10, () -> {
            try {
                var mth = BigMachineBlockEntity.class.getDeclaredMethod("machineData");
                mth.setAccessible(true);
                var data = (net.minecraft.world.inventory.ContainerData) mth.invoke(fu);
                if (data.getCount() != count) fail("Terminal-Daten " + data.getCount() + " statt " + count);
                for (int i = 0; i < count; i++) data.get(i);
            } catch (ReflectiveOperationException e) {
                fail("Terminal-Daten nicht lesbar: " + e);
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_12", timeoutTicks = 200)
    public static void formedPartsStayHiddenAfterReload(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        h.runAfterDelay(30, () -> {
            if (!fu.isFormed()) fail("Tokamak nicht gebildet");
            BlockPos pos = fu.getBlockPos();
            var tag = fu.saveWithFullMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(pos);
            var again = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, h.getLevel().getBlockState(pos), tag, h.getLevel().registryAccess());
            if (again == null) fail("Controller nicht ladbar");
            h.getLevel().setBlockEntity(again);
            int n = 0;
            for (MultiblockStructure.Cell c : BigMachine.FUSION_REACTOR.structure.cells(pos, 0)) {
                var st = h.getLevel().getBlockState(c.pos());
                if (n++ % 3 == 0 && st.hasProperty(com.sqc.nuclearsq.reactor.ReactorProps.FORMED) && st.getValue(com.sqc.nuclearsq.reactor.ReactorProps.FORMED))
                    h.getLevel().setBlock(c.pos(), st.setValue(com.sqc.nuclearsq.reactor.ReactorProps.FORMED, false), 2);
            }
        });
        h.runAfterDelay(120, () -> {
            var be = (BigMachineBlockEntity) h.getLevel().getBlockEntity(fu.getBlockPos());
            if (!be.isFormed()) fail("nach dem Laden nicht wieder gebildet");
            int shown = 0;
            for (MultiblockStructure.Cell c : BigMachine.FUSION_REACTOR.structure.cells(be.getBlockPos(), 0)) {
                var st = h.getLevel().getBlockState(c.pos());
                if (st.hasProperty(com.sqc.nuclearsq.reactor.ReactorProps.FORMED) && !st.getValue(com.sqc.nuclearsq.reactor.ReactorProps.FORMED)) shown++;
            }
            if (shown > 0) fail("nach dem Laden " + shown + " Bausteine sichtbar");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_2", timeoutTicks = 60)
    public static void gasPortsFilterByFluid(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        h.runAfterDelay(5, () -> {
            for (String[] t : new String[][] {{"deuterium_in", "deuterium"}, {"tritium_io", "tritium"}, {"helium_out", "helium"}}) {
                for (ModFluids.Entry e : new ModFluids.Entry[] {ModFluids.DEUTERIUM, ModFluids.TRITIUM, ModFluids.HELIUM}) {
                    int n = fu.tank(t[0]).fill(new FluidStack(e.source.get(), 10), IFluidHandler.FluidAction.SIMULATE);
                    if ((n > 0) != e.id.equals(t[1])) fail(t[0] + " nimmt " + e.id + (n > 0 ? " an" : " nicht an"));
                }
            }
            if (fu.tank("coolant_in").fill(new FluidStack(ModFluids.COOLANT.source.get(), 10), IFluidHandler.FluidAction.SIMULATE) != 10) fail("coolant_in nimmt kein Kuehlmittel an");
            if (fu.tank("coolant_in").fill(new FluidStack(ModFluids.HOT_COOLANT.source.get(), 10), IFluidHandler.FluidAction.SIMULATE) != 0) fail("coolant_in nimmt heisses Kuehlmittel an");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_3", timeoutTicks = 700)
    public static void fusionIgnitesAndDeliversNetPower(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        fill(fu, "deuterium_in", ModFluids.DEUTERIUM.source.get(), 4000);
        fill(fu, "tritium_io", ModFluids.TRITIUM.source.get(), 4000);
        h.onEachTick(() -> {
            charge(fu);
            fu.tank("coolant_in").fill(new FluidStack(ModFluids.COOLANT.source.get(), 32000), IFluidHandler.FluidAction.EXECUTE);
            fu.tank("coolant_out").drain(32000, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(40, () -> {
            if (fu.fusion().phase() != FusionCore.HEATING || !fu.stateKey().equals("heating")) fail("Zustand " + fu.stateKey() + " statt heating");
            if (fu.fusion().ignitionPercent() <= 0) fail("kein Zuendfortschritt");
        });
        int[] mark = new int[2];
        h.runAfterDelay(330, () -> {
            if (!fu.fusion().burning() || !fu.stateKey().equals("plasma")) fail("Zustand " + fu.stateKey() + " statt plasma");
            mark[0] = fu.tank("deuterium_in").getFluidAmount();
            mark[1] = fu.tank("helium_out").getFluidAmount();
        });
        h.runAfterDelay(430, () -> {
            if (fu.fusion().lastNet() != 6750000 || fu.fusion().lastGross() != 6750000) fail("Leistung " + fu.fusion().lastGross() + "/" + fu.fusion().lastNet());
            if (fu.fusion().temperature() < 100) fail("Temperatur " + fu.fusion().temperature());
            int d = mark[0] - fu.tank("deuterium_in").getFluidAmount(), he = fu.tank("helium_out").getFluidAmount() - mark[1];
            if (d < 150 || d > 170) fail("Deuterium-Verbrauch " + d + " mB in 100 Ticks");
            if (he < 150 || he > 170) fail("Helium " + he + " mB in 100 Ticks");
            if (fu.fusion().out().getEnergyStored() <= 0) fail("kein Strom im Ausgang");
        });
        h.runAfterDelay(530, () -> {
            if (fu.fusion().burning()) fail("Plasma brennt laenger als 200 Ticks");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_13", timeoutTicks = 700)
    public static void plasmaOptimizerRaisesOutput(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        fill(fu, "deuterium_in", ModFluids.DEUTERIUM.source.get(), 4000);
        fill(fu, "tritium_io", ModFluids.TRITIUM.source.get(), 4000);
        int max = BigMachine.FUSION_REACTOR.extra("plasma_optimizer_max");
        if (max != 6 || BigMachine.FUSION_REACTOR.extra("plasma_optimizer_gross_pct") != 5) fail("Upgrade-Werte " + max);
        h.runAfterDelay(5, () -> {
            var in = fu.itemsAt("item_in");
            ItemStack opt = new ItemStack(item("plasma_optimizer_sqc"), max + 2);
            if (in.insertItem(0, opt, true).getCount() == opt.getCount()) fail("Eingang nimmt Optimierer nicht an");
            fu.items().setStackInSlot(0, opt);
        });
        h.onEachTick(() -> {
            charge(fu);
            fu.tank("coolant_in").fill(new FluidStack(ModFluids.COOLANT.source.get(), 32000), IFluidHandler.FluidAction.EXECUTE);
            fu.tank("coolant_out").drain(32000, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(330, () -> {
            if (!fu.fusion().burning()) fail("Zustand " + fu.stateKey());
            if (fu.fusion().upgradeCount(0) != max) fail("Eingebaut " + fu.fusion().upgradeCount(0) + " statt " + max);
            if (fu.items().getStackInSlot(0).getCount() != 2) fail("Ueberzaehlige Optimierer " + fu.items().getStackInSlot(0).getCount());
            if (fu.fusion().upgradeBonusPercent() != 30 || fu.fusion().ignitionCost() != 2340000000L || fu.fusion().lastGross() != 12285000 || fu.fusion().lastNet() != 12285000)
                fail("Leistung " + fu.fusion().lastGross() + "/" + fu.fusion().lastNet());
            var tag = new CompoundTag();
            fu.fusion().save(tag);
            if (tag.getCompound("fusion").getInt("up_plasma_optimizer") != max) fail("Upgrades nicht gespeichert");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_14", timeoutTicks = 60)
    public static void plasmaOptimizerDropsIntoOutput(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        h.runAfterDelay(5, () -> {
            var sl = h.getLevel();
            fu.fusion().giveUpgrade(sl, fu.getBlockPos(), fu.items(), 0);
            boolean found = false;
            for (int i = FusionCore.INPUTS; i < FusionCore.SLOTS; i++) found |= fu.items().getStackInSlot(i).is(item("plasma_optimizer_sqc"));
            if (!found) fail("Optimierer nicht im Ausgang");
            for (int i = FusionCore.INPUTS; i < FusionCore.SLOTS; i++) fu.items().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            fu.fusion().giveUpgrade(sl, fu.getBlockPos(), fu.items(), 0);
            if (sl.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(fu.getBlockPos()).inflate(3))
                    .stream().noneMatch(e -> e.getItem().is(item("plasma_optimizer_sqc")))) fail("Optimierer nicht herausgefallen");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_4", timeoutTicks = 700)
    public static void fusionDisruptsWithoutCooling(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        fill(fu, "deuterium_in", ModFluids.DEUTERIUM.source.get(), 4000);
        fill(fu, "tritium_io", ModFluids.TRITIUM.source.get(), 4000);
        boolean[] cut = {false};
        h.onEachTick(() -> {
            charge(fu);
            if (fu.fusion().burning()) cut[0] = true;
            if (cut[0]) fu.tank("coolant_in").drain(32000, IFluidHandler.FluidAction.EXECUTE);
            else fu.tank("coolant_in").fill(new FluidStack(ModFluids.COOLANT.source.get(), 32000), IFluidHandler.FluidAction.EXECUTE);
            fu.tank("coolant_out").drain(32000, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(470, () -> {
            if (!cut[0]) fail("Plasma nie gezuendet");
            if (fu.fusion().phase() != FusionCore.DISRUPTED || !fu.stateKey().equals("disrupted")) fail("Zustand " + fu.stateKey() + " statt disrupted");
            if (fu.fusion().wearPercent() < 10) fail("Wand ohne Schaden: " + fu.fusion().wearPercent() + " %");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_5", timeoutTicks = 700)
    public static void fusionStartsOnDeuteriumAndBreedsTritium(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        fill(fu, "deuterium_in", ModFluids.DEUTERIUM.source.get(), 8000);
        h.onEachTick(() -> {
            charge(fu);
            fu.tank("coolant_in").fill(new FluidStack(ModFluids.COOLANT.source.get(), 32000), IFluidHandler.FluidAction.EXECUTE);
            fu.tank("coolant_out").drain(32000, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(320, () -> {
            if (!fu.fusion().burning()) fail("Zustand " + fu.stateKey() + ", Deuterium allein zuendet nicht");
            if (fu.fusion().burnNow() > 12) fail("Brennrate " + fu.fusion().burnNow() + " % im D-D-Betrieb");
            if (fu.tank("tritium_io").getFluidAmount() <= 0 && !fu.fusion().ddMode()) fail("weder D-D-Betrieb noch Tritium");
        });
        h.runAfterDelay(560, () -> {
            if (fu.tank("helium_out").getFluidAmount() <= 0) fail("keine Asche");
            if (fu.tank("tritium_io").getFluidAmount() <= 0 && fu.fusion().ddMode()) fail("kein Tritium nach dem D-D-Betrieb");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_6", timeoutTicks = 700)
    public static void fusionVentsHeliumWhenFull(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        fill(fu, "deuterium_in", ModFluids.DEUTERIUM.source.get(), 4000);
        fill(fu, "tritium_io", ModFluids.TRITIUM.source.get(), 4000);
        fill(fu, "helium_out", ModFluids.HELIUM.source.get(), 16000);
        h.onEachTick(() -> {
            charge(fu);
            fu.tank("coolant_in").fill(new FluidStack(ModFluids.COOLANT.source.get(), 32000), IFluidHandler.FluidAction.EXECUTE);
            fu.tank("coolant_out").drain(32000, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(330, () -> {
            if (!fu.fusion().burning()) fail("Plasma aus mit vollem Heliumtank, Zustand " + fu.stateKey());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_7", timeoutTicks = 40)
    public static void wallTileSwapsAndWornTileIsWaste(GameTestHelper h) {
        BigMachine m = BigMachine.FUSION_REACTOR;
        FusionCore core = new FusionCore(m, () -> { });
        CompoundTag tag = new CompoundTag(), f = new CompoundTag();
        f.putLong("wear", m.extra("wall_wear_ticks") + 5);
        f.putInt("burn", 100);
        tag.put("fusion", f);
        core.load(tag);
        if (core.wearPercent() != 100) fail("Verschleiss " + core.wearPercent());
        ItemStackHandler items = new ItemStackHandler(FusionCore.SLOTS) {
            @Override public boolean isItemValid(int slot, ItemStack stack) { return FusionCore.valid(slot, stack); }
        };
        Map<String, FluidTank> tanks = new HashMap<>();
        for (String n : new String[] {"deuterium_in", "tritium_io", "helium_out", "coolant_in", "coolant_out"}) tanks.put(n, new FluidTank(16000));
        MachineEnergy energy = new MachineEnergy(1000, 1000);
        core.tick(h.getLevel(), h.absolutePos(BlockPos.ZERO), tanks, energy, items, true, 0);
        if (!FusionCore.WALL_WORN.equals(core.reason())) fail("Zustand '" + core.reason() + "' statt wall_worn");
        ItemStack fresh = new ItemStack(item("first_wall_tile_sqc"));
        if (!items.insertItem(0, fresh, false).isEmpty()) fail("Eingang nimmt die Wandfliese nicht an");
        if (items.isItemValid(1, new ItemStack(Items.DIRT))) fail("Eingang nimmt Erde an");
        core.tick(h.getLevel(), h.absolutePos(BlockPos.ZERO), tanks, energy, items, true, 0);
        if (core.wearPercent() != 0) fail("Verschleiss nach dem Tausch " + core.wearPercent());
        if (!items.getStackInSlot(0).isEmpty()) fail("neue Fliese nicht eingesetzt");
        boolean worn = false;
        for (int i = FusionCore.INPUTS; i < FusionCore.SLOTS; i++) worn |= items.getStackInSlot(i).is(item("worn_wall_tile_sqc"));
        if (!worn) fail("aktivierte Fliese fehlt im Ausgang");
        if (!com.sqc.nuclearsq.spent.SpentFuel.isWaste(new ItemStack(item("worn_wall_tile_sqc")))) fail("aktivierte Fliese ist kein Endlager-Abfall");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "fusion_8", timeoutTicks = 60)
    public static void fusionItemPortsAreSplit(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        h.runAfterDelay(5, () -> {
            var in = fu.itemsAt("item_in");
            var out = fu.itemsAt("item_out");
            ItemStack pellets = new ItemStack(item("lithium_pellet_sqc"), 4);
            if (!in.insertItem(0, pellets, false).isEmpty()) fail("Pellets passen nicht in den Eingang");
            if (in.insertItem(5, pellets, true).getCount() != 4) fail("Eingang nimmt in Ausgangsfelder auf");
            if (in.insertItem(1, new ItemStack(Items.COBBLESTONE), true).getCount() != 1) fail("Eingang nimmt Bruchstein an");
            if (!in.extractItem(0, 1, true).isEmpty()) fail("Eingang gibt Items her");
            if (!out.extractItem(0, 1, true).isEmpty()) fail("Ausgang gibt Eingangsfelder her");
            if (out.insertItem(4, pellets, true).getCount() != 4) fail("Ausgang nimmt Items an");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_9", timeoutTicks = 120)
    public static void deuteriumSeparatorMakesDeuterium(GameTestHelper h) {
        BigMachineBlockEntity sep = build(h, BigMachine.DEUTERIUM_SEPARATOR, new BlockPos(2, 4, 2));
        fill(sep, "water_in", net.minecraft.world.level.material.Fluids.WATER, 6400);
        h.runAfterDelay(30, () -> {
            if (sep.tank("deuterium_out").getFluidAmount() != 0) fail("Deuterium ohne Strom");
            charge(sep);
        });
        h.runAfterDelay(90, () -> {
            int d = sep.tank("deuterium_out").getFluidAmount(), used = 6400 - sep.tank("water_in").getFluidAmount();
            if (d < 100 || d > 125) fail("Deuterium " + d + " mB nach 60 Ticks");
            if (used < 64 * 55 || used > 64 * 62 || used % 64 != 0) fail("Wasserverbrauch " + used + " mB");
            if (!sep.tank("deuterium_out").getFluid().is(ModFluids.DEUTERIUM.source.get())) fail("falsche Fluessigkeit im Ausgang");
            if (!sep.stateKey().equals("running")) fail("Zustand " + sep.stateKey());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "fusion_10", timeoutTicks = 20)
    public static void fusionItemsExist(GameTestHelper h) {
        for (String id : new String[] {"superconducting_coil_sqc", "first_wall_tile_sqc", "worn_wall_tile_sqc", "lithium_pellet_sqc", "raw_lithium_sqc",
                "plasma_optimizer_sqc", "lithium_ingot_sqc", "lithium_ore_sqc", "deepslate_lithium_ore_sqc", "machine_port_gas_sqc", "fusion_reactor_kit_sqc",
                "deuterium_separator_kit_sqc", "fusion_reactor_controller_sqc", "deuterium_separator_controller_sqc"}) {
            if (item(id) == Items.AIR) fail(id + " ist nicht registriert");
        }
        h.succeed();
    }
    @GameTest(template = FLOOR, batch = "fusion_15", timeoutTicks = 100)
    public static void noMobsInsideFusion(GameTestHelper h) {
        BigMachineBlockEntity fu = build(h, BigMachine.FUSION_REACTOR, new BlockPos(0, 4, 0));
        var sl = h.getLevel();
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(sl);
        var cow = net.minecraft.world.entity.EntityType.COW.create(sl);
        h.runAfterDelay(30, () -> {
            if (!fu.isFormed()) fail("Tokamak nicht gebildet");
            var in = net.minecraft.world.phys.Vec3.atBottomCenterOf(BigMachine.FUSION_REACTOR.structure.world(fu.getBlockPos(), 0, 2, 3, 2));
            var out = net.minecraft.world.phys.Vec3.atBottomCenterOf(BigMachine.FUSION_REACTOR.structure.world(fu.getBlockPos(), 0, 10, 0, -3));
            var probe = net.minecraft.world.entity.EntityType.ZOMBIE.create(sl);
            probe.moveTo(in);
            var ev = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(probe, sl, net.minecraft.world.entity.MobSpawnType.NATURAL, null));
            if (ev.getResult() != net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL) fail("Spawn im Ringraum erlaubt");
            probe.moveTo(out);
            ev = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(probe, sl, net.minecraft.world.entity.MobSpawnType.NATURAL, null));
            if (ev.getResult() != net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.DEFAULT) fail("Spawn vor dem Reaktor gesperrt");
            zombie.moveTo(in);
            cow.moveTo(in.add(1, 0, 0));
            sl.addFreshEntity(zombie);
            sl.addFreshEntity(cow);
        });
        h.runAfterDelay(60, () -> {
            if (zombie.isAlive()) fail("Zombie im Ringraum bleibt");
            if (!cow.isAlive()) fail("Kuh wurde entfernt");
            cow.discard();
            h.succeed();
        });
    }
}
