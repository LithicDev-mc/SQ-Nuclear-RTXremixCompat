package com.sqc.nuclearsq.compat.powergrid;

import net.minecraft.world.level.block.entity.BlockEntityType;
import org.patryk3211.powergrid.electricity.febridge.FEInverterBlock;
import org.patryk3211.powergrid.electricity.febridge.FEInverterBlockEntity;

public class GridTerminalBlock extends FEInverterBlock {

    public GridTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends FEInverterBlockEntity> getBlockEntityType() {
        return PowerGridRegistry.TERMINAL_BE.get();
    }
}
