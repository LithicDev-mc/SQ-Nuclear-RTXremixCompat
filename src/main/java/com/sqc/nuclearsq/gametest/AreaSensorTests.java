package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.AreaMonitorBlock;
import com.sqc.nuclearsq.data.AreaMonitorBlockEntity;
import com.sqc.nuclearsq.data.AreaSensorBlockEntity;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.data.DataValue;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class AreaSensorTests {
    private AreaSensorTests() {}

    private static final String FLOOR = "floor";
    private static final double MIN_GAIN = 5;

    private static <T extends AreaMonitorBlockEntity> T place(GameTestHelper h, Block b, BlockPos rel, Class<T> type) {
        h.setBlock(rel, b.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.NORTH));
        if (!(type.isInstance(h.getLevel().getBlockEntity(h.absolutePos(rel))))) throw new GameTestAssertException("Kein Sensor bei " + rel);
        return type.cast(h.getLevel().getBlockEntity(h.absolutePos(rel)));
    }

    private static double value(DataSource s, String key) {
        DataValue v = s.value(key);
        if (v == null) throw new GameTestAssertException("Datenwert " + key + " fehlt");
        return v.value();
    }

    private static final double SRC_SV_H = 1.0;

    @GameTest(template = FLOOR, batch = "area_sensor_measure", timeoutTicks = 40)
    public static void findsSourceInRadius(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++) {
            for (int y = 3; y <= 9; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        BlockPos rel = new BlockPos(3, 4, 7), spotRel = new BlockPos(3, 4, 8);
        BlockPos src = h.absolutePos(new BlockPos(8, 4, 7));
        AreaSensorBlockEntity area = place(h, DataRegistry.AREA_SENSOR.get(), rel, AreaSensorBlockEntity.class);
        AreaMonitorBlockEntity spot = place(h, DataRegistry.AREA_MONITOR.get(), spotRel, AreaMonitorBlockEntity.class);
        BlockPos abs = h.absolutePos(rel);
        Contamination.clean(lv, abs, 12, 1f);
        if (area.radius() != RadiationValues.AREA_SENSOR_RADIUS) throw new GameTestAssertException("Standard-Umkreis " + area.radius());
        double baseArea = area.measure(), baseSpot = spot.measure();

        RadiationManager.emit(lv, src, SRC_SV_H);
        double a = area.measure() - baseArea, s = spot.measure() - baseSpot;
        String got = String.format("Flaeche %.3g, Ort %.3g, Mittel %.3g", a, s, area.average());
        if (!(s > 0 && a >= s * MIN_GAIN)) throw new GameTestAssertException("Flaechensensor nicht deutlich hoeher: " + got);
        if (!(area.average() < area.rate() && area.average() > RadiationValues.AMBIENT_SV_H)) throw new GameTestAssertException("Mittelwert unplausibel: " + got);
        if (area.hottest().distSqr(src) > 4) throw new GameTestAssertException("Heisseste Stelle " + area.hottest() + " weit weg von der Quelle " + src);

        DataSource d = DataSources.at(lv, abs);
        if (d == null) throw new GameTestAssertException("Flaechensensor ist keine Datenquelle");
        if (!d.type().equals("area_sensor")) throw new GameTestAssertException("Art falsch: " + d.type());
        if (Math.abs(value(d, "dose_rate_max_usv") - area.rate() * 1e6) > 1e-6) throw new GameTestAssertException("dose_rate_max_usv passt nicht");
        if (Math.abs(value(d, "dose_rate_avg_usv") - area.average() * 1e6) > 1e-6) throw new GameTestAssertException("dose_rate_avg_usv passt nicht");
        if (value(d, "hottest_x") != area.hottest().getX() || value(d, "hottest_z") != area.hottest().getZ()) throw new GameTestAssertException("hottest_* passt nicht");
        if (value(d, "radius") != RadiationValues.AREA_SENSOR_RADIUS) throw new GameTestAssertException("radius passt nicht");

        if (d.set("threshold", (baseArea + a) / 2) != null) throw new GameTestAssertException("threshold laesst sich nicht setzen");
        RadiationManager.emit(lv, src, SRC_SV_H);
        area.measure();
        if (!area.alarm() || !h.getBlockState(rel).getValue(AreaMonitorBlock.ALARM)) throw new GameTestAssertException("Kein Alarm nach Hoechstwert: " + got);

        if (d.set("radius", 2) != null) throw new GameTestAssertException("radius laesst sich nicht setzen");
        RadiationManager.emit(lv, src, SRC_SV_H);
        double small = area.measure() - baseArea;
        if (area.radius() != 2 || !(small < a / MIN_GAIN)) throw new GameTestAssertException("Umkreis 2 sieht die Quelle noch: " + small + " (vorher " + a + ")");
        d.set("radius", 100);
        if (area.radius() != AreaSensorBlockEntity.MAX_RADIUS) throw new GameTestAssertException("Umkreis nicht begrenzt: " + area.radius());
        if (d.set("radius", -1) == null) throw new GameTestAssertException("Negativer Umkreis angenommen");
        if (ModList.get().isLoaded("computercraft")) {
            String p = com.sqc.nuclearsq.compat.cc.DataCcCompat.peripheralType(lv, abs);
            if (!"sqc_machine".equals(p)) throw new GameTestAssertException("Flaechensensor: Peripheral " + p);
        }
        h.succeed();
    }
}
