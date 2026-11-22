package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.robotics.RobotArmBlockEntity;
import com.sqc.nuclearsq.robotics.RoboticsRegistry;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.TransportRegistry;
import com.sqc.nuclearsq.transport.TransportTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class CableFlowTests {
    private CableFlowTests() {}

    private static void put(GameTestHelper h, BlockPos p) {
        h.getLevel().setBlockAndUpdate(p, TransportRegistry.CABLES.get(TransportTier.HARDENED).get().defaultBlockState());
    }

    private static void forceChunks(GameTestHelper h, BlockPos from, BlockPos to, boolean on) {
        for (int x = from.getX() >> 4; x <= to.getX() >> 4; x++) for (int z = from.getZ() >> 4; z <= to.getZ() >> 4; z++) h.getLevel().setChunkForced(x, z, on);
    }

    private static void run(GameTestHelper h, BlockPos src, BlockPos end, Direction toSource, BlockPos lo, BlockPos hi, int warm, int measure, long minAvg) {
        forceChunks(h, lo, hi, true);
        BigMachineBlockEntity sink = TestSinks.batteryAt(h, end, toSource);
        long[] got = {0};
        int[] t = {0};
        h.onEachTick(() -> {
            if (h.getLevel().getBlockEntity(src) instanceof ConduitBlockEntity c) c.energySide(Direction.UP).receiveEnergy(TransportTier.HARDENED.cableRate, false);
            if (!sink.isFormed()) return;
            int e = sink.energy().getEnergyStored();
            if (++t[0] > warm) got[0] += e;
            sink.energy().drain(e);
            if (t[0] == warm + measure) {
                forceChunks(h, lo, hi, false);
                long avg = got[0] / measure;
                System.out.println("CABLEFLOW avg/t=" + avg);
                if (avg < minAvg) throw new GameTestAssertException("Abnehmer am Ende bekommt nur " + avg + " FE/t, erwartet mindestens " + minAvg);
                h.succeed();
            }
        });
    }

    @GameTest(template = "floor", batch = "cableflow", timeoutTicks = 3000)
    public static void longLine(GameTestHelper h) {
        BlockPos b = h.absolutePos(new BlockPos(0, 40, 0));
        for (int i = 0; i < 300; i++) put(h, b.east(i));
        run(h, b, b.east(300), Direction.WEST, b, b.east(310), 400, 200, 1000);
    }

    @GameTest(template = "floor", batch = "cableflow", timeoutTicks = 3000)
    public static void comb(GameTestHelper h) {
        BlockPos b = h.absolutePos(new BlockPos(0, 40, 40));
        for (int i = 0; i < 200; i++) {
            put(h, b.east(i));
            if (i % 4 == 2) for (int k = 1; k <= 8; k++) { put(h, b.east(i).north(k)); put(h, b.east(i).south(k)); }
        }
        run(h, b, b.east(200), Direction.WEST, b.north(10), b.east(210).south(10), 1500, 200, 1000);
    }

    @GameTest(template = "floor", batch = "cableflow", timeoutTicks = 3000)
    public static void mesh(GameTestHelper h) {
        BlockPos b = h.absolutePos(new BlockPos(0, 40, 150));
        for (int x = 0; x < 30; x++) for (int z = 0; z < 30; z++) put(h, b.offset(x, 0, z));
        run(h, b, b.offset(30, 0, 29), Direction.WEST, b, b.offset(40, 0, 40), 1500, 200, 1000);
    }

    @GameTest(template = "floor", batch = "cableflow", timeoutTicks = 3000)
    public static void smallSinkNearSource(GameTestHelper h) {
        BlockPos b = h.absolutePos(new BlockPos(0, 40, 200));
        for (int i = 0; i < 80; i++) put(h, b.east(i));
        put(h, b.east(5).north(1));
        BigMachineBlockEntity near = TestSinks.batteryAt(h, b.east(5).north(2), Direction.SOUTH);
        h.onEachTick(() -> {
            if (near.isFormed()) near.energy().drain(near.energy().getEnergyStored());
        });
        run(h, b, b.east(80), Direction.WEST, b.north(10), b.east(90), 400, 200, 1000);
    }

    @GameTest(template = "floor", batch = "cableflow", timeoutTicks = 3000)
    public static void bigSinkBehindSmallNear(GameTestHelper h) {
        BlockPos b = h.absolutePos(new BlockPos(0, 40, 260));
        forceChunks(h, b.north(5), b.east(70).south(40), true);
        for (int i = 0; i < 60; i++) put(h, b.east(i));
        put(h, b.east(3).north(1));
        BigMachineBlockEntity near = TestSinks.batteryAt(h, b.east(3).north(2), Direction.SOUTH);
        List<BigMachineBlockEntity> far = new ArrayList<>();
        for (int k = 0; k < 15; k++) {
            put(h, b.east(60).south(k));
            if (k % 3 == 0) { far.add(TestSinks.batteryAt(h, b.east(61).south(k), Direction.WEST)); far.add(TestSinks.batteryAt(h, b.east(59).south(k + 1), Direction.EAST)); }
        }
        long[] got = {0};
        int[] t = {0};
        h.onEachTick(() -> {
            if (h.getLevel().getBlockEntity(b) instanceof ConduitBlockEntity c) c.energySide(Direction.UP).receiveEnergy(TransportTier.HARDENED.cableRate, false);
            if (near.isFormed()) near.energy().drain(near.energy().getEnergyStored());
            if (!far.stream().allMatch(BigMachineBlockEntity::isFormed)) return;
            t[0]++;
            for (BigMachineBlockEntity s : far) { int e = s.energy().getEnergyStored(); if (t[0] > 400) got[0] += e; s.energy().drain(e); }
            if (t[0] == 600) {
                forceChunks(h, b.north(5), b.east(70).south(40), false);
                long avg = got[0] / 200;
                System.out.println("CABLEFLOW bigSinkBehindSmallNear avg/t=" + avg);
                if (avg < 36000) throw new GameTestAssertException("Ferne Akkus bekommen nur " + avg + " FE/t, erwartet mindestens 36000");
                h.succeed();
            }
        });
    }

    private static BigMachineBlockEntity battery(GameTestHelper h, BlockPos min) {
        return battery(h, BigMachine.BATTERY_SMALL, min);
    }

    private static BigMachineBlockEntity battery(GameTestHelper h, BigMachine m, BlockPos min) {
        MultiblockStructure s = m.structure;
        BlockPos ctrl = h.absolutePos(min).subtract(s.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : s.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        return (BigMachineBlockEntity) h.getLevel().getBlockEntity(ctrl);
    }

    private static void even(GameTestHelper h, BigMachineBlockEntity[] b, BlockPos src) {
        int[] t = {0};
        h.onEachTick(() -> {
            for (BigMachineBlockEntity x : b) if (!x.isFormed()) return;
            if (h.getLevel().getBlockEntity(h.absolutePos(src)) instanceof ConduitBlockEntity c) c.energySide(Direction.UP).receiveEnergy(3000, false);
            if (++t[0] < 600) return;
            int lo = Integer.MAX_VALUE, hi = 0;
            for (BigMachineBlockEntity x : b) { lo = Math.min(lo, x.battery().fill()); hi = Math.max(hi, x.battery().fill()); }
            System.out.println("CABLEFLOW even " + lo + ".." + hi);
            if (lo < 10 || hi - lo > 2) throw new GameTestAssertException("Akkus laden ungleich: " + lo + " bis " + hi + " %");
            h.succeed();
        });
    }

    @GameTest(template = "floor", batch = "cableflow", timeoutTicks = 1200)
    public static void parallelBatteriesChargeEvenly(GameTestHelper h) {
        BigMachineBlockEntity[] b = {battery(h, new BlockPos(3, 2, 3)), battery(h, new BlockPos(3, 2, 6)), battery(h, new BlockPos(3, 2, 9))};
        for (int z = 0; z <= 12; z++) put(h, h.absolutePos(new BlockPos(2, 2, z)));
        even(h, b, new BlockPos(2, 2, 0));
    }

    @GameTest(template = "floor", batch = "cableflow", timeoutTicks = 1200)
    public static void seriesBatteriesChargeEvenly(GameTestHelper h) {
        BigMachineBlockEntity[] b = {battery(h, new BlockPos(3, 2, 3)), battery(h, new BlockPos(7, 2, 3)), battery(h, new BlockPos(11, 2, 3))};
        for (int x : new int[]{1, 2, 5, 6, 9, 10}) put(h, h.absolutePos(new BlockPos(x, 2, 4)));
        even(h, b, new BlockPos(1, 2, 4));
    }

    @GameTest(template = "floor", batch = "cableflow", timeoutTicks = 1500)
    public static void largeSeriesBatteriesChargeTogether(GameTestHelper h) {
        BigMachineBlockEntity[] b = new BigMachineBlockEntity[4];
        for (int i = 0; i < 4; i++) {
            b[i] = battery(h, BigMachine.BATTERY_LARGE, new BlockPos(3 + 5 * i, 2, 3));
            put(h, h.absolutePos(new BlockPos(1 + 5 * i, 2, 4)));
            put(h, h.absolutePos(new BlockPos(2 + 5 * i, 2, 4)));
        }
        int[] t = {0};
        h.onEachTick(() -> {
            for (BigMachineBlockEntity x : b) if (!x.isFormed()) return;
            if (h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 2, 4))) instanceof ConduitBlockEntity c) c.energySide(Direction.UP).receiveEnergy(1500, false);
            if (++t[0] < 800) return;
            int lo = Integer.MAX_VALUE, hi = 0;
            for (BigMachineBlockEntity x : b) { lo = Math.min(lo, x.energy().getEnergyStored()); hi = Math.max(hi, x.energy().getEnergyStored()); }
            System.out.println("CABLEFLOW largeSeries " + lo + ".." + hi);
            if (lo < 150000 || hi - lo > 64000) throw new GameTestAssertException("Grosse Akkus laden nacheinander: " + lo + " bis " + hi + " FE");
            h.succeed();
        });
    }

    @GameTest(template = "floor", batch = "cableflow", timeoutTicks = 1000)
    public static void machinesFillTogether(GameTestHelper h) {
        for (int z = 0; z <= 12; z++) put(h, h.absolutePos(new BlockPos(2, 2, z)));
        BlockPos[] arms = new BlockPos[5];
        for (int i = 0; i < 5; i++) {
            arms[i] = h.absolutePos(new BlockPos(3, 2, 2 + 2 * i));
            h.getLevel().setBlockAndUpdate(arms[i], RoboticsRegistry.ARM.get().defaultBlockState());
        }
        int[] t = {0};
        h.onEachTick(() -> {
            if (h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 2, 0))) instanceof ConduitBlockEntity c) c.energySide(Direction.UP).receiveEnergy(1000, false);
            if (++t[0] < 150) return;
            int lo = Integer.MAX_VALUE, hi = 0;
            for (BlockPos p : arms) {
                int e = ((RobotArmBlockEntity) h.getLevel().getBlockEntity(p)).energy().getEnergyStored();
                lo = Math.min(lo, e);
                hi = Math.max(hi, e);
            }
            System.out.println("CABLEFLOW machines " + lo + ".." + hi);
            if (lo < 20000 || hi - lo > 3000) throw new GameTestAssertException("Maschinen laden nacheinander: " + lo + " bis " + hi + " FE");
            h.succeed();
        });
    }
}
