package com.sqc.nuclearsq.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BankModemBlockEntity extends BlockEntity {
    public BankModemBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyRegistry.MODEM_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) BankNet.MODEMS.add(GlobalPos.of(level.dimension(), worldPosition));
    }

    @Override
    public void onChunkUnloaded() {
        forget();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        forget();
        super.setRemoved();
    }

    private void forget() {
        if (level != null) BankNet.MODEMS.remove(GlobalPos.of(level.dimension(), worldPosition));
    }
}
