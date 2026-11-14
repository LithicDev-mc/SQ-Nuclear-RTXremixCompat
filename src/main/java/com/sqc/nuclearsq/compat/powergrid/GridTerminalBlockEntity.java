package com.sqc.nuclearsq.compat.powergrid;

import com.sqc.nuclearsq.machine.big.TranslatorSink;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.patryk3211.powergrid.electricity.febridge.FEInverterBlock;
import org.patryk3211.powergrid.electricity.febridge.forge.FEInverterBlockEntityImpl;

public class GridTerminalBlockEntity extends FEInverterBlockEntityImpl implements TranslatorSink {

    public GridTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(PowerGridRegistry.TERMINAL_BE.get(), pos, state);
    }

    @Override
    public int acceptTranslated(BlockPos from, int fe, boolean simulate) {
        if (fe <= 0 || from.distManhattan(worldPosition) != 1) return 0;
        IEnergyStorage buf = getEnergyStorage(getBlockState().getValue(FEInverterBlock.FACING));
        return buf == null ? 0 : buf.receiveEnergy(fe, simulate);
    }
}
