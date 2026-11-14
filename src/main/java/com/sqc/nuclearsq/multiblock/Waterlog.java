package com.sqc.nuclearsq.multiblock;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class Waterlog {
    private Waterlog() {}

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static BlockState place(BlockState state, BlockPlaceContext ctx) {
        return state == null ? null : state.setValue(WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER);
    }

    public static BlockState at(BlockState state, LevelAccessor level, BlockPos pos) {
        return state.hasProperty(WATERLOGGED)
                ? state.setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER)
                : state;
    }

    public static FluidState fluid(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    public static void tick(BlockState state, LevelAccessor level, BlockPos pos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
    }

    public static boolean canFlowIn(BlockState state, Fluid fluid) {
        return !state.getValue(WATERLOGGED) && (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER);
    }

    public static boolean flowIn(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (!canFlowIn(state, fluid.getType()) || !fluid.isSource() && !fed(level, pos)) return false;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(WATERLOGGED, true), 3);
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            recheck(state.setValue(WATERLOGGED, true), level, pos);
        }
        return true;
    }

    private static boolean flowsIn(BlockState st) {
        return st.getBlock() instanceof com.sqc.nuclearsq.reactor.FuelRodBlock || st.getBlock() instanceof com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
    }

    private static final int SEARCH_MAX = 512;
    private static final Direction[] FEED_DIRS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};

    static boolean fed(BlockGetter level, BlockPos pos) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        seen.add(pos);
        todo.add(pos);
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            for (Direction d : FEED_DIRS) {
                BlockPos q = p.relative(d);
                BlockState st = level.getBlockState(q);
                if (flowsIn(st)) {
                    if (!st.getValue(WATERLOGGED) || !seen.add(q)) continue;
                    if (seen.size() > SEARCH_MAX) return true;
                    todo.add(q);
                } else {
                    FluidState f = st.getFluidState();
                    if (f.is(FluidTags.WATER) && (f.isSource() || d == Direction.UP)) return true;
                }
            }
        }
        return false;
    }

    public static void recheck(BlockState state, LevelAccessor level, BlockPos pos) {
        if (state.getValue(WATERLOGGED) && !level.isClientSide()) level.scheduleTick(pos, state.getBlock(), 1);
    }

    public static void drainIfDry(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(WATERLOGGED) && !fed(level, pos)) level.setBlock(pos, state.setValue(WATERLOGGED, false), 3);
    }

    public static BlockState leftover(BlockState state) {
        return state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED)
                ? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }
}
