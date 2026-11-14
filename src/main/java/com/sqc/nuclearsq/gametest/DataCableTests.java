package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.transport.CableBlock;
import com.sqc.nuclearsq.transport.ConduitBlock;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.ConduitSide;
import com.sqc.nuclearsq.transport.TransportRegistry;
import com.sqc.nuclearsq.transport.TransportTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class DataCableTests {
    private DataCableTests() {}

    private static final String FLOOR = "floor";

    private static void expect(Level level, BlockPos p, Direction d, ConduitSide want) {
        ConduitSide got = ConduitBlock.sideAt(level, p, d);
        if (got != want) throw new GameTestAssertException("Seite " + d + " bei " + p + ": " + got + " statt " + want);
    }

    @GameTest(template = FLOOR, batch = "data_cable_sides", timeoutTicks = 40)
    public static void connectsToDataOnly(GameTestHelper h) {
        Level lv = h.getLevel();
        BlockState data = DataRegistry.CABLE.get().defaultBlockState();
        BlockPos a = h.absolutePos(new BlockPos(2, 2, 2)), b = a.east(), power = b.east(), term = a.west();
        lv.setBlockAndUpdate(term, DataRegistry.TERMINAL.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.NORTH));
        lv.setBlockAndUpdate(a, data);
        lv.setBlockAndUpdate(b, data);
        lv.setBlockAndUpdate(power, TransportRegistry.CABLES.get(TransportTier.BASIC).get().defaultBlockState());
        h.runAfterDelay(5, () -> {
            if (lv.getBlockState(a) != data || !data.hasProperty(CableBlock.MOUNT)) throw new GameTestAssertException("Blockstate geaendert oder ohne Montage");
            expect(lv, a, Direction.EAST, ConduitSide.ON);
            expect(lv, b, Direction.WEST, ConduitSide.ON);
            expect(lv, a, Direction.WEST, ConduitSide.PORT);
            expect(lv, b, Direction.EAST, ConduitSide.NONE);
            expect(lv, power, Direction.WEST, ConduitSide.NONE);
            if (lv.getCapability(Capabilities.EnergyStorage.BLOCK, a, null) != null) throw new GameTestAssertException("Datenkabel nimmt Strom an");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "data_cable_cut", timeoutTicks = 40)
    public static void wrenchedSideCutsTheNet(GameTestHelper h) {
        Level lv = h.getLevel();
        BlockPos term = h.absolutePos(new BlockPos(1, 2, 2));
        lv.setBlockAndUpdate(term, DataRegistry.TERMINAL.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.NORTH));
        for (int i = 1; i <= 3; i++) lv.setBlockAndUpdate(term.east(i), DataRegistry.CABLE.get().defaultBlockState());
        h.runAfterDelay(5, () -> {
            int all = DataNet.trace(lv, List.of(term)).cables().size();
            if (all != 3) throw new GameTestAssertException("Netz hat " + all + " statt 3 Kabel");
            ConduitBlockEntity mid = (ConduitBlockEntity) lv.getBlockEntity(term.east(2));
            if (mid.cycle(Direction.EAST, false) != ConduitBlockEntity.OFF) throw new GameTestAssertException("Seite nicht getrennt");
            DataRegistry.CABLE.get().refresh(lv, term.east(2));
            DataRegistry.CABLE.get().refresh(lv, term.east(3));
            expect(lv, term.east(2), Direction.EAST, ConduitSide.NONE);
            expect(lv, term.east(3), Direction.WEST, ConduitSide.NONE);
            int cut = DataNet.trace(lv, List.of(term)).cables().size();
            if (cut != 2) throw new GameTestAssertException("Getrenntes Netz hat " + cut + " statt 2 Kabel");
            mid.cycle(Direction.EAST, false);
            int back = DataNet.trace(lv, List.of(term)).cables().size();
            if (back != 3) throw new GameTestAssertException("Wieder verbunden: " + back + " statt 3 Kabel");
            h.succeed();
        });
    }
}
