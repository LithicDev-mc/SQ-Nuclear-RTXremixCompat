package com.sqc.nuclearsq.data;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class DataTerminalBlock extends DataPanelBlock {
    public DataTerminalBlock(Properties props) {
        super(props, false, 1, 0, 2, 15, 14, 14);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && ComputeNet.allow(sp, level, pos)) {
            DataNet.Result scan = DataNet.scanChecked(level, List.of(pos));
            if (scan.capped()) sp.displayClientMessage(Component.translatable("data.nuclearsq.cap.msg", DataNet.MAX_CABLES).withStyle(net.minecraft.ChatFormatting.RED), true);
            if (scan.sources().isEmpty()) sp.displayClientMessage(Component.translatable("data.nuclearsq.msg.none"), true);
            else DataTerminalMenu.open(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void open(ServerPlayer sp, BlockPos terminal, DataSource s) {
        RemoteAccess.grant(sp, s.pos(), terminal);
        s.open(sp);
    }

    public static void select(ServerPlayer sp, BlockPos terminal, BlockPos source) {
        DataSource s = DataNet.find(sp.level(), terminal, source);
        if (s == null) {
            sp.closeContainer();
            sp.displayClientMessage(Component.translatable("data.nuclearsq.msg.gone"), true);
            return;
        }
        open(sp, terminal, s);
    }
}
