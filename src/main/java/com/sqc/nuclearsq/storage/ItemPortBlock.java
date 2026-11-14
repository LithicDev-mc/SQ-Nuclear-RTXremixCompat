package com.sqc.nuclearsq.storage;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.data.DataDevice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ItemPortBlock extends Block implements EntityBlock, DataDevice {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final double T = 4;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public ItemPortBlock(Properties props) {
        super(props);
        for (Direction d : Direction.values()) shapes.put(d, plate(d));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static VoxelShape plate(Direction d) {
        double t = T / 16;
        return switch (d) {
            case NORTH -> Shapes.box(0, 0, 0, 1, 1, t);
            case SOUTH -> Shapes.box(0, 0, 1 - t, 1, 1, 1);
            case WEST -> Shapes.box(0, 0, 0, t, 1, 1);
            case EAST -> Shapes.box(1 - t, 0, 0, 1, 1, 1);
            case DOWN -> Shapes.box(0, 0, 0, 1, t, 1);
            case UP -> Shapes.box(0, 1 - t, 0, 1, 1, 1);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
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
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPortBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != StorageRegistry.ITEM_PORT_BE.get()) return null;
        return (l, p, s, be) -> ((ItemPortBlockEntity) be).serverTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ItemPortBlockEntity be) {
            boolean added = be.toggleFilter(stack);
            player.displayClientMessage(Component.translatable(added ? "message.nuclearsq.item_port.filter_added" : "message.nuclearsq.item_port.filter_removed",
                    stack.getHoverName()), true);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ItemPortBlockEntity be) {
            if (player.isShiftKeyDown()) {
                be.clearFilters();
                player.displayClientMessage(Component.translatable("message.nuclearsq.item_port.filter_cleared"), true);
            } else {
                be.cycleMode();
                player.displayClientMessage(be.status(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
