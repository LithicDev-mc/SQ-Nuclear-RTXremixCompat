package com.sqc.nuclearsq.storage;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.data.DataPanelBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WifiAdapterBlock extends DataPanelBlock implements EntityBlock {
    public WifiAdapterBlock(Properties props) {
        super(props, false, 3, 0, 3, 13, 16, 13);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WifiAdapterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != StorageRegistry.WIFI_ADAPTER_BE.get()) return null;
        return (l, p, s, be) -> WifiAdapterBlockEntity.tick(l, p, s, (WifiAdapterBlockEntity) be);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        boolean terminal = stack.getItem() instanceof WifiTerminalItem, antenna = stack.is(StorageRegistry.WIFI_ANTENNA.get());
        if (!terminal && !antenna) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof WifiAdapterBlockEntity be)) return ItemInteractionResult.FAIL;
        if (terminal) {
            WifiTerminalItem.setLink(stack, GlobalPos.of(level.dimension(), pos));
            player.displayClientMessage(Component.translatable("message.nuclearsq.wifi.linked", pos.getX(), pos.getY(), pos.getZ(), be.range()), true);
        } else if (be.addAntenna()) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.nuclearsq.wifi.antenna", be.antennas(),
                    WifiValues.adapter().maxAntennas(), be.range()), true);
        } else {
            player.displayClientMessage(Component.translatable("message.nuclearsq.wifi.antenna_full", WifiValues.adapter().maxAntennas()), true);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WifiAdapterBlockEntity be) {
            player.displayClientMessage(Component.translatable(be.online() ? "message.nuclearsq.wifi.status" : "message.nuclearsq.wifi.status_off",
                    be.range(), be.antennas(), WifiValues.adapter().maxAntennas(), be.stored(), be.capacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof WifiAdapterBlockEntity be) {
            int n = be.takeAntennas();
            if (n > 0) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(StorageRegistry.WIFI_ANTENNA.get(), n));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
