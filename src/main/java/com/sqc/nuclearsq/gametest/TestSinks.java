package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

final class TestSinks {
    private TestSinks() {}

    private static final int IN_X = 0, IN_Y = 0, IN_Z = 1;

    static BigMachineBlockEntity battery(GameTestHelper h, BlockPos cell, Direction toSource) {
        return batteryAt(h, h.absolutePos(cell), toSource);
    }

    static BigMachineBlockEntity batteryAt(GameTestHelper h, BlockPos in, Direction toSource) {
        MultiblockStructure s = BigMachine.BATTERY_SMALL.structure;
        BlockPos source = in.relative(toSource);
        for (int rot = 0; rot < 4; rot++) {
            BlockPos anchor = in.subtract(s.world(BlockPos.ZERO, rot, IN_X, IN_Y, IN_Z));
            List<MultiblockStructure.Cell> cells = s.cells(anchor, rot);
            if (cells.stream().anyMatch(c -> c.pos().equals(source))) continue;
            for (MultiblockStructure.Cell c : cells) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
            if (h.getLevel().getBlockEntity(anchor) instanceof BigMachineBlockEntity be) return be;
            throw new GameTestAssertException("Akku-Abnehmer ohne Controller bei " + anchor);
        }
        throw new GameTestAssertException("Kein Platz fuer einen Akku-Abnehmer bei " + in + " Richtung " + toSource);
    }
}
