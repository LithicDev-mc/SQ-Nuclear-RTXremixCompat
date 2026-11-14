package com.sqc.nuclearsq.nanobot;

import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.storage.StorageAccess;
import com.sqc.nuclearsq.storage.StorageNet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class DroneItemPortBlock extends DataPanelBlock {
    public DroneItemPortBlock(Properties props) {
        super(props, false, 2, 0, 3, 14, 14, 14);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            StorageAccess a = StorageNet.access(level, pos);
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.port_status", a.racks(), a.onlineRacks(), a.drives(), a.used(), a.capacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
