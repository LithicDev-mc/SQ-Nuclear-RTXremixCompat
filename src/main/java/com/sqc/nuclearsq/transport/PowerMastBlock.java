package com.sqc.nuclearsq.transport;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PowerMastBlock extends Block implements EntityBlock {
    public static final int HEIGHT = 12;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, HEIGHT - 1);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private static final double[][] ATTACH = {{-0.94, 8.9, 0.5}, {1.94, 8.9, 0.5}};
    private static final double[] EARTH = {0.5, 12.3, 0.5};

    private final VoxelShape[] shapes = new VoxelShape[height()];

    public PowerMastBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(part(), 0).setValue(AXIS, Direction.Axis.X));
        double s = scale();
        for (int i = 0; i < shapes.length; i++) {
            double w = s == 1 ? 0.3 : Math.min(0.5, s * (0.62 - 0.42 * Math.min((i + 0.5) / s, 7.0) / 7.0));
            shapes[i] = Shapes.box(0.5 - w, 0, 0.5 - w, 0.5 + w, 1, 0.5 + w);
        }
    }

    public IntegerProperty part() { return PART; }

    public int height() { return HEIGHT; }

    protected double scale() { return 1; }

    public int span() { return GridNode.MAX_SPAN; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(part(), AXIS);
    }

    public List<Vec3> attach(BlockPos base, BlockState st) {
        List<Vec3> out = new ArrayList<>(ATTACH.length);
        boolean z = st.getValue(AXIS) == Direction.Axis.Z;
        double s = scale();
        for (double[] a : ATTACH) {
            double ax = 0.5 + s * (a[0] - 0.5), az = 0.5 + s * (a[2] - 0.5);
            double x = z ? 1 - az : ax, zz = z ? ax : az;
            out.add(new Vec3(base.getX() + x, base.getY() + s * a[1], base.getZ() + zz));
        }
        return out;
    }

    public Vec3 earth(BlockPos base) {
        return new Vec3(base.getX() + EARTH[0], base.getY() + scale() * EARTH[1], base.getZ() + EARTH[2]);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes[state.getValue(part())];
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() + height() - 1 >= level.getMaxBuildHeight()) return null;
        for (int i = 1; i < height(); i++) {
            if (!level.getBlockState(pos.above(i)).canBeReplaced(ctx)) return null;
        }
        Direction.Axis look = ctx.getHorizontalDirection().getAxis();
        return defaultBlockState().setValue(AXIS, look == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        for (int i = 1; i < height(); i++) level.setBlock(pos.above(i), state.setValue(part(), i), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos npos) {
        int part = state.getValue(part());
        if (dir == Direction.UP && part < height() - 1 && !(neighbor.is(this) && neighbor.getValue(part()) == part + 1)) return Blocks.AIR.defaultBlockState();
        if (dir == Direction.DOWN && part > 0 && !(neighbor.is(this) && neighbor.getValue(part()) == part - 1)) return Blocks.AIR.defaultBlockState();
        return state;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        int part = state.getValue(part());
        if (!level.isClientSide && part > 0) {
            BlockPos base = pos.below(part);
            BlockState bs = level.getBlockState(base);
            if (bs.is(this)) {
                if (!player.isCreative()) Block.dropResources(bs, level, base, level.getBlockEntity(base), player, player.getMainHandItem());
                level.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && state.getValue(part()) == 0 && !newState.is(this)
                && level.getBlockEntity(pos) instanceof GridNodeBlockEntity n) {
            int wires = n.unlinkAll();
            if (wires > 0) Block.popResource(level, pos, new ItemStack(GridRegistry.WIRE.get(), wires));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof OverheadWireItem ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && GridNode.at(level, pos) instanceof GridNodeBlockEntity n) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.grid.mast", n.links().size()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        if (rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(part()) == 0 ? new GridNodeBlockEntity(pos, state) : null;
    }
}
