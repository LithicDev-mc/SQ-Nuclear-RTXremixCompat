package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.ControlWallBlockEntity;
import com.sqc.nuclearsq.data.DataDisplay;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.data.DataValue;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class MonitorInfoTests {
    private MonitorInfoTests() {}

    private static final String FLOOR = "floor";
    private static final Set<String> SWITCHES = Set.of("enable", "run", "rod_mode", "channel", "overheated", "manual");

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein Controller");
        return be;
    }

    private static List<String> keys(List<DataValue> values) {
        return values.stream().map(DataValue::key).toList();
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static void noSwitches(String who, List<DataValue> shown) {
        for (DataValue v : shown) {
            if (SWITCHES.contains(v.key())) throw new GameTestAssertException(who + ": Schalter auf dem Bildschirm: " + keys(shown));
        }
    }

    @GameTest(template = FLOOR, batch = "monitor_info", timeoutTicks = 60)
    public static void rodDriveShowsPowerHeatFuelFirst(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        while (be.energy().receiveEnergy(1000, false) > 0) { }
        be.tank("hydraulic_in").fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        h.runAfterDelay(5, () -> {
            DataSource s = DataSources.of(be);
            if (s == null) throw new GameTestAssertException("Brennstabantrieb ohne Datenquelle");
            List<String> shown = keys(s.display(6));
            if (shown.size() < 3 || !shown.subList(0, 3).equals(List.of("power", "heat", "fuel"))) {
                throw new GameTestAssertException("Pult zeigt nicht Leistung, Hitze, Brennstoff zuerst: " + shown);
            }
            noSwitches("Brennstabantrieb", s.display(6));
            ControlWallBlockEntity.Tile tile = ControlWallBlockEntity.tile(s, 3);
            if (tile.lines().size() != 3) throw new GameTestAssertException("Leitstand-Kachel ohne drei Werte: " + tile.lines().size());
            String state = key(s.stateText());
            if (!Set.of("data.nuclearsq.state.no_rods", "data.nuclearsq.state.no_flow").contains(state) || s.state() == DataSource.OK) {
                throw new GameTestAssertException("Antrieb ohne Stab meldet keine Stoerung: " + state + " / " + s.state());
            }
            List<DataValue> all = DataDisplay.order(s);
            if (!all.get(0).key().equals("power") || !SWITCHES.contains(all.get(all.size() - 1).key())) {
                throw new GameTestAssertException("Instrument-Reihenfolge falsch: " + keys(all));
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "monitor_info", timeoutTicks = 60)
    public static void batteryShowsRatesAndCharging(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.BATTERY_SMALL, new BlockPos(3, 2, 3));
        h.runAfterDelay(5, () -> {
            DataSource s = DataSources.of(be);
            if (s == null) throw new GameTestAssertException("Akku ohne Datenquelle");
            List<String> shown = keys(s.display(6));
            if (!shown.contains("in_rate") || !shown.contains("out_rate") || !shown.contains("energy")) {
                throw new GameTestAssertException("Akku zeigt nicht Aufnahme, Abgabe und Strom: " + shown);
            }
            noSwitches("Akku", s.display(6));
            String state = key(s.stateText());
            if (!Set.of("data.nuclearsq.state.idle", "data.nuclearsq.state.charging", "data.nuclearsq.state.discharging",
                    "gui.nuclearsq.device.big.state.off").contains(state)) {
                throw new GameTestAssertException("Akku-Zustand ohne Laden/Entladen: " + state);
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "monitor_info", timeoutTicks = 60)
    public static void pumpShowsThroughputWithoutSwitch(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(4, 4, 4));
        h.runAfterDelay(5, () -> {
            DataSource s = DataSources.of(be);
            if (s == null) throw new GameTestAssertException("Pumpe ohne Datenquelle");
            List<DataValue> shown = s.display(6);
            noSwitches("Pumpe", shown);
            if (!keys(shown).contains("rate")) throw new GameTestAssertException("Pumpe zeigt keinen Durchsatz: " + keys(shown));
            if (s.value("enable") == null) throw new GameTestAssertException("enable fehlt fuer Konsole/Computer");
            h.succeed();
        });
    }
}
