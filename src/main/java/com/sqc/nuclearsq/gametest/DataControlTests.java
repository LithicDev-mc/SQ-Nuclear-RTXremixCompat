package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataControlBlock;
import com.sqc.nuclearsq.data.DataControlBlockEntity;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class DataControlTests {
    private DataControlTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("7a1c2e3f-4b5d-4e6f-8a90-b1c2d3e4f5a6"), "nsq_controls");

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein Controller");
        return be;
    }

    private static BlockPos wire(GameTestHelper h, BigMachineBlockEntity be, Block control) {
        java.util.List<BlockPos> starts = new java.util.ArrayList<>();
        starts.add(be.getBlockPos());
        for (MultiblockStructure.Cell c : be.machine().structure.cells(be.getBlockPos(), 0)) {
            if (h.getLevel().getBlockEntity(c.pos()) instanceof com.sqc.nuclearsq.machine.big.BigPortBlockEntity) starts.add(c.pos());
        }
        for (BlockPos p : starts) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!h.getLevel().getBlockState(p.relative(d)).isAir() || !h.getLevel().getBlockState(p.relative(d, 2)).isAir()) continue;
                h.getLevel().setBlockAndUpdate(p.relative(d), DataRegistry.CABLE.get().defaultBlockState());
                BlockPos at = p.relative(d, 2);
                h.getLevel().setBlockAndUpdate(at, control.defaultBlockState().setValue(DataPanelBlock.FACING, d));
                return at;
            }
        }
        throw new GameTestAssertException(be.machine().id + ": kein Platz fuer ein Kabel");
    }

    private static DataControlBlockEntity control(GameTestHelper h, BlockPos at) {
        if (!(h.getLevel().getBlockEntity(at) instanceof DataControlBlockEntity c)) throw new GameTestAssertException("Element ohne BlockEntity");
        return c;
    }

    private static void bind(DataControlBlockEntity c, ServerPlayer sp, String key) {
        for (int i = 0; i < 20 && !key.equals(c.key()); i++) c.cycle(sp);
        if (!key.equals(c.key())) throw new GameTestAssertException("Element laesst sich nicht auf " + key + " stellen: " + c.key());
    }

    @GameTest(template = FLOOR, batch = "data_controls", timeoutTicks = 100)
    public static void leverSwitchesPumpAndFollowsIt(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(4, 4, 4));
        BlockPos at = wire(h, be, DataRegistry.LEVER.get());
        h.runAfterDelay(5, () -> {
            ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), PROFILE);
            DataControlBlockEntity c = control(h, at);
            bind(c, sp, "enable");
            if (!be.getBlockPos().equals(c.source())) throw new GameTestAssertException("Hebel zeigt nicht auf die Pumpe");
            BlockState st = h.getLevel().getBlockState(at);
            if (!st.getValue(DataControlBlock.ON)) throw new GameTestAssertException("Hebel folgt nicht: Pumpe ist ein, Hebel unten");
            ((DataControlBlock) st.getBlock()).setState(h.getLevel(), at, st.setValue(DataControlBlock.ON, false));
            c.lever(sp, false);
            if (be.enabled()) throw new GameTestAssertException("Hebel schaltet die Pumpe nicht aus");
            if (((DataControlBlock) DataRegistry.LEVER.get()).signal(h.getLevel().getBlockState(at)) != 0) throw new GameTestAssertException("Hebel unten gibt Redstone");
            be.setEnabled(true);
            h.runAfterDelay(DataControlBlockEntity.PERIOD + 2, () -> {
                if (!h.getLevel().getBlockState(at).getValue(DataControlBlock.ON)) throw new GameTestAssertException("Hebel folgt dem Einschalten nicht");
                if (((DataControlBlock) DataRegistry.LEVER.get()).signal(h.getLevel().getBlockState(at)) != 15) throw new GameTestAssertException("Hebel oben ohne Redstone 15");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "data_controls", timeoutTicks = 60)
    public static void sliderSetsRodTarget(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        h.runAfterDelay(5, () -> {
            BlockPos at = wire(h, be, DataRegistry.SLIDER.get());
            ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), PROFILE);
            be.setTerminalMode(true);
            DataControlBlockEntity c = control(h, at);
            bind(c, sp, "rod");
            c.slide(sp, 10);
            if (be.rodTarget() != 10) throw new GameTestAssertException("Schieberegler stellt das Stab-Soll nicht: " + be.rodTarget());
            if (h.getLevel().getBlockState(at).getValue(DataControlBlock.LEVEL) != 10) throw new GameTestAssertException("Regler steht nicht auf 10");
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockState st = DataRegistry.SLIDER.get().defaultBlockState().setValue(DataPanelBlock.FACING, d);
                Direction right = d.getCounterClockWise();
                double lx = 0.5 + right.getStepX() * 0.5, lz = 0.5 + right.getStepZ() * 0.5;
                double rx = 0.5 - right.getStepX() * 0.5, rz = 0.5 - right.getStepZ() * 0.5;
                int left = DataControlBlock.levelAt(st, BlockPos.ZERO, rx, rz), rgt = DataControlBlock.levelAt(st, BlockPos.ZERO, lx, lz);
                if (left != 0 || rgt != 15) throw new GameTestAssertException("Schiene " + d + ": links " + left + ", rechts " + rgt);
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "data_controls", timeoutTicks = 60)
    public static void buttonTriggersScram(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        while (be.energy().receiveEnergy(1000, false) > 0) { }
        h.runAfterDelay(5, () -> {
            BlockPos at = wire(h, be, DataRegistry.BUTTON.get());
            ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), PROFILE);
            be.setTerminalMode(true);
            be.setRodTarget(12);
            DataControlBlockEntity c = control(h, at);
            bind(c, sp, "scram");
            c.press(sp);
            if (be.rodTarget() != 0) throw new GameTestAssertException("Knopf loest keinen Not-Aus aus: Soll " + be.rodTarget());
            h.succeed();
        });
    }
}
