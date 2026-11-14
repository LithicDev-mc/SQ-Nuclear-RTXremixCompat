package com.sqc.nuclearsq.module;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ModuleBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<ModuleState> STATE = EnumProperty.create("state", ModuleState.class);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static ModuleKind pending;

    public final ModuleKind kind;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public static ModuleBlock create(ModuleKind kind, Properties props) {
        pending = kind;
        return new ModuleBlock(kind, props);
    }

    private ModuleBlock(ModuleKind kind, Properties props) {
        super(props);
        this.kind = kind;
        BlockState def = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATE, ModuleState.IDLE);
        if (kind.tall) def = def.setValue(HALF, DoubleBlockHalf.LOWER);
        registerDefaultState(def);
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotate(kind.shape, d));
    }

    private static VoxelShape rotate(VoxelShape north, Direction to) {
        VoxelShape[] out = {Shapes.empty()};
        int turns = (to.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4;
        north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double ax = x0, az = z0, bx = x1, bz = z1;
            for (int i = 0; i < turns; i++) {
                double nax = 1 - bz, nbx = 1 - az, naz = ax, nbz = bx;
                ax = nax; bx = nbx; az = naz; bz = nbz;
            }
            out[0] = Shapes.or(out[0], Shapes.box(ax, y0, az, bx, y1, bz));
        });
        return out[0];
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> create(kind, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, STATE);
        if (pending != null && pending.tall) b.add(HALF);
    }

    public boolean lower(BlockState st) {
        return !kind.tall || st.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState st = defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        if (!kind.tall) return st;
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) return null;
        return st;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState st, @Nullable net.minecraft.world.entity.LivingEntity by,
                            net.minecraft.world.item.ItemStack stack) {
        if (kind.tall) level.setBlock(pos.above(), st.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState st, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (kind.tall && dir.getAxis() == Direction.Axis.Y) {
            boolean lower = st.getValue(HALF) == DoubleBlockHalf.LOWER;
            if (dir == (lower ? Direction.UP : Direction.DOWN)) {
                boolean partner = other.is(this) && other.getValue(HALF) != st.getValue(HALF);
                if (!partner) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                if (lower) return st;
                return st.setValue(STATE, other.getValue(STATE)).setValue(FACING, other.getValue(FACING));
            }
        }
        return super.updateShape(st, dir, other, level, pos, otherPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState st, Player player) {
        if (kind.tall && !level.isClientSide && st.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState b = level.getBlockState(below);
            if (b.is(this) && b.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (!player.isCreative()) Block.dropResources(b, level, below, level.getBlockEntity(below), player, player.getMainHandItem());
                level.setBlock(below, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, st, player);
    }

    @Override
    protected VoxelShape getShape(BlockState st, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.get(st.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState st, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos base = lower(st) ? pos : pos.below();
        if (!level.isClientSide && level.getBlockEntity(base) instanceof ModuleBlockEntity be) player.displayClientMessage(be.statusLine(), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return lower(st) ? new ModuleBlockEntity(pos, st) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState st, BlockEntityType<T> type) {
        return level.isClientSide || type != ModuleRegistry.MODULE_BE.get() ? null
                : (l, p, s, be) -> ((ModuleBlockEntity) be).serverTick();
    }
}
