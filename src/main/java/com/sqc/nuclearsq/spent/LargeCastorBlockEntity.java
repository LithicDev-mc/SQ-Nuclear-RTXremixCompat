package com.sqc.nuclearsq.spent;

import com.sqc.nuclearsq.registry.SpentFuelRegistry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class LargeCastorBlockEntity extends CastorCaskBlockEntity {
    public static final int SLOTS = 36;

    public LargeCastorBlockEntity(BlockPos pos, BlockState state) {
        super(SpentFuelRegistry.LARGE_CASTOR_BE.get(), pos, state, SLOTS);
    }

    @Override
    protected List<BlockPos> cells() {
        List<BlockPos> l = new ArrayList<>(LargeCastorBlock.PARTS);
        for (int p = 0; p < LargeCastorBlock.PARTS; p++) {
            l.add(worldPosition.offset(LargeCastorBlock.dx(p), LargeCastorBlock.dy(p), LargeCastorBlock.dz(p)));
        }
        return l;
    }
}
