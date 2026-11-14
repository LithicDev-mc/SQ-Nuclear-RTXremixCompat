package com.sqc.nuclearsq.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import com.sqc.nuclearsq.multiblock.Waterlog;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.entity.player.Player;

public class FuelRodBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    public static final float TIER1_OUTPUT = 1.0f, TIER2_OUTPUT = 3.75f, TIER3_OUTPUT = 7.5f, TIER4_OUTPUT = 12.5f;
    private static final float[] OUTPUT = {TIER1_OUTPUT, TIER2_OUTPUT, TIER3_OUTPUT, TIER4_OUTPUT};

    private final int tier;

    public static int tier(BlockState state) {
        return state.getBlock() instanceof FuelRodBlock f ? f.tier : 0;
    }

    public static float output(BlockState state) {
        return state.getBlock() instanceof FuelRodBlock f ? OUTPUT[f.tier - 1] : TIER1_OUTPUT;
    }

    public FuelRodBlock(Properties properties) {
        this(properties, 1);
    }

    public FuelRodBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(Waterlog.WATERLOGGED, false)
                .setValue(CHARGE, 4)
                .setValue(ReactorProps.ROD_FORMED, false)
                .setValue(ReactorProps.DRIVEN, false)
                .setValue(ReactorProps.GLOWING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE, ReactorProps.ROD_FORMED, ReactorProps.DRIVEN, ReactorProps.GLOWING, Waterlog.WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ReactorProps.DRIVEN) ? Shapes.empty() : Shapes.block();
    }

    private static final VoxelShape ROD_BOX = Block.box(2, 0, 2, 14, 16, 14);

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ReactorProps.DRIVEN) ? Shapes.empty() : ROD_BOX;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        boolean hidden = state.getValue(ReactorProps.ROD_FORMED) || state.getValue(ReactorProps.DRIVEN);
        return hidden ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelRodBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ReactorRegistry.FUEL_ROD_BE.get() ? null
                : (l, p, s, be) -> ((FuelRodBlockEntity) be).serverTick();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this) && state.getValue(ReactorProps.ROD_FORMED)) FuelRodHeadBlock.rodBlockRemoved(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof FuelRodBlockEntity f
                && f.isCooled()) {
            return java.util.List.of(new net.minecraft.world.item.ItemStack(com.sqc.nuclearsq.registry.SpentFuelRegistry.SPENT_FUEL.get()));
        }
        return super.getDrops(state, params);
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
