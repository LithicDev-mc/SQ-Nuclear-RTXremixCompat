package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.AreaMonitorBlock;
import com.sqc.nuclearsq.data.AreaMonitorBlockEntity;
import com.sqc.nuclearsq.data.DataConsole;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.data.DataValue;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class AreaMonitorTests {
    private AreaMonitorTests() {}

    private static final String FLOOR = "floor";

    private static AreaMonitorBlockEntity place(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, DataRegistry.AREA_MONITOR.get().defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.NORTH));
        if (!(h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof AreaMonitorBlockEntity be)) {
            throw new GameTestAssertException("Kein Flaechenmessgeraet bei " + rel);
        }
        return be;
    }

    private static double value(DataSource s, String key) {
        DataValue v = s.value(key);
        if (v == null) throw new GameTestAssertException("Datenwert " + key + " fehlt");
        return v.value();
    }

    private static boolean alarmState(GameTestHelper h, BlockPos rel) {
        return h.getBlockState(rel).getValue(AreaMonitorBlock.ALARM);
    }

    @GameTest(template = FLOOR, batch = "area_monitor_measure", timeoutTicks = 140)
    public static void measuresSourceAndAlarms(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        for (int x = 2; x <= 6; x++) h.setBlock(new BlockPos(x, 4, 3), net.minecraft.world.level.block.Blocks.AIR);
        BlockPos rel = new BlockPos(3, 4, 3);
        BlockPos src = h.absolutePos(new BlockPos(5, 4, 3));
        AreaMonitorBlockEntity be = place(h, rel);
        BlockPos abs = h.absolutePos(rel);
        com.sqc.nuclearsq.radiation.Contamination.clean(lv, abs, (int) com.sqc.nuclearsq.radiation.Contamination.REACH, 1f);
        double base = be.measure();

        RadiationManager.emit(lv, src, 0.01);
        double rate = be.measure() - base;
        if (!(rate > RadiationValues.AMBIENT_SV_H * 10)) throw new GameTestAssertException("Messung nicht ueber Grundstrahlung: " + rate);
        if (rate < 0.001 || rate > 0.005) throw new GameTestAssertException("Messung unplausibel: " + rate + " Sv/h");

        DataSource s = DataSources.at(lv, abs);
        if (s == null) throw new GameTestAssertException("Flaechenmessgeraet ist keine Datenquelle");
        if (Math.abs(value(s, "dose_rate") - be.rate()) > 1e-12) throw new GameTestAssertException("dose_rate " + value(s, "dose_rate") + " != Messung " + be.rate());
        if (Math.abs(value(s, "dose_rate_usv") - be.rate() * 1e6) > 1e-6) throw new GameTestAssertException("dose_rate_usv passt nicht: " + value(s, "dose_rate_usv"));
        if (value(s, "peak") < rate) throw new GameTestAssertException("Hoechstwert unter der Messung");
        if (Math.abs(value(s, "threshold") - AreaMonitorBlockEntity.DEFAULT_THRESHOLD) > 1e-15) throw new GameTestAssertException("Standard-Schwelle falsch");

        if (s.set("threshold", 1.0) != null) throw new GameTestAssertException("threshold laesst sich nicht setzen");
        be.measure();
        s = DataSources.at(lv, abs);
        if (be.alarm() || alarmState(h, rel) || value(s, "alarm") != 0 || s.state() != DataSource.OK) {
            throw new GameTestAssertException("Alarm trotz Schwelle ueber dem Wert");
        }
        if (h.getBlockState(rel).getSignal(lv, abs, Direction.NORTH) != 0) throw new GameTestAssertException("Redstone ohne Alarm");

        if (s.set("threshold_usv", (base + rate / 2) * 1e6) != null) throw new GameTestAssertException("threshold_usv laesst sich nicht setzen");
        be.measure();
        s = DataSources.at(lv, abs);
        BlockState st = h.getBlockState(rel);
        if (!be.alarm() || !st.getValue(AreaMonitorBlock.ALARM) || value(s, "alarm") != 1 || s.state() != DataSource.ALARM) {
            throw new GameTestAssertException("Kein ALARM bei Schwelle unter dem Wert");
        }
        if (st.getSignal(lv, abs, Direction.NORTH) != 15) throw new GameTestAssertException("Kein Redstone-Signal 15 bei Alarm");
        if (s.set("threshold_usv", -1) == null) throw new GameTestAssertException("Negative Schwelle angenommen");

        h.runAfterDelay(100, () -> {
            if (be.rate() > base + rate / 2) throw new GameTestAssertException("Takt misst nicht neu: " + be.rate() + " (Untergrund " + base + ")");
            if (alarmState(h, rel)) throw new GameTestAssertException("Alarm bleibt ohne Quelle");
            if (be.peak() < rate) throw new GameTestAssertException("Hoechstwert verloren");
            DataSource d = DataSources.at(lv, abs);
            if (d.set("reset_peak", 1) != null || be.peak() > base + rate / 2) {
                throw new GameTestAssertException("reset_peak setzt den Hoechstwert nicht zurueck: " + be.peak());
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "area_monitor_net", timeoutTicks = 40)
    public static void joinsDataNet(GameTestHelper h) {
        BlockPos console = new BlockPos(2, 2, 2), monitor = new BlockPos(5, 2, 2);
        h.setBlock(console, DataRegistry.CONSOLE.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.WEST));
        h.setBlock(new BlockPos(3, 2, 2), DataRegistry.CABLE.get().defaultBlockState());
        h.setBlock(new BlockPos(4, 2, 2), DataRegistry.CABLE.get().defaultBlockState());
        AreaMonitorBlockEntity be = place(h, monitor);
        h.runAfterDelay(3, () -> {
            BlockPos c = h.absolutePos(console);
            List<DataSource> net = DataNet.scan(h.getLevel(), c);
            if (net.size() != 1 || !net.get(0).pos().equals(h.absolutePos(monitor))) {
                throw new GameTestAssertException("Flaechenmessgeraet fehlt im Netz: " + net.size());
            }
            if (!net.get(0).type().equals("area_monitor")) throw new GameTestAssertException("Art falsch: " + net.get(0).type());
            List<Component> err = DataConsole.run(h.getLevel(), c, null, "set 1 threshold_usv 50");
            if (Math.abs(be.threshold() - 50e-6) > 1e-12) throw new GameTestAssertException("Konsole setzt die Schwelle nicht: " + be.threshold() + " " + err);
            if (ModList.get().isLoaded("computercraft")) {
                String p = com.sqc.nuclearsq.compat.cc.DataCcCompat.peripheralType(h.getLevel(), h.absolutePos(monitor));
                if (!"sqc_machine".equals(p)) throw new GameTestAssertException("Flaechenmessgeraet: Peripheral " + p);
            }
            h.succeed();
        });
    }
}
