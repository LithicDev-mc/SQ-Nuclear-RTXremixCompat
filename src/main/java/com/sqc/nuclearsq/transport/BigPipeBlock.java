package com.sqc.nuclearsq.transport;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BigPipeBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final IntegerProperty QUAD = IntegerProperty.create("quad", 0, 3);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static boolean noDrop;
    private static boolean cascading;

    public BigPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(QUAD, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, QUAD, WATERLOGGED);
    }

    public static Direction.Axis[] plane(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
        };
    }

    private static BlockPos step(BlockPos p, Direction.Axis a, int n) {
        return n == 0 ? p : p.relative(Direction.fromAxisAndDirection(a, Direction.AxisDirection.POSITIVE), n);
    }

    public static BlockPos cell(BlockPos origin, Direction.Axis axis, int quad) {
        Direction.Axis[] pl = plane(axis);
        return step(step(origin, pl[0], quad & 1), pl[1], quad >> 1);
    }

    public static BlockPos origin(BlockPos pos, Direction.Axis axis, int quad) {
        Direction.Axis[] pl = plane(axis);
        return step(step(pos, pl[0], -(quad & 1)), pl[1], -(quad >> 1));
    }

    private static BlockPos[] siblings(BlockPos pos, BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        int quad = state.getValue(QUAD);
        BlockPos o = origin(pos, axis, quad);
        BlockPos[] out = new BlockPos[3];
        int k = 0;
        for (int q = 0; q < 4; q++) if (q != quad) out[k++] = cell(o, axis, q);
        return out;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BigPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != TransportRegistry.BIG_PIPE_BE.get()) return null;
        return (l, p, s, be) -> ((BigPipeBlockEntity) be).serverTick();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative()) noDrop = true;
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !cascading) {
            boolean drop = !noDrop;
            noDrop = false;
            cascading = true;
            try {
                for (BlockPos s : siblings(pos, state)) {
                    BlockState o = level.getBlockState(s);
                    if (o.is(this) && o.getValue(AXIS) == state.getValue(AXIS)) {
                        level.setBlock(s, o.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
                    }
                }
            } finally {
                cascading = false;
            }
            if (drop) Block.popResource(level, pos, new ItemStack(this));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
