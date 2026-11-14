package com.sqc.nuclearsq.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ShaftCouplingBlock extends RotatedPillarBlock {
    private static final VoxelShape X = Shapes.or(Block.box(0, 5, 5, 16, 11, 11), Block.box(4, 1, 1, 12, 15, 15));
    private static final VoxelShape Y = Shapes.or(Block.box(5, 0, 5, 11, 16, 11), Block.box(1, 4, 1, 15, 12, 15));
    private static final VoxelShape Z = Shapes.or(Block.box(5, 5, 0, 11, 11, 16), Block.box(1, 1, 4, 15, 15, 12));

    public ShaftCouplingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction d = ctx.getHorizontalDirection();
        return defaultBlockState().setValue(AXIS, d.getAxis());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(AXIS)) {
            case X -> X;
            case Y -> Y;
            case Z -> Z;
        };
    }
}
