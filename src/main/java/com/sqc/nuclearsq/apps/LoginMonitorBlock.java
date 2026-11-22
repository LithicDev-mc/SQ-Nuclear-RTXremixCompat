package com.sqc.nuclearsq.apps;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class LoginMonitorBlock extends AppMonitorBlock {
    public LoginMonitorBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) {
                boolean was = AppSessions.loggedIn(sp);
                AppSessions.logout(sp);
                sp.displayClientMessage(Component.translatable(was ? "message.nuclearsq.app_login.logout" : "message.nuclearsq.app_login.not_in"), true);
            } else {
                LoginMonitor.send(sp, pos, "", false);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LoginMonitorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AppRegistry.LOGIN_MONITOR_BE.get()) return null;
        return (lvl, p, st, be) -> ((LoginMonitorBlockEntity) be).serverTick();
    }
}
