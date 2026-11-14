package com.sqc.nuclearsq.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import com.sqc.nuclearsq.multiblock.Waterlog;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

public class FuelRodHeadBlock extends Block implements SimpleWaterloggedBlock {
    public static final int ROD_BLOCKS = 4;
    public static final IntegerProperty HULL = IntegerProperty.create("hull", 1, 4);

    private final int tier;

    public FuelRodHeadBlock(Properties properties) {
        this(properties, 1);
    }

    public FuelRodHeadBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(Waterlog.WATERLOGGED, false)
                .setValue(HULL, 1)
                .setValue(ReactorProps.ROD_FORMED, false)
                .setValue(ReactorProps.DRIVEN, false)
                .setValue(ReactorProps.GLOWING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ReactorProps.ROD_FORMED, ReactorProps.DRIVEN, ReactorProps.GLOWING, HULL, Waterlog.WATERLOGGED);
    }

    public int tier() {
        return tier;
    }

    public int maxHull() {
        return Math.max(2, tier);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ReactorProps.DRIVEN) ? Shapes.empty() : Shapes.block();
    }

    private static final VoxelShape HEAD_BOX = Block.box(1, 0, 1, 15, 16, 15);

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ReactorProps.DRIVEN) ? Shapes.empty() : HEAD_BOX;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(ReactorProps.DRIVEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (state.getValue(ReactorProps.ROD_FORMED)) {
            setRodFormed(level, pos, false);
            player.displayClientMessage(Component.translatable("message.nuclearsq.rod_disassembled"), true);
            return InteractionResult.CONSUME;
        }

        int missing = countMissing(level, pos);
        if (missing > 0) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.rod_incomplete", missing), true);
            return InteractionResult.CONSUME;
        }
        int needed = strongestHull(level, pos);
        if (needed > maxHull()) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.rod_docker_too_weak", needed), true);
            return InteractionResult.CONSUME;
        }
        setRodFormed(level, pos, true);
        player.displayClientMessage(Component.translatable("message.nuclearsq.rod_assembled"), true);
        return InteractionResult.CONSUME;
    }

    private static int countMissing(Level level, BlockPos head) {
        int missing = 0;
        for (int i = 1; i <= ROD_BLOCKS; i++) {
            if (!(level.getBlockState(head.below(i)).getBlock() instanceof FuelRodBlock)) missing++;
        }
        return missing;
    }

    private static int strongestHull(Level level, BlockPos head) {
        int max = 0;
        for (int i = 1; i <= ROD_BLOCKS; i++) max = Math.max(max, FuelRodBlock.tier(level.getBlockState(head.below(i))));
        return max;
    }

    private static int weakestHull(Level level, BlockPos head) {
        int min = 4;
        for (int i = 1; i <= ROD_BLOCKS; i++) {
            int t = FuelRodBlock.tier(level.getBlockState(head.below(i)));
            if (t > 0) min = Math.min(min, t);
        }
        return min;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this) && state.getValue(ReactorProps.ROD_FORMED)) setRodFormed(level, pos, false);
        super.onRemove(state, level, pos, newState, moved);
    }

    static void rodBlockRemoved(Level level, BlockPos pos) {
        for (int i = 1; i <= ROD_BLOCKS; i++) {
            BlockState st = level.getBlockState(pos.above(i));
            if (st.getBlock() instanceof FuelRodHeadBlock) {
                if (st.getValue(ReactorProps.ROD_FORMED)) setRodFormed(level, pos.above(i), false);
                return;
            }
            if (!(st.getBlock() instanceof FuelRodBlock)) return;
        }
    }

    private static void setRodFormed(Level level, BlockPos head, boolean value) {
        int hull = value ? weakestHull(level, head) : 1;
        for (int i = 0; i <= ROD_BLOCKS; i++) {
            BlockPos p = head.below(i);
            BlockState st = level.getBlockState(p);
            if (!st.hasProperty(ReactorProps.ROD_FORMED)) continue;
            BlockState next = st.setValue(ReactorProps.ROD_FORMED, value);
            if (next.hasProperty(ReactorProps.GLOWING)) {
                next = next.setValue(ReactorProps.GLOWING, value);
            }
            if (next.hasProperty(HULL)) next = next.setValue(HULL, hull);
            if (!value && next.hasProperty(ReactorProps.DRIVEN)) {
                next = next.setValue(ReactorProps.DRIVEN, false);
            }
            if (next != st) level.setBlock(p, next, 3);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return Waterlog.place(super.getStateForPlacement(ctx), ctx);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return Waterlog.canFlowIn(state, fluid);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return Waterlog.flowIn(level, pos, state, fluid);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlog.fluid(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        Waterlog.tick(state, level, pos);
        Waterlog.recheck(state, level, pos);
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        Waterlog.recheck(state, level, pos);
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        Waterlog.drainIfDry(state, level, pos);
    }
}
