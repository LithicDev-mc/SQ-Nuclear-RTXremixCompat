package com.sqc.nuclearsq.spent;

import com.sqc.nuclearsq.registry.SpentFuelRegistry;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class LargeCastorBlock extends Block implements EntityBlock {
    public static final int W = 2, H = 3, D = 2, PARTS = W * H * D;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, PARTS - 1);

    private static final VoxelShape[] SHAPES = new VoxelShape[PARTS];

    static {
        for (int p = 0; p < PARTS; p++) {
            int x = dx(p), y = dy(p), z = dz(p);
            SHAPES[p] = Block.box(x == 0 ? 2 : 0, 0, z == 0 ? 2 : 0, x == 0 ? 16 : 14, y == H - 1 ? 15 : 16, z == 0 ? 16 : 14);
        }
    }

    public LargeCastorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    public static int dx(int part) { return part % W; }
    public static int dz(int part) { return part / W % D; }
    public static int dy(int part) { return part / (W * D); }

    public static int part(int x, int y, int z) { return x + W * z + W * D * y; }

    public static BlockPos master(BlockPos pos, BlockState state) {
        int p = state.getValue(PART);
        return pos.offset(-dx(p), -dy(p), -dz(p));
    }

    @Nullable
    public static LargeCastorBlockEntity entity(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof LargeCastorBlock)) return null;
        return level.getBlockEntity(master(pos, state)) instanceof LargeCastorBlockEntity be ? be : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos o = ctx.getClickedPos();
        if (o.getY() + H - 1 > level.getMaxBuildHeight() - 1) return null;
        for (int p = 1; p < PARTS; p++) {
            BlockPos q = o.offset(dx(p), dy(p), dz(p));
            if (!level.getBlockState(q).canBeReplaced(ctx) || !level.getWorldBorder().isWithinBounds(q)) return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        for (int p = 1; p < PARTS; p++) {
            level.setBlock(pos.offset(dx(p), dy(p), dz(p)), defaultBlockState().setValue(PART, p), Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.getAbilities().instabuild && state.getValue(PART) != 0) {
            BlockPos m = master(pos, state);
            if (level.getBlockState(m).is(this)) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this)) {
            BlockPos m = master(pos, state);
            if (state.getValue(PART) == 0) {
                for (int p = 1; p < PARTS; p++) {
                    BlockPos q = m.offset(dx(p), dy(p), dz(p));
                    BlockState s = level.getBlockState(q);
                    if (s.is(this) && s.getValue(PART) == p) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            } else if (level.getBlockState(m).is(this)) {
                level.destroyBlock(m, true);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(PART)];
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!SpentFuel.fitsCastor(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        LargeCastorBlockEntity be = entity(level, pos, state);
        if (be == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return insert(stack, level, be, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        LargeCastorBlockEntity be = entity(level, pos, state);
        if (be == null) return InteractionResult.PASS;
        return use(level, be, player);
    }

    static ItemInteractionResult insert(ItemStack stack, Level level, CastorCaskBlockEntity be, Player player, InteractionHand hand) {
        if (!SpentFuel.fitsCastor(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            ItemStack rest = ItemHandlerHelper.insertItem(be.items(), stack.copy(), false);
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, rest);
            status(player, be);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    static InteractionResult use(Level level, CastorCaskBlockEntity be, Player player) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown() && be.getBlockState().getBlock() instanceof LargeCastorBlock block) {
                pickUp(level, be, block, player);
                return InteractionResult.CONSUME;
            }
            status(player, be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void pickUp(Level level, CastorCaskBlockEntity be, LargeCastorBlock block, Player player) {
        BlockPos m = be.getBlockPos();
        ItemStack stack = new ItemStack(block);
        stack.applyComponents(be.collectComponents());
        level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public static void status(Player player, CastorCaskBlockEntity be) {
        int[] c = be.counts();
        player.displayClientMessage(Component.translatable("message.nuclearsq.castor.contents", c[0], c[1], be.capacity()), true);
    }

    static int signal(CastorCaskBlockEntity be) {
        int[] c = be.counts();
        int n = c[0] + c[1];
        return n == 0 ? 0 : 1 + 14 * n / be.capacity();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        LargeCastorBlockEntity be = entity(level, pos, state);
        return be == null ? 0 : signal(be);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new LargeCastorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SpentFuelRegistry.LARGE_CASTOR_BE.get()) return null;
        return (lvl, pos, st, be) -> ((LargeCastorBlockEntity) be).serverTick();
    }
}
