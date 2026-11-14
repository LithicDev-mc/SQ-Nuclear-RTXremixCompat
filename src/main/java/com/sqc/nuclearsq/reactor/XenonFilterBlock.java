package com.sqc.nuclearsq.reactor;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.registry.CatalogRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class XenonFilterBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public XenonFilterBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        XenonFilterBlockEntity.register(level, pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(CatalogRegistry.ITEM_MAP.get("carbon_filter_sqc").get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof XenonFilterBlockEntity be)) return ItemInteractionResult.FAIL;
        if (be.insert()) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.nuclearsq.filter_inserted"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.nuclearsq.xenon_filter.full", XenonFilterBlockEntity.SLOTS), true);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof XenonFilterBlockEntity be) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.xenon_filter.status", be.filters(), XenonFilterBlockEntity.SLOTS,
                    be.leftPercent(), be.energy().getEnergyStored(), be.energy().getMaxEnergyStored()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof XenonFilterBlockEntity be && be.filters() > 1) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(CatalogRegistry.ITEM_MAP.get("carbon_filter_sqc").get(), be.filters() - 1));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new XenonFilterBlockEntity(pos, state);
    }
}
