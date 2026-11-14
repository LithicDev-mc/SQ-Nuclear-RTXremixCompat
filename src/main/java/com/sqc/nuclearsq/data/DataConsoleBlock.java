package com.sqc.nuclearsq.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class DataConsoleBlock extends DataPanelBlock {
    public DataConsoleBlock(Properties props) {
        super(props, false, 0, 0, 0, 16, 15, 16);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) DataConsoleMenu.open(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
