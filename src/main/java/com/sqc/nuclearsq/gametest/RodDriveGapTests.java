package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RodDriveGapTests {
    private RodDriveGapTests() {}

    private static final String FLOOR = "floor";
    private static final int FULL_ROD = FuelRodHeadBlock.ROD_BLOCKS * FuelRodBlockEntity.UNIT;

    private static BigMachineBlockEntity rodDrive(GameTestHelper h, BlockPos min) {
        BigMachine m = BigMachine.CONTROL_ROD_DRIVE;
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("kein Controller");
        while (be.energy().receiveEnergy(1000, false) > 0) { }
        fill(be, "hydraulic_in", 4000);
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, int mb) {
        var f = port.startsWith("primary") ? ModFluids.PRIMARY_WATER.source.get() : Fluids.WATER;
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static void pumping(GameTestHelper h, BigMachineBlockEntity be) {
        h.onEachTick(() -> be.addPrimaryFlow(320));
    }

    private static void formed(BigMachineBlockEntity be) {
        if (!be.isFormed()) throw new GameTestAssertException("Antrieb wird nicht als aufgebaut erkannt (" + be.matched() + "/" + be.total() + ")");
    }

    private static void rodAt(BigMachineBlockEntity be, String who, int want) {
        if (be.rod() != want) throw new GameTestAssertException(who + ": Stabstellung " + be.rod() + ", erwartet " + want);
    }

    private static void twoRods(GameTestHelper h, BigMachineBlockEntity be, int charge) {
        for (int k = 0; k < 2; k++) {
            BlockPos head = be.rodBase(be.shafts()[k]).above(9);
            for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
                h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState()
                        .setValue(ReactorProps.ROD_FORMED, true));
                if (charge != FuelRodBlockEntity.UNIT && h.getLevel().getBlockEntity(head.below(i)) instanceof FuelRodBlockEntity f) {
                    f.setRemaining(charge);
                }
            }
            h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
    }

    private static int rodFuel(GameTestHelper h, BigMachineBlockEntity be, int k) {
        int sum = 0;
        BlockPos head = be.rodBase(be.shafts()[k]).above(9);
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            if (h.getLevel().getBlockEntity(head.below(i)) instanceof FuelRodBlockEntity f) sum += f.getRemaining();
        }
        return sum;
    }

    private static void lockAndInsert(BigMachineBlockEntity be) {
        formed(be);
        be.setShaftLocked(0, true);
        be.setShaftLocked(1, true);
        be.setRodTarget(15);
    }

    @GameTest(template = FLOOR, batch = "gap_vessel_runs_dry", timeoutTicks = 600)
    public static void fuelRodDriveVesselRunsDry(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        twoRods(h, be, FuelRodBlockEntity.UNIT);
        fill(be, "primary_cold_in", 600);
        pumping(h, be);
        int[] fuel = new int[1];
        h.runAfterDelay(25, () -> lockAndInsert(be));
        h.runAfterDelay(400, () -> {
            if (be.shafts()[0].shown() < 3.9f) throw new GameTestAssertException("Stab nicht eingezogen: " + be.shafts()[0].shown());
            int cold = be.tank("primary_cold_in").getFluidAmount();
            int hot = be.tank("primary_hot_out").getFluidAmount();
            if (cold != 0) throw new GameTestAssertException("Primaerwasser nicht verbraucht: " + cold + " mB");
            if (hot != 600) throw new GameTestAssertException("Heisses Primaerwasser passt nicht: " + hot + " mB aus 600 mB");
            if (!be.vesselDry() || be.statusLamp() != 2) {
                throw new GameTestAssertException("Kein Durchfluss mehr, aber keine Sicherung/rote Lampe (dry " + be.vesselDry() + ", Lampe " + be.statusLamp() + ")");
            }
            fuel[0] = rodFuel(h, be, 0) + rodFuel(h, be, 1);
            if (fuel[0] >= 2 * FULL_ROD) throw new GameTestAssertException("Kein Abbrand, solange Primaerwasser da war");
        });
        h.runAfterDelay(480, () -> {
            int now = rodFuel(h, be, 0) + rodFuel(h, be, 1);
            if (now != fuel[0]) throw new GameTestAssertException("Abbrand ohne Durchfluss: " + fuel[0] + " -> " + now);
            if (!be.vesselDry()) throw new GameTestAssertException("Sicherung faellt ohne Durchfluss wieder ab");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "gap_empty_rods", timeoutTicks = 500)
    public static void fuelRodDriveEmptyRodsMakeNoSteam(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        twoRods(h, be, 0);
        fill(be, "primary_cold_in", 8000);
        h.runAfterDelay(25, () -> {
            if (be.shafts()[0].headY() != 9) throw new GameTestAssertException("Leerer Stab nicht erkannt: " + be.shafts()[0].headY());
            lockAndInsert(be);
        });
        h.runAfterDelay(400, () -> {
            if (be.shafts()[0].shown() < 3.9f) throw new GameTestAssertException("Leerer Stab nicht eingezogen: " + be.shafts()[0].shown());
            if (be.tank("primary_hot_out").getFluidAmount() != 0) throw new GameTestAssertException("Leere Staebe erhitzen Primaerwasser");
            if (be.tank("primary_cold_in").getFluidAmount() != 8000) throw new GameTestAssertException("Primaerwasser verbraucht ohne Ladung");
            if (be.vesselDry()) throw new GameTestAssertException("Trocken-Sicherung bei leeren Staeben");
            if (rodFuel(h, be, 0) != 0 || rodFuel(h, be, 1) != 0) throw new GameTestAssertException("Ladung nicht 0");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "gap_channels", timeoutTicks = 400)
    public static void rodDriveChannelsStaySeparate(GameTestHelper h) {
        BigMachineBlockEntity a = rodDrive(h, new BlockPos(0, 4, 1));
        BigMachineBlockEntity b = rodDrive(h, new BlockPos(8, 4, 1));
        h.runAfterDelay(25, () -> {
            formed(a);
            formed(b);
            a.setChannel(1);
            b.setChannel(2);
            if (a.linked().size() != 1 || b.linked().size() != 1) {
                throw new GameTestAssertException("Getrennte Kanaele sind verbunden: " + a.linked().size() + "/" + b.linked().size());
            }
            a.setRodTarget(7);
            if (b.terminalMode() || b.rodTarget() == 7) throw new GameTestAssertException("Soll von Kanal 1 landet auf Kanal 2");
            b.setRodTarget(3);
            if (a.rodTarget() != 7) throw new GameTestAssertException("Soll von Kanal 2 landet auf Kanal 1");
        });
        h.runAfterDelay(125, () -> {
            rodAt(a, "A (Kanal 1)", 7);
            rodAt(b, "B (Kanal 2)", 3);
            b.setChannel(1);
            if (a.linked().size() != 2) throw new GameTestAssertException("Nach Kanalwechsel nicht in einer Gruppe");
            if (b.rodTarget() != 7 || !b.terminalMode()) throw new GameTestAssertException("Kanalwechsel uebernimmt das Soll nicht: " + b.rodTarget());
        });
        h.runAfterDelay(200, () -> {
            rodAt(b, "B nach Kanalwechsel", 7);
            h.getLevel().setBlockAndUpdate(b.getBlockPos(), Blocks.AIR.defaultBlockState());
        });
        h.runAfterDelay(230, () -> {
            if (a.linked().size() != 1) throw new GameTestAssertException("Abgebauter Antrieb bleibt in der Gruppe: " + a.linked().size());
            a.setRodTarget(2);
            if (a.rodTarget() != 2) throw new GameTestAssertException("Soll nach Abbau nicht setzbar");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "gap_scram_no_power", timeoutTicks = 500)
    public static void fuelRodDriveScramNeedsPower(GameTestHelper h) {
        BigMachineBlockEntity be = rodDrive(h, new BlockPos(2, 4, 2));
        twoRods(h, be, FuelRodBlockEntity.UNIT);
        fill(be, "primary_cold_in", 8000);
        pumping(h, be);
        boolean[] off = {false};
        h.onEachTick(() -> { if (off[0]) be.energy().setStored(0); });
        h.runAfterDelay(25, () -> lockAndInsert(be));
        h.runAfterDelay(250, () -> off[0] = true);
        h.runAfterDelay(260, () -> {
            rodAt(be, "vor SCRAM", 15);
            be.scram();
            if (be.rodTarget() != 15) throw new GameTestAssertException("SCRAM ohne Strom setzt Soll " + be.rodTarget());
            off[0] = false;
            while (be.energy().receiveEnergy(1000, false) > 0) { }
        });
        h.runAfterDelay(400, () -> {
            rodAt(be, "Strom wieder da", 15);
            be.scram();
            h.succeed();
        });
    }
}
