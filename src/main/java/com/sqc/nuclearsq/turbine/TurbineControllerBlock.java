package com.sqc.nuclearsq.turbine;

import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.MultiblockRegistry;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

public class TurbineControllerBlock extends Block implements EntityBlock {
    public TurbineControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ReactorProps.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ReactorProps.FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(ReactorProps.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.getValue(ReactorProps.FORMED) ? net.minecraft.world.phys.shapes.Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return state.getValue(ReactorProps.FORMED);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        com.sqc.nuclearsq.kit.KitMarks.removed(state, level, pos, newState);
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TurbineControllerBlockEntity be) {
            be.unhideParts();
            be.dropPart();
            be.unlinkPorts();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbineControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != MultiblockRegistry.TURBINE_BE.get()) return null;
        return (l, p, s, be) -> ((TurbineControllerBlockEntity) be).serverTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (com.sqc.nuclearsq.reactor.PartStages.translatorStage(stack) > 0) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurbineControllerBlockEntity be) be.installPart(player, hand);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp
                && level.getBlockEntity(pos) instanceof TurbineControllerBlockEntity be) {
            if (!(player.isShiftKeyDown() && be.removePart(player))) sp.openMenu(be, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return com.sqc.nuclearsq.kit.KitMarks.drops(params, () -> super.getDrops(state, params));
    }

    @Override
    public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState state) {
        return net.minecraft.world.level.material.PushReaction.BLOCK;
    }
}
