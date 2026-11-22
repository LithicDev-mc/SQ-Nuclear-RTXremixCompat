package com.sqc.nuclearsq.gametest;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.CoreChemistry;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ChemiePlusTests {
    private ChemiePlusTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(java.util.UUID.fromString("3c6f1d52-8a4b-4e7d-9b10-5a2c7e9f0b34"), "nsq_chemie_plus");

    private static Item item(String id) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
        if (it == Items.AIR) throw new GameTestAssertException("Item " + id + " nicht registriert");
        return it;
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein BlockEntity");
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, net.minecraft.world.level.material.Fluid f, int mb) {
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(be.machine().id + "." + port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static int out(BigMachineBlockEntity be, Item it) {
        int n = 0;
        for (int i = 1; i <= 4; i++) if (be.items().getStackInSlot(i).is(it)) n += be.items().getStackInSlot(i).getCount();
        return n;
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_boron", timeoutTicks = 200)
    public static void emergencyCoolingInjectsBoron(GameTestHelper h) {
        BigMachineBlockEntity drive = build(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        BigMachineBlockEntity ec = build(h, BigMachine.EMERGENCY_COOLING, new BlockPos(2, 4, 12));
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), PROFILE);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("boric_acid_sqc"), 10));
        ec.stockBoron(p, InteractionHand.MAIN_HAND);
        if (p.getMainHandItem().getCount() != 10 - BigMachineBlockEntity.BORON_STOCK_MAX) throw new GameTestAssertException("Vorrat nimmt nicht 8 Stueck: " + p.getMainHandItem().getCount() + " uebrig");
        ec.setManual(true);
        h.onEachTick(() -> ec.energy().receiveEnergy(ec.energy().getMaxEnergyStored(), false));
        fill(ec, "water_in", net.minecraft.world.level.material.Fluids.WATER, 2000);
        h.runAfterDelay(100, () -> {
            if (!ec.isFormed() || !drive.isFormed()) throw new GameTestAssertException("Anlage nicht aufgebaut");
            int ppm = drive.core().chem().boron();
            if (ppm < 2 * CoreChemistry.BORON_PER_ITEM || ppm > 3 * CoreChemistry.BORON_PER_ITEM) throw new GameTestAssertException("Bor " + ppm + " ppm statt 200 bis 300");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_full", timeoutTicks = 120)
    public static void emergencyCoolingStopsWhenLoopFull(GameTestHelper h) {
        BigMachineBlockEntity drive = build(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        BigMachineBlockEntity ec = build(h, BigMachine.EMERGENCY_COOLING, new BlockPos(2, 4, 12));
        ec.setManual(true);
        h.onEachTick(() -> ec.energy().receiveEnergy(ec.energy().getMaxEnergyStored(), false));
        fill(ec, "water_in", net.minecraft.world.level.material.Fluids.WATER, 4000);
        fill(drive, "primary_cold_in", ModFluids.PRIMARY_WATER.source.get(), drive.tank("primary_cold_in").getCapacity());
        h.runAfterDelay(100, () -> {
            if (!ec.isFormed() || !drive.isFormed()) throw new GameTestAssertException("Anlage nicht aufgebaut");
            if (ec.tank("primary_out").getFluidAmount() > 0) throw new GameTestAssertException("Notkuehlung speist in den vollen Kreis: " + ec.tank("primary_out").getFluidAmount() + " mB");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_cell", timeoutTicks = 100)
    public static void fuelCellMakesPower(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FUEL_CELL, new BlockPos(2, 4, 2));
        fill(be, "hydrogen_in", ModFluids.HYDROGEN.source.get(), 400);
        fill(be, "oxygen_in", ModFluids.OXYGEN.source.get(), 100);
        h.runAfterDelay(20, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Brennstoffzelle nicht aufgebaut");
            int h2 = be.tank("hydrogen_in").getFluidAmount(), o2 = be.tank("oxygen_in").getFluidAmount();
            if (h2 >= 400 || (400 - h2) * 7 != 15 * (100 - o2)) throw new GameTestAssertException("Verbrauch Wasserstoff " + (400 - h2) + ", Sauerstoff " + (100 - o2));
            if (!be.isGenerator() || be.generatorPeak() != 15000) throw new GameTestAssertException("Kein Erzeuger mit 15000 FE/t");
            be.tank("oxygen_in").drain(1000, IFluidHandler.FluidAction.EXECUTE);
            int before = be.tank("hydrogen_in").getFluidAmount();
            h.runAfterDelay(10, () -> {
                if (be.tank("hydrogen_in").getFluidAmount() != before) throw new GameTestAssertException("Zelle laeuft ohne Sauerstoff");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_gas", timeoutTicks = 100)
    public static void gasGeneratorMakesPower(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.GAS_GENERATOR, new BlockPos(2, 4, 2));
        fill(be, "ethylene_in", ModFluids.ETHYLENE.source.get(), 300);
        h.runAfterDelay(20, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Gasgenerator nicht aufgebaut");
            int used = 300 - be.tank("ethylene_in").getFluidAmount();
            if (used == 0 || used % 6 != 0) throw new GameTestAssertException("Verbrauch " + used + " mB");
            if (!be.isGenerator() || be.generatorPeak() != 10000) throw new GameTestAssertException("Kein Erzeuger mit 10000 FE/t");
            be.tank("ethylene_in").drain(1000, IFluidHandler.FluidAction.EXECUTE);
            fill(be, "ethylene_in", ModFluids.ETHYLENE.source.get(), 2);
            h.runAfterDelay(10, () -> {
                if (be.tank("ethylene_in").getFluidAmount() != 2) throw new GameTestAssertException("Generator laeuft mit 2 mB");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_gas", timeoutTicks = 100)
    public static void gasGeneratorBurnsDiesel(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.GAS_GENERATOR, new BlockPos(2, 4, 2));
        fill(be, "ethylene_in", ModFluids.DIESEL.source.get(), 300);
        h.runAfterDelay(20, () -> {
            int used = 300 - be.tank("ethylene_in").getFluidAmount();
            if (used == 0 || used % 6 != 0) throw new GameTestAssertException("Diesel-Verbrauch " + used + " mB");
            if (be.tank("ethylene_in").fill(new net.neoforged.neoforge.fluids.FluidStack(ModFluids.ETHYLENE.source.get(), 10), IFluidHandler.FluidAction.SIMULATE) != 0)
                throw new GameTestAssertException("Tank mischt Diesel und Ethylen");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_buffer", timeoutTicks = 60)
    public static void fluidBufferHoldsOneFluid(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FLUID_BUFFER, new BlockPos(2, 4, 2));
        fill(be, "fluid_in", ModFluids.TAR.source.get(), 5000);
        h.runAfterDelay(5, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Pufferspeicher nicht aufgebaut");
            if (be.tank("fluid_in").getCapacity() != 256000) throw new GameTestAssertException("Kapazitaet " + be.tank("fluid_in").getCapacity());
            if (be.tank("fluid_in").fill(new net.neoforged.neoforge.fluids.FluidStack(ModFluids.DIESEL.source.get(), 10), IFluidHandler.FluidAction.SIMULATE) != 0)
                throw new GameTestAssertException("Puffer mischt zwei Sorten");
            if (be.tank("fluid_in").drain(2000, IFluidHandler.FluidAction.EXECUTE).getAmount() != 2000 || be.tank("fluid_in").getFluidAmount() != 3000)
                throw new GameTestAssertException("Abgabe falsch: " + be.tank("fluid_in").getFluidAmount());
            h.succeed();
        });
    }

    private static BlockPos outside(GameTestHelper h, BigMachineBlockEntity be, int x, int z) {
        BlockPos port = be.machine().structure.world(be.getBlockPos(), 0, x, 0, z);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (h.getLevel().getBlockState(port.relative(d)).isAir()) return port.relative(d);
        }
        throw new GameTestAssertException("Anschluss " + x + "," + z + " ohne freie Seite");
    }

    private static com.sqc.nuclearsq.transport.ConduitBlockEntity pipe(GameTestHelper h, BlockPos at, BlockPos port, boolean pull) {
        h.getLevel().setBlockAndUpdate(at, com.sqc.nuclearsq.transport.TransportRegistry.PIPES.get(com.sqc.nuclearsq.transport.TransportTier.BASIC).get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(at) instanceof com.sqc.nuclearsq.transport.ConduitBlockEntity c)) throw new GameTestAssertException("Rohr ohne BlockEntity");
        BlockPos d = port.subtract(at);
        if (pull && c.cycle(net.minecraft.core.Direction.getNearest(d.getX(), d.getY(), d.getZ()), true) != com.sqc.nuclearsq.transport.ConduitBlockEntity.PULL)
            throw new GameTestAssertException("Rohr saugt nicht");
        ((com.sqc.nuclearsq.transport.ConduitBlock) h.getLevel().getBlockState(at).getBlock()).refresh(h.getLevel(), at);
        return c;
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_buffer_ports", timeoutTicks = 80)
    public static void fluidBufferInAndOutPorts(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FLUID_BUFFER, new BlockPos(2, 4, 2));
        if (be.fluidsAt("fluid_in").drain(10, IFluidHandler.FluidAction.SIMULATE).getAmount() != 0) throw new GameTestAssertException("Eingang gibt ab");
        if (be.fluidsAt("fluid_out").fill(new FluidStack(ModFluids.TAR.source.get(), 10), IFluidHandler.FluidAction.SIMULATE) != 0)
            throw new GameTestAssertException("Ausgang nimmt an");
        fill(be, "fluid_in", ModFluids.TAR.source.get(), 5000);
        BlockPos outPort = be.machine().structure.world(be.getBlockPos(), 0, 2, 0, 1);
        pipe(h, outside(h, be, 2, 1), outPort, true);
        int[] last = {5000};
        h.onEachTick(() -> {
            int now = be.tank("fluid_in").getFluidAmount();
            if (now > last[0]) throw new GameTestAssertException("Puffer zieht wieder ein: " + last[0] + " -> " + now);
            last[0] = now;
        });
        h.runAfterDelay(60, () -> {
            if (last[0] >= 5000) throw new GameTestAssertException("Ausgang gibt nichts ab");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_buffer_pull", timeoutTicks = 60)
    public static void pullPipeSideNeverPushes(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FLUID_BUFFER, new BlockPos(2, 4, 2));
        BlockPos inPort = be.machine().structure.world(be.getBlockPos(), 0, 0, 0, 1);
        var c = pipe(h, outside(h, be, 0, 1), inPort, true);
        h.runAfterDelay(2, () -> {
            if (c.fluidSide(null).fill(new FluidStack(ModFluids.TAR.source.get(), 50), IFluidHandler.FluidAction.EXECUTE) != 50)
                throw new GameTestAssertException("Rohr nimmt nichts an");
        });
        h.runAfterDelay(40, () -> {
            if (be.tank("fluid_in").getFluidAmount() != 0) throw new GameTestAssertException("Saugseite schiebt zurueck: " + be.tank("fluid_in").getFluidAmount() + " mB im Puffer");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_buffer_bucket", timeoutTicks = 60)
    public static void fluidBufferTakesBucket(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FLUID_BUFFER, new BlockPos(2, 4, 2));
        h.runAfterDelay(5, () -> {
            ServerPlayer p = FakePlayerFactory.get(h.getLevel(), PROFILE);
            p.getInventory().clearContent();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            if (!be.acceptHeld(p, InteractionHand.MAIN_HAND)) throw new GameTestAssertException("Eimer wird nicht angenommen");
            if (be.tank("fluid_in").getFluidAmount() != 1000) throw new GameTestAssertException("Puffer hat " + be.tank("fluid_in").getFluidAmount() + " mB");
            if (!p.getMainHandItem().is(Items.BUCKET)) throw new GameTestAssertException("Kein leerer Eimer zurueck: " + p.getMainHandItem());
            if (!be.acceptHeld(p, InteractionHand.MAIN_HAND) || be.tank("fluid_in").getFluidAmount() != 0 || !p.getMainHandItem().is(Items.WATER_BUCKET))
                throw new GameTestAssertException("Leerer Eimer entnimmt nicht: " + be.tank("fluid_in").getFluidAmount() + " mB");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_comparator", timeoutTicks = 60)
    public static void fluidBufferComparatorShowsFill(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.FLUID_BUFFER, new BlockPos(2, 4, 2));
        h.runAfterDelay(5, () -> {
            if (be.comparator() != 0) throw new GameTestAssertException("Leer: Signal " + be.comparator());
            fill(be, "fluid_in", ModFluids.TAR.source.get(), be.tank("fluid_in").getCapacity() / 2);
            if (be.comparator() != 8) throw new GameTestAssertException("Halb voll: Signal " + be.comparator());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_sr", timeoutTicks = 560)
    public static void vitrificationSeparatesStrontium(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.VITRIFICATION_PLANT, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        fill(be, "hlw_out", ModFluids.HLW.source.get(), 600);
        fill(be, "nitric_in", ModFluids.NITRIC_ACID.source.get(), 400);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("ion_resin_sqc"), 2), false);
        h.runAfterDelay(480, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Verglasungsanlage nicht aufgebaut");
            if (out(be, item("strontium_cesium_sqc")) != 1 || out(be, item("spent_resin_sqc")) != 2) {
                throw new GameTestAssertException("Konzentrat " + out(be, item("strontium_cesium_sqc")) + ", Harz " + out(be, item("spent_resin_sqc")) + " (Zustand " + be.crafter().status() + ")");
            }
            if (be.tank("hlw_out").getFluidAmount() != 300 || be.tank("nitric_in").getFluidAmount() != 200) throw new GameTestAssertException("Fluessigkeiten: HLW " + be.tank("hlw_out").getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_plus_etch", timeoutTicks = 300)
    public static void chemLabEtchesSiliconWithHydrofluoricAcid(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.CHEMICAL_LAB, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        fill(be, "hf_in", ModFluids.HYDROFLUORIC_ACID.source.get(), 300);
        fill(be, "water_in", net.minecraft.world.level.material.Fluids.WATER, 400);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("silicon_raw_sqc"), 3), false);
        h.runAfterDelay(260, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Chemielabor nicht aufgebaut");
            if (out(be, item("silicon_pure_sqc")) != 3 || be.tank("hf_in").getFluidAmount() != 150) {
                throw new GameTestAssertException("Silizium " + out(be, item("silicon_pure_sqc")) + ", HF " + be.tank("hf_in").getFluidAmount() + " (Zustand " + be.crafter().status() + ")");
            }
            h.succeed();
        });
    }
}
