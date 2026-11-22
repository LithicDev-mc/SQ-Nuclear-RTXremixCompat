package com.sqc.nuclearsq.reactor;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import com.sqc.nuclearsq.multiblock.Waterlog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.material.FluidState;

public class ReactorCasingBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty SKY = BooleanProperty.create("sky");

    public ReactorCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Waterlog.WATERLOGGED, false).setValue(ReactorProps.FORMED, false).setValue(SKY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ReactorProps.FORMED, SKY, Waterlog.WATERLOGGED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(ReactorProps.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected int getLightBlock(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.getValue(ReactorProps.FORMED) && !state.getValue(SKY) ? 15 : super.getLightBlock(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.getValue(SKY) || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return Waterlog.place(super.getStateForPlacement(ctx), ctx);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlog.fluid(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        Waterlog.tick(state, level, pos);
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return com.sqc.nuclearsq.kit.KitMarks.drops(params, () -> super.getDrops(state, params));
    }

    @Override
    public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState state) {
        return net.minecraft.world.level.material.PushReaction.BLOCK;
    }

    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        com.sqc.nuclearsq.kit.KitMarks.removed(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
