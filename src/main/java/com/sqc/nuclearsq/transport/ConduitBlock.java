package com.sqc.nuclearsq.transport;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.multiblock.Waterlog;

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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

public class ConduitBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public final boolean fluid;
    public final TransportTier tier;
    private final double half;
    private final VoxelShape core;
    private final Map<Direction, VoxelShape> arms = new EnumMap<>(Direction.class);
    private final VoxelShape[] shapes = new VoxelShape[SHAPE_KEYS << 12];
    protected static final int SHAPE_KEYS = 8;

    public ConduitBlock(Properties props, boolean fluid, TransportTier tier) {
        this(props, fluid, tier, fluid ? 3 : 2);
    }

    protected ConduitBlock(Properties props, boolean fluid, TransportTier tier, int halfPx) {
        super(props);
        this.fluid = fluid;
        this.tier = tier;
        this.half = halfPx / 16.0;
        double lo = 0.5 - half, hi = 0.5 + half;
        core = Shapes.box(lo, lo, lo, hi, hi, hi);
        arms.put(Direction.DOWN, Shapes.box(lo, 0, lo, hi, lo, hi));
        arms.put(Direction.UP, Shapes.box(lo, hi, lo, hi, 1, hi));
        arms.put(Direction.NORTH, Shapes.box(lo, lo, 0, hi, hi, lo));
        arms.put(Direction.SOUTH, Shapes.box(lo, lo, hi, hi, hi, 1));
        arms.put(Direction.WEST, Shapes.box(0, lo, lo, lo, hi, hi));
        arms.put(Direction.EAST, Shapes.box(hi, lo, lo, 1, hi, hi));
        registerDefaultState(stateDefinition.any().setValue(Waterlog.WATERLOGGED, false));
    }

    public int rate() {
        return fluid ? tier.pipeRate : tier.cableRate;
    }

    public boolean items() {
        return false;
    }

    public boolean data() {
        return false;
    }

    public boolean canPull() {
        return fluid;
    }

    protected boolean connectsTo(ConduitBlock o) {
        return o.fluid == fluid && o.items() == items() && o.data() == data();
    }

    protected boolean hasTarget(Level level, BlockPos n, Direction side) {
        return fluid ? level.getCapability(Capabilities.FluidHandler.BLOCK, n, side) != null
                : level.getCapability(Capabilities.EnergyStorage.BLOCK, n, side) != null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Waterlog.WATERLOGGED);
    }

    public static int connOf(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ConduitBlockEntity be ? be.conn() : 0;
    }

    public static ConduitSide sideAt(BlockGetter level, BlockPos pos, Direction d) {
        return ConduitSide.of(connOf(level, pos), d);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        ConduitBlockEntity be = level.getBlockEntity(pos) instanceof ConduitBlockEntity c ? c : null;
        if (be != null && be.framed()) return Shapes.block();
        int conn = be == null ? 0 : be.conn();
        int key = conn | shapeKey(state) << 12;
        VoxelShape s = shapes[key];
        if (s == null) shapes[key] = s = shape(state, conn);
        return s;
    }

    protected int shapeKey(BlockState state) {
        return 0;
    }

    protected VoxelShape shape(BlockState state, int conn) {
        VoxelShape s = core;
        for (Direction d : Direction.values()) if (ConduitSide.of(conn, d) != ConduitSide.NONE) s = Shapes.or(s, arms.get(d));
        return s;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return Waterlog.place(placementState(ctx), ctx);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlog.fluid(state);
    }

    protected BlockState placementState(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos npos) {
        Waterlog.tick(state, level, pos);
        if (level instanceof Level l && !l.isClientSide) refresh(l, pos);
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        refresh(level, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        refresh(level, pos);
    }

    public void refresh(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof ConduitBlock b) || !(level.getBlockEntity(pos) instanceof ConduitBlockEntity be)) return;
        boolean changed = be.setConn(b.connections(st, level, pos));
        if (be.setHold(b.hold(st, level, pos, be.conn())) | changed) level.sendBlockUpdated(pos, st, st, Block.UPDATE_ALL);
    }

    protected int hold(BlockState state, Level level, BlockPos pos, int conn) {
        return 0;
    }

    protected int connections(BlockState state, LevelAccessor level, BlockPos pos) {
        int conn = 0;
        for (Direction d : Direction.values()) conn = ConduitSide.with(conn, d, side(state, level, pos, d));
        return conn;
    }

    protected ConduitSide side(BlockState state, LevelAccessor level, BlockPos pos, Direction dir) {
        ConduitBlockEntity self = level.getBlockEntity(pos) instanceof ConduitBlockEntity be ? be : null;
        int mode = self == null ? ConduitBlockEntity.AUTO : self.mode(dir);
        if (mode == ConduitBlockEntity.OFF) return ConduitSide.NONE;
        BlockPos n = pos.relative(dir);
        if (level.getBlockState(n).getBlock() instanceof ConduitBlock o) {
            if (!connectsTo(o)) return ConduitSide.NONE;
            boolean otherOff = level.getBlockEntity(n) instanceof ConduitBlockEntity ob && ob.mode(dir.getOpposite()) == ConduitBlockEntity.OFF;
            return otherOff ? ConduitSide.NONE : ConduitSide.ON;
        }
        if (!(level instanceof Level l)) return ConduitSide.NONE;
        if (!hasTarget(l, n, dir.getOpposite())) return ConduitSide.NONE;
        return mode == ConduitBlockEntity.PULL ? ConduitSide.PULL : ConduitSide.PORT;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof WrenchItem)) return ConduitFrameBlock.useOnConduit(stack, level, pos, player);
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!WrenchItem.mayUse(level, pos, player)) return ItemInteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            WrenchItem.dismantle(level, pos, player);
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ConduitBlockEntity be)) return ItemInteractionResult.FAIL;
        Direction d = clickedSide(state, be.conn(), pos, hit);
        int mode = be.cycle(d, canPull());
        refresh(level, pos);
        if (level.getBlockState(pos.relative(d)).getBlock() instanceof ConduitBlock other) other.refresh(level, pos.relative(d));
        String key = mode == ConduitBlockEntity.OFF ? "off" : mode == ConduitBlockEntity.PULL ? "pull" : "on";
        player.displayClientMessage(Component.translatable("message.nuclearsq.conduit." + key,
                Component.translatable("direction.nuclearsq." + d.getName())), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return ConduitFrameBlock.clearCamo(level, pos, player);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative() && level.getBlockEntity(pos) instanceof ConduitBlockEntity be) be.setFrame(false, null);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!state.is(now.getBlock()) && level.getBlockEntity(pos) instanceof ConduitBlockEntity be && be.framed()) {
            Block.popResource(level, pos, new ItemStack(TransportRegistry.CONDUIT_FRAME_ITEM.get()));
        }
        super.onRemove(state, level, pos, now, moved);
    }

    public Direction clickedSide(BlockState state, int conn, BlockPos pos, BlockHitResult hit) {
        Vec3 v = hit.getLocation().subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z), m = Math.max(ax, Math.max(ay, az));
        if (m <= half + 0.01) return hit.getDirection();
        if (m == ax) return v.x > 0 ? Direction.EAST : Direction.WEST;
        if (m == ay) return v.y > 0 ? Direction.UP : Direction.DOWN;
        return v.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConduitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != TransportRegistry.CONDUIT_BE.get()) return null;
        return (lvl, pos, st, be) -> ((ConduitBlockEntity) be).serverTick();
    }
}
