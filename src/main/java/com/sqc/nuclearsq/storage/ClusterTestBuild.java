package com.sqc.nuclearsq.storage;

import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class ClusterTestBuild {
    private ClusterTestBuild() {}

    public static String machine(ServerLevel lv, BlockPos lo) {
        BigMachine m = BigMachine.COMPUTE_CLUSTER;
        BlockPos ctrl = lo.subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (var c : m.structure.cells(ctrl, 0)) {
            for (BlockPos p : BlockPos.betweenClosed(c.pos().offset(-2, 0, -2), c.pos().offset(2, 2, 2))) {
                if (p.getY() >= lo.getY()) lv.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
        for (var c : m.structure.cells(ctrl, 0)) lv.setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        BlockPos port = m.structure.world(ctrl, 0, m.port("data").x(), m.port("data").y(), m.port("data").z());
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (!lv.getBlockState(port.relative(d)).isAir() || !lv.getBlockState(port.relative(d, 2)).isAir()) continue;
            lv.setBlockAndUpdate(port.relative(d), DataRegistry.CABLE.get().defaultBlockState());
            lv.setBlockAndUpdate(port.relative(d, 2), DataRegistry.TERMINAL.get().defaultBlockState());
            break;
        }
        if (lv.getBlockEntity(ctrl) instanceof BigMachineBlockEntity be) {
            be.energy().add(be.energy().getMaxEnergyStored());
            be.tank("water_in").fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        }
        return "Maschine Rechencluster steht, Controller bei " + ctrl.toShortString() + ". Strom und Wasser sind gefuellt.";
    }
}
