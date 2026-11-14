package com.sqc.nuclearsq.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BankServerBlock extends Block implements EntityBlock {
    public BankServerBlock(Properties props) {
        super(props);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BankServerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getServer() != null) {
            EconomyData d = EconomyData.get(level.getServer());
            player.displayClientMessage(Component.translatable("message.nuclearsq.bank_server.info", d.accountCount(), d.openOfferCount()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
