package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.ControlDesk;
import com.sqc.nuclearsq.data.ControlWallBlock;
import com.sqc.nuclearsq.data.DataConsole;
import com.sqc.nuclearsq.data.DataInstrumentBlockEntity;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class DataNetTests {
    private DataNetTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein Controller");
        return be;
    }

    private static Direction freeSide(GameTestHelper h, BlockPos ctrl) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (h.getLevel().getBlockState(ctrl.relative(d)).isAir() && h.getLevel().getBlockState(ctrl.relative(d, 2)).isAir()
                    && h.getLevel().getBlockState(ctrl.relative(d, 3)).isAir()) return d;
        }
        throw new GameTestAssertException("Kein Platz am Controller");
    }

    private static String text(List<Component> out) {
        StringBuilder b = new StringBuilder();
        for (Component c : out) b.append(c.getString()).append('\n');
        return b.toString();
    }

    @GameTest(template = FLOOR, batch = "data_console", timeoutTicks = 60)
    public static void consoleReadsAndSetsOverCable(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(4, 4, 4));
        BlockPos ctrl = be.getBlockPos();
        Direction d = freeSide(h, ctrl);
        h.getLevel().setBlockAndUpdate(ctrl.relative(d), DataRegistry.CABLE.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(ctrl.relative(d, 2), DataRegistry.CABLE.get().defaultBlockState());
        BlockPos console = ctrl.relative(d, 3);
        h.getLevel().setBlockAndUpdate(console, DataRegistry.CONSOLE.get().defaultBlockState().setValue(DataPanelBlock.FACING, d));
        h.runAfterDelay(5, () -> {
            List<DataSource> net = DataNet.scan(h.getLevel(), console);
            if (net.size() != 1 || !net.get(0).pos().equals(ctrl)) throw new GameTestAssertException("Kabel findet die Pumpe nicht: " + net.size());
            String list = text(DataConsole.run(h.getLevel(), console, null, "list"));
            if (!list.contains(" 1 ")) throw new GameTestAssertException("list zeigt keine Anlage: " + list);
            if (!be.enabled()) throw new GameTestAssertException("Pumpe ist nicht eingeschaltet");
            DataConsole.run(h.getLevel(), console, null, "set 1 enable 0");
            if (be.enabled()) throw new GameTestAssertException("set 1 enable 0 schaltet nicht aus");
            DataConsole.run(h.getLevel(), console, null, "set pump enable 1");
            if (!be.enabled()) throw new GameTestAssertException("set pump enable 1 schaltet nicht ein (Name statt Nummer)");
            List<Component> bad = DataConsole.run(h.getLevel(), console, null, "set 1 heat 5");
            if (bad.isEmpty() || !be.enabled()) throw new GameTestAssertException("Nicht setzbarer Wert wird angenommen");
            List<Component> nan = DataConsole.run(h.getLevel(), console, null, "set 1 enable abc");
            if (nan.isEmpty() || !be.enabled()) throw new GameTestAssertException("Keine Zahl wird angenommen");
            if (DataConsole.run(h.getLevel(), console, null, "info 7").isEmpty()) throw new GameTestAssertException("Falsche Nummer ohne Meldung");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "data_instrument", timeoutTicks = 60)
    public static void instrumentShowsValue(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(4, 4, 4));
        BlockPos ctrl = be.getBlockPos();
        Direction d = freeSide(h, ctrl);
        h.getLevel().setBlockAndUpdate(ctrl.relative(d), DataRegistry.CABLE.get().defaultBlockState());
        BlockPos gauge = ctrl.relative(d, 2);
        h.getLevel().setBlockAndUpdate(gauge, DataRegistry.GAUGE.get().defaultBlockState().setValue(DataPanelBlock.FACING, d));
        h.runAfterDelay(5, () -> {
            if (!(h.getLevel().getBlockEntity(gauge) instanceof DataInstrumentBlockEntity in)) throw new GameTestAssertException("Instrument ohne BlockEntity");
            in.refresh();
            if (!in.linked() || !ctrl.equals(in.source())) throw new GameTestAssertException("Instrument zeigt keinen Wert der Pumpe");
            if (in.key().isEmpty() || in.text().isEmpty()) throw new GameTestAssertException("Instrument ohne Wert: '" + in.key() + "'");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "data_instrument", timeoutTicks = 60)
    public static void multiDisplayShowsRows(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(4, 4, 4));
        BlockPos ctrl = be.getBlockPos();
        Direction d = freeSide(h, ctrl);
        h.getLevel().setBlockAndUpdate(ctrl.relative(d), DataRegistry.CABLE.get().defaultBlockState());
        BlockPos panel = ctrl.relative(d, 2);
        h.getLevel().setBlockAndUpdate(panel, DataRegistry.MULTI.get().defaultBlockState().setValue(DataPanelBlock.FACING, d));
        h.runAfterDelay(5, () -> {
            if (!(h.getLevel().getBlockEntity(panel) instanceof com.sqc.nuclearsq.data.DataMultiBlockEntity in)) throw new GameTestAssertException("Mehrfachanzeige ohne BlockEntity");
            in.refresh();
            var t = in.tile();
            if (t == null || !ctrl.equals(in.source())) throw new GameTestAssertException("Mehrfachanzeige zeigt die Pumpe nicht");
            if (t.lines().size() != com.sqc.nuclearsq.data.DataMultiBlockEntity.ROWS || t.lines().get(0).text().isEmpty()) throw new GameTestAssertException("Mehrfachanzeige ohne Zeilen");
            String before = in.key(0);
            new com.sqc.nuclearsq.data.DataMultiMenu(0, panel).clickMenuButton(AuditFixTests.testPlayer(h), 3);
            if (before.isEmpty() || before.equals(in.key(0))) throw new GameTestAssertException("Pfeil wechselt den Wert nicht: " + before);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "data_wall", timeoutTicks = 20)
    public static void wallNeedsThreeByTwoToFiveByThree(GameTestHelper h) {
        Block wall = DataRegistry.WALL.get();
        for (int x = 1; x <= 2; x++) for (int y = 2; y <= 3; y++) h.setBlock(new BlockPos(x, y, 2), wall.defaultBlockState().setValue(DataPanelBlock.FACING, Direction.NORTH));
        if (ControlWallBlock.layout(h.getLevel(), h.absolutePos(new BlockPos(1, 2, 2))) != null) throw new GameTestAssertException("2x2 gilt als Wand");
        for (int x = 1; x <= 3; x++) for (int y = 2; y <= 3; y++) h.setBlock(new BlockPos(x, y, 6), wall.defaultBlockState().setValue(DataPanelBlock.FACING, Direction.NORTH));
        ControlWallBlock.Layout l = ControlWallBlock.layout(h.getLevel(), h.absolutePos(new BlockPos(2, 3, 6)));
        if (l == null || l.w() != 3 || l.h() != 2) throw new GameTestAssertException("3x2 wird nicht erkannt: " + l);
        for (int x = 1; x <= 6; x++) for (int y = 2; y <= 3; y++) h.setBlock(new BlockPos(x, y, 10), wall.defaultBlockState().setValue(DataPanelBlock.FACING, Direction.NORTH));
        if (ControlWallBlock.layout(h.getLevel(), h.absolutePos(new BlockPos(1, 2, 10))) != null) throw new GameTestAssertException("6x2 gilt als Wand");
        for (int w = ControlWallBlock.MIN_W; w <= ControlWallBlock.MAX_W; w++) {
            for (int hh = ControlWallBlock.MIN_H; hh <= ControlWallBlock.MAX_H; hh++) {
                ControlWallBlock.Grid one = ControlWallBlock.grid(w, hh, 1), many = ControlWallBlock.grid(w, hh, 12);
                if (one.cols() * one.rows() < 1 || many.cols() * many.rows() < one.cols() * one.rows()) {
                    throw new GameTestAssertException("Raster " + w + "x" + hh + ": " + one + " / " + many);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "data_desk", timeoutTicks = 60)
    public static void controlDeskIsTerminalWithoutItself(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.CONTROL_DESK, new BlockPos(4, 4, 4));
        h.runAfterDelay(5, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Steuerpult wird nicht als aufgebaut erkannt");
            if (be.desk() == null) throw new GameTestAssertException("Steuerpult ohne Pult-Daten");
            if (!DataNet.isTerminal(h.getLevel(), be.getBlockPos())) throw new GameTestAssertException("Steuerpult zaehlt nicht als Bediengeraet");
            if (!DataNet.scan(h.getLevel(), be.getBlockPos()).isEmpty()) throw new GameTestAssertException("Steuerpult findet sich selbst im Netz");
            if (ControlDesk.emergencyStop(h.getLevel(), be) != 0) throw new GameTestAssertException("Not-Aus ohne Reaktor meldet Abschaltungen");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "data_cap", timeoutTicks = 60)
    public static void cableLimitIsReported(GameTestHelper h) {
        if (DataNet.MAX_CABLES != 8192) throw new GameTestAssertException("Kabelgrenze " + DataNet.MAX_CABLES);
        BlockPos start = h.absolutePos(new BlockPos(1, 2, 4));
        h.getLevel().setBlockAndUpdate(start, DataRegistry.CONSOLE.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.EAST));
        for (int i = 1; i <= 10; i++) h.getLevel().setBlockAndUpdate(start.east(i), DataRegistry.CABLE.get().defaultBlockState());
        h.runAfterDelay(5, () -> {
            DataNet.Result ok = DataNet.scanChecked(h.getLevel(), List.of(start), 10);
            if (ok.capped() || ok.incomplete()) throw new GameTestAssertException("10 Kabel bei Grenze 10 gelten als zu gross");
            DataNet.Result cut = DataNet.scanChecked(h.getLevel(), List.of(start), 5);
            if (!cut.capped() || !cut.incomplete()) throw new GameTestAssertException("10 Kabel bei Grenze 5 werden nicht gemeldet");
            if (DataNet.capTile(start).stateText().getString().isEmpty()) throw new GameTestAssertException("Warnkachel ohne Text");
            h.succeed();
        });
    }
}
