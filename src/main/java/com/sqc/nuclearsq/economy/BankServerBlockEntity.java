package com.sqc.nuclearsq.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BankServerBlockEntity extends BlockEntity {
    public BankServerBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyRegistry.SERVER_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) BankNet.SERVERS.add(GlobalPos.of(level.dimension(), worldPosition));
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
        if (level != null) BankNet.SERVERS.remove(GlobalPos.of(level.dimension(), worldPosition));
    }
}
