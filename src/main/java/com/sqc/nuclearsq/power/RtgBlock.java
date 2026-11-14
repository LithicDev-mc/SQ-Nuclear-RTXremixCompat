package com.sqc.nuclearsq.power;

import com.sqc.nuclearsq.registry.PowerRegistry;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RtgBlock extends Block implements EntityBlock {
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(2, 3, 2, 14, 5, 14), Block.box(3, 5, 3, 13, 16, 13),
            Block.box(0, 5, 7, 16, 16, 9), Block.box(7, 5, 0, 9, 16, 16));
    private static final VoxelShape UPPER = Shapes.or(Block.box(3, 0, 3, 13, 10, 13), Block.box(2, 10, 2, 14, 12, 14), Block.box(4, 12, 4, 12, 16, 12),
            Block.box(0, 0, 7, 16, 10, 9), Block.box(7, 0, 0, 9, 10, 16));

    public RtgBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LOADED, false).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    public static void place(Level level, BlockPos pos) {
        BlockState lower = PowerRegistry.RTG.get().defaultBlockState();
        level.setBlock(pos.above(), lower.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        level.setBlock(pos, lower, Block.UPDATE_ALL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOADED, HALF);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) return null;
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState st, @Nullable net.minecraft.world.entity.LivingEntity by, ItemStack stack) {
        level.setBlock(pos.above(), st.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState st, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir.getAxis() == Direction.Axis.Y) {
            boolean lower = st.getValue(HALF) == DoubleBlockHalf.LOWER;
            if (dir == (lower ? Direction.UP : Direction.DOWN) && !(other.is(this) && other.getValue(HALF) != st.getValue(HALF))) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(st, dir, other, level, pos, otherPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState st, Player player) {
        if (!level.isClientSide && st.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState b = level.getBlockState(below);
            if (b.is(this) && b.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (!player.isCreative()) Block.dropResources(b, level, below, level.getBlockEntity(below), player, player.getMainHandItem());
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, st, player);
    }

    private static BlockPos base(BlockState st, BlockPos pos) {
        return st.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof RtgCapsuleItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(base(state, pos)) instanceof RtgBlockEntity be) {
            if (be.hasCapsule()) {
                player.displayClientMessage(Component.translatable("message.nuclearsq.rtg.full"), true);
            } else if (be.insert(stack)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.7f);
            }
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(base(state, pos)) instanceof RtgBlockEntity be) {
            if (player.isShiftKeyDown() && be.hasCapsule()) {
                ItemStack out = be.take();
                if (!player.getInventory().add(out)) player.drop(out, false);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.7f);
            } else if (!be.hasCapsule()) {
                player.displayClientMessage(Component.translatable("message.nuclearsq.rtg.empty"), true);
            } else if (be.rate() <= 0 && be.capsule().getDamageValue() >= RtgBlockEntity.CAPSULE_STEPS) {
                player.displayClientMessage(Component.translatable("message.nuclearsq.rtg.spent"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.nuclearsq.rtg.status", be.rate(), be.peak(),
                        be.delivered(), be.remainingPercent()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this) && state.getValue(HALF) == DoubleBlockHalf.LOWER && level.getBlockEntity(pos) instanceof RtgBlockEntity be && be.hasCapsule()) {
            Block.popResource(level, pos, be.capsule());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.nuclearsq.rtg_sqc.tip", RtgBlockEntity.PEAK).withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new RtgBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != PowerRegistry.RTG_BE.get()) return null;
        return (lvl, pos, st, be) -> ((RtgBlockEntity) be).serverTick();
    }
}
