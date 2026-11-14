package com.sqc.nuclearsq.crate;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.sqc.nuclearsq.data.DataDevice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class StorageCrateBlock extends BaseEntityBlock implements DataDevice {
    public static final MapCodec<StorageCrateBlock> CODEC = simpleCodec(StorageCrateBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty SECOND = BooleanProperty.create("second");

    public StorageCrateBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SECOND, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SECOND);
    }

    public static Direction toSecond(BlockState st) {
        return st.getValue(FACING).getCounterClockWise();
    }

    public static BlockPos mainPos(BlockState st, BlockPos pos) {
        return st.getValue(SECOND) ? pos.relative(toSecond(st).getOpposite()) : pos;
    }

    @Nullable
    public static StorageCrateBlockEntity main(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof StorageCrateBlock)) return null;
        return level.getBlockEntity(mainPos(st, pos)) instanceof StorageCrateBlockEntity be ? be : null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState st = defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        BlockPos other = ctx.getClickedPos().relative(toSecond(st));
        return ctx.getLevel().isInWorldBounds(other) && ctx.getLevel().getBlockState(other).canBeReplaced(ctx) ? st : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState st, @Nullable LivingEntity by, ItemStack stack) {
        if (!level.isClientSide) level.setBlock(pos.relative(toSecond(st)), st.setValue(SECOND, true), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState st, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        Direction toOther = st.getValue(SECOND) ? toSecond(st).getOpposite() : toSecond(st);
        if (dir == toOther && !(other.is(this) && other.getValue(SECOND) != st.getValue(SECOND) && other.getValue(FACING) == st.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(st, dir, other, level, pos, otherPos);
    }

    @Override
    protected BlockState rotate(BlockState st, Rotation rot) {
        return st.setValue(FACING, rot.rotate(st.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState st, Mirror mirror) {
        return st.rotate(mirror.getRotation(st.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState st) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState st, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && main(level, pos) instanceof StorageCrateBlockEntity be) {
            player.openMenu(be, buf -> buf.writeVarInt(be.capacity()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState st, Player player) {
        if (!level.isClientSide && main(level, pos) instanceof StorageCrateBlockEntity be) {
            BlockPos mp = be.getBlockPos();
            if (player.isCreative()) {
                if (!be.isEmpty()) {
                    ItemStack drop = new ItemStack(this);
                    drop.applyComponents(be.collectComponents());
                    Containers.dropItemStack(level, mp.getX() + 0.5, mp.getY() + 0.5, mp.getZ() + 0.5, drop);
                    be.clearContent();
                }
                if (st.getValue(SECOND)) level.setBlock(mp, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            } else if (st.getValue(SECOND)) {
                level.destroyBlock(mp, true, player);
            }
        }
        return super.playerWillDestroy(level, pos, st, player);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState st) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState st, Level level, BlockPos pos) {
        return main(level, pos) instanceof StorageCrateBlockEntity be ? AbstractContainerMenu.getRedstoneSignalFromContainer(be) : 0;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState st, BlockEntityType<T> type) {
        return level.isClientSide || type != CrateRegistry.BLOCK_ENTITY.get() ? null : (l, p, s, be) -> ((StorageCrateBlockEntity) be).serverTick();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return st.getValue(SECOND) ? null : new StorageCrateBlockEntity(pos, st);
    }
}
