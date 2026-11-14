package com.sqc.nuclearsq.data;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.sqc.nuclearsq.radiation.RadiationManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AreaMonitorBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<AreaMonitorBlock> CODEC = simpleCodec(AreaMonitorBlock::new);
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");

    private static final VoxelShape SHAPE_FLOOR = Block.box(3, 0, 3, 13, 10, 13);
    private static final VoxelShape SHAPE_CEILING = Block.box(3, 6, 3, 13, 16, 13);
    private static final VoxelShape SHAPE_NORTH = Block.box(3, 3, 6, 13, 13, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(3, 3, 0, 13, 13, 10);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 3, 3, 10, 13, 13);
    private static final VoxelShape SHAPE_WEST = Block.box(6, 3, 3, 16, 13, 13);

    public AreaMonitorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL)
                .setValue(ALARM, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, ALARM);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> SHAPE_FLOOR;
            case CEILING -> SHAPE_CEILING;
            default -> switch (state.getValue(FACING)) {
                case SOUTH -> SHAPE_SOUTH;
                case EAST -> SHAPE_EAST;
                case WEST -> SHAPE_WEST;
                default -> SHAPE_NORTH;
            };
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AreaMonitorBlockEntity be) {
            if (player.isShiftKeyDown()) {
                be.resetPeak();
                player.displayClientMessage(Component.translatable("message.nuclearsq.area_monitor.reset"), true);
            } else {
                player.displayClientMessage(status(be), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected Component status(AreaMonitorBlockEntity be) {
        return Component.translatable("message.nuclearsq.area_monitor.status",
                RadiationManager.fmtRate(be.rate()), RadiationManager.fmtRate(be.peak()), RadiationManager.fmtRate(be.threshold()));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ALARM) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ALARM) && getConnectedDirection(state) == direction ? 15 : 0;
    }

    void setAlarm(Level level, BlockPos pos, BlockState state, boolean alarm) {
        if (state.getValue(ALARM) == alarm) return;
        BlockState next = state.setValue(ALARM, alarm);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        updateNeighbours(level, pos, next);
    }

    private void updateNeighbours(Level level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), this);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock()) && state.getValue(ALARM)) updateNeighbours(level, pos, state);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AreaMonitorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof AreaMonitorBlockEntity m) m.serverTick();
        };
    }
}
