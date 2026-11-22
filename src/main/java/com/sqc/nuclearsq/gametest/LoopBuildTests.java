package com.sqc.nuclearsq.gametest;

import java.util.HashSet;
import java.util.Set;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.LoopTestBuild;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class LoopBuildTests {
    private LoopBuildTests() {}

    private static final int FILL_TICKS = 800, RUN_IN = 700, WATCH = 400, LEVEL_BAND = 2000;

    private static BigMachineBlockEntity machine(GameTestHelper h, LoopTestBuild.Result r, String key) {
        BlockPos p = r.anchors().get(key);
        if (p == null || !(h.getLevel().getBlockEntity(p) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Anlage fehlt: " + key);
        if (!be.isFormed()) throw new GameTestAssertException("Anlage nicht aufgebaut: " + key);
        return be;
    }

    private static void steady(GameTestHelper h, String name, BlockPos rel, int start) {
        final int from = start + RUN_IN, to = from + WATCH;
        BlockPos origin = h.absolutePos(rel);
        BlockPos[] box = LoopTestBuild.bounds(name, origin);
        Set<Long> chunks = new HashSet<>();
        for (int x = (box[0].getX() - 2) >> 4; x <= (box[1].getX() + 2) >> 4; x++) {
            for (int z = (box[0].getZ() - 2) >> 4; z <= (box[1].getZ() + 2) >> 4; z++) chunks.add(ChunkPos.asLong(x, z));
        }
        for (long c : chunks) h.getLevel().setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
        LoopTestBuild.Result r = LoopTestBuild.build(h.getLevel(), origin, name);
        for (BlockPos p : BlockPos.betweenClosed(r.lo().offset(-2, -6, -2), r.hi().offset(2, 16, 2))) {
            if (h.getLevel().getBlockState(p).getBlock() instanceof com.sqc.nuclearsq.machine.CreativeSourceBlock) {
                LoopTestBuild.remove(h.getLevel(), origin, name, r);
                for (long c : chunks) h.getLevel().setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
                throw new GameTestAssertException(name + ": Kreativ-Block im Aufbau bei " + p.toShortString());
            }
        }
        Runnable release = () -> {
            LoopTestBuild.remove(h.getLevel(), origin, name, r);
            for (long c : chunks) h.getLevel().setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        };
        int[] tick = {0}, level = {Integer.MAX_VALUE, Integer.MIN_VALUE}, heat = {0}, dry = {0};
        h.onEachTick(() -> {
            if (++tick[0] < from || tick[0] > to) return;
            if (!(h.getLevel().getBlockEntity(r.anchors().get("drive")) instanceof BigMachineBlockEntity drive)) return;
            int now = drive.tank("primary_cold_in").getFluidAmount();
            level[0] = Math.min(level[0], now);
            level[1] = Math.max(level[1], now);
            heat[0] = Math.max(heat[0], drive.core().heatPercent());
            if (drive.vesselDry()) dry[0]++;
        });
        h.runAfterDelay(FILL_TICKS, () -> {
            try {
                BigMachineBlockEntity drive = machine(h, r, "drive");
                if (drive.containmentOnTop() == null) throw new GameTestAssertException("Huelle nicht erkannt");
                if (drive.shafts()[0].headY() < 0) throw new GameTestAssertException("Brennstab nicht erkannt");
                if (drive.tank("primary_cold_in").getFluidAmount() <= 0) throw new GameTestAssertException("Notkuehlung fuellt den Antrieb nicht ueber die Huelle");
            } catch (RuntimeException e) {
                release.run();
                throw e;
            }
        });
        h.runAfterDelay(start, () -> {
            try {
                BigMachineBlockEntity drive = machine(h, r, "drive");
                for (int k = 0; k < drive.shafts().length; k++) drive.setShaftLocked(k, true);
                drive.setRodTarget(BigMachineBlockEntity.ROD_MAX);
            } catch (RuntimeException e) {
                release.run();
                throw e;
            }
        });
        h.runAfterDelay(to + 1, () -> {
            try {
                BigMachineBlockEntity drive = machine(h, r, "drive"), sg = machine(h, r, "sg"), pump = machine(h, r, "pump");
                String where = name + ": ";
                if (drive.reactorPower() <= 0) throw new GameTestAssertException(where + "Antrieb hat keine Leistung, Hitze " + drive.core().heatPercent() + " %");
                if (dry[0] > 0) throw new GameTestAssertException(where + "Durchfluss reisst ab, " + dry[0] + " Ticks");
                if (level[1] - level[0] > LEVEL_BAND) throw new GameTestAssertException(where + "Stand im Behaelter schwankt zwischen " + level[0] + " und " + level[1] + " mB");
                if (heat[0] > 20) throw new GameTestAssertException(where + "Kern wird zu heiss: " + heat[0] + " %");
                if (pump.lastMb() <= 0) throw new GameTestAssertException(where + "Hauptkuehlmittelpumpe foerdert nicht");
                if (sg.lastMb() <= 0) throw new GameTestAssertException(where + "Dampferzeuger macht keinen Dampf");
                if (!(h.getLevel().getBlockEntity(r.anchors().get("tur")) instanceof com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity tur) || !tur.isFormed())
                    throw new GameTestAssertException(where + "Turbine nicht aufgebaut");
                if (tur.lastMb() <= 0) throw new GameTestAssertException(where + "Turbine bekommt keinen Dampf");
                if (r.anchors().containsKey("pzr")) {
                    BigMachineBlockEntity pzr = machine(h, r, "pzr");
                    if (pzr.pressure() < 40 || pzr.pressure() > 60) throw new GameTestAssertException(where + "Druck liegt bei " + pzr.pressure() + " %");
                }
                if (r.anchors().containsKey("cond") && machine(h, r, "cond").lastMb() <= 0) throw new GameTestAssertException(where + "Kondensator bekommt keinen Abdampf");
                drive.scram();
                h.succeed();
            } finally {
                release.run();
            }
        });
    }

    @GameTest(template = "floor", batch = "loop_build_primary", timeoutTicks = 2300)
    public static void primaryLoopRunsSteady(GameTestHelper h) {
        steady(h, "primary", new BlockPos(60, 60, 40), 900);
    }

    @GameTest(template = "floor", batch = "loop_build_extended", timeoutTicks = 2300)
    public static void extendedLoopRunsSteady(GameTestHelper h) {
        steady(h, "extended", new BlockPos(60, 120, 40), 900);
    }

    @GameTest(template = "floor", batch = "loop_build_full", timeoutTicks = 2300)
    public static void fullLoopRunsSteady(GameTestHelper h) {
        steady(h, "full", new BlockPos(100, 180, 40), 900);
    }
}
