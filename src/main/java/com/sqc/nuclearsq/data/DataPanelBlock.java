package com.sqc.nuclearsq.data;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class DataPanelBlock extends Block implements DataDevice {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final boolean wall;

    protected DataPanelBlock(Properties props, boolean wall, double x0, double y0, double z0, double x1, double y1, double z1) {
        super(props);
        this.wall = wall;
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotate(d, x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static VoxelShape rotate(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (d) {
            case NORTH -> Shapes.box(1 - x1, y0, 1 - z1, 1 - x0, y1, 1 - z0);
            case EAST -> Shapes.box(z0, y0, 1 - x1, z1, y1, 1 - x0);
            case WEST -> Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1);
            default -> Shapes.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction f = wall && face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, f);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
}
