package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.BigMachineRegistry;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import com.sqc.nuclearsq.multiblock.Waterlog;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.material.FluidState;

public class BigMachineControllerBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    private final BigMachine machine;

    public BigMachineControllerBlock(Properties properties, BigMachine machine) {
        super(properties);
        this.machine = machine;
        registerDefaultState(stateDefinition.any().setValue(Waterlog.WATERLOGGED, false).setValue(ReactorProps.FORMED, false));
    }

    public BigMachine machine() {
        return machine;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ReactorProps.FORMED, Waterlog.WATERLOGGED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        if (!state.getValue(ReactorProps.FORMED)) return RenderShape.MODEL;
        return machine.chunkBaked() && com.sqc.nuclearsq.NuclearClientConfig.chunkBaking() ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(ReactorProps.FORMED) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return lightTight(state);
    }

    private boolean lightTight(BlockState state) {
        return machine == BigMachine.CONTAINMENT && state.getValue(ReactorProps.FORMED);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return lightTight(state) ? 15 : super.getLightBlock(state, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        com.sqc.nuclearsq.kit.KitMarks.removed(state, level, pos, newState);
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BigMachineBlockEntity be) {
            be.onBroken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BigMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BigMachineRegistry.CONTROLLER_BE.get()) return null;
        if (level.isClientSide) {
            return machine == BigMachine.CONTROL_ROD_DRIVE || machine == BigMachine.SPENT_FUEL_RACK || machine == BigMachine.PARTICLE_ASSEMBLER
                    ? (l, p, s, be) -> ((BigMachineBlockEntity) be).clientTick() : null;
        }
        return (l, p, s, be) -> ((BigMachineBlockEntity) be).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BigMachineBlockEntity be) {
            if (machine == BigMachine.CONTAINMENT && player.isShiftKeyDown()) be.toggleDoor(player);
            else if (!(player.isShiftKeyDown() && be.removePart(player))) be.use(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (machine == BigMachine.CONTAINMENT && !level.isClientSide && level.getBlockEntity(pos) instanceof BigMachineBlockEntity be) {
            be.doorRedstone();
        }
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (machine == BigMachine.WASTE_REPOSITORY && com.sqc.nuclearsq.spent.SpentFuel.isWaste(stack)
                || machine == BigMachine.SPENT_FUEL_RACK && com.sqc.nuclearsq.spent.SpentFuel.isSpentRod(stack)
                || machine == BigMachine.FLUID_BUFFER && net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(stack).isPresent()) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof BigMachineBlockEntity be) be.acceptHeld(player, hand);
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (machine == BigMachine.CONTROL_ROD_DRIVE && (com.sqc.nuclearsq.reactor.PartStages.moderatorStage(stack) > 0
                || com.sqc.nuclearsq.reactor.PartStages.coolerStage(stack) > 0
                || stack.is(com.sqc.nuclearsq.registry.CatalogRegistry.ITEM_MAP.get("boric_acid_sqc").get()))) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof BigMachineBlockEntity be) be.installPart(player, hand);
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (machine == BigMachine.EMERGENCY_COOLING && stack.is(com.sqc.nuclearsq.registry.CatalogRegistry.ITEM_MAP.get("boric_acid_sqc").get())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof BigMachineBlockEntity be) be.stockBoron(player, hand);
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof com.sqc.nuclearsq.transport.OverheadWireItem) {
            return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
}
