package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
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
}
