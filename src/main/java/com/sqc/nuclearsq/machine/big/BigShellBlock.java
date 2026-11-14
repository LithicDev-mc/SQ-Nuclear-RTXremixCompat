package com.sqc.nuclearsq.machine.big;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.sqc.nuclearsq.multiblock.Waterlog;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;

public class BigShellBlock extends Block implements SimpleWaterloggedBlock {
    public static final IntegerProperty ROT = IntegerProperty.create("rot", 0, 3);

    private static final ThreadLocal<BigMachine> BUILDING = new ThreadLocal<>();

    private final BigMachine machine;
    private final IntegerProperty shape;
    private final VoxelShape[][] cache;

    public static BigShellBlock create(Properties properties, BigMachine machine) {
        BUILDING.set(machine);
        try {
            return new BigShellBlock(properties, machine);
        } finally {
            BUILDING.remove();
        }
    }

    private BigShellBlock(Properties properties, BigMachine machine) {
        super(properties);
        this.machine = machine;
        this.shape = (IntegerProperty) stateDefinition.getProperty("shape");
        this.cache = new VoxelShape[machine.boxes.length][4];
        registerDefaultState(stateDefinition.any().setValue(Waterlog.WATERLOGGED, false).setValue(shape, 0).setValue(ROT, 0));
    }

    private static IntegerProperty shapeProperty(BigMachine m) {
        return IntegerProperty.create("shape", 0, Math.max(1, m.boxes.length - 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        BigMachine m = BUILDING.get();
        if (m == null) throw new IllegalStateException("BigShellBlock nur ueber BigShellBlock.create bauen");
        builder.add(shapeProperty(m), ROT, Waterlog.WATERLOGGED);
    }

    public BigMachine machine() {
        return machine;
    }

    public BlockState state(int idx, int rot) {
        return defaultBlockState().setValue(shape, idx).setValue(ROT, rot);
    }

    @Override
    public String getDescriptionId() {
        return "block.nuclearsq.machine_shell";
    }

    @Override
    public MutableComponent getName() {
        return Component.translatable("block.nuclearsq.machine_shell");
    }

    public VoxelShape shape(int idx, int rot) {
        if (idx < 0 || idx >= cache.length) return Shapes.empty();
        VoxelShape s = cache[idx][rot];
        if (s != null) return s;
        s = Shapes.empty();
        for (double[] b : machine.boxes[idx]) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            for (int r = 0; r < rot; r++) {
                double nx0 = 16 - z1, nx1 = 16 - z0;
                z0 = x0;
                z1 = x1;
                x0 = nx0;
                x1 = nx1;
            }
            s = Shapes.or(s, Block.box(x0, b[1], z0, x1, b[4], z1));
        }
        cache[idx][rot] = s;
        return s;
    }

    private VoxelShape shape(BlockState state) {
        return shape(state.getValue(shape), state.getValue(ROT));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (machine == BigMachine.CONTAINMENT && ContainmentDoor.isOpenShape(state.getValue(shape))) return Shapes.empty();
        return shape(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (machine != BigMachine.CONTAINMENT) return InteractionResult.PASS;
        BigMachineBlockEntity hull = ContainmentDoor.controllerOf(level, pos);
        if (hull == null) return InteractionResult.PASS;
        if (!level.isClientSide) hull.toggleDoor(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private boolean doorCell(BlockState state, BlockGetter level, BlockPos pos) {
        int i = state.getValue(shape);
        return machine == BigMachine.CONTAINMENT && (ContainmentDoor.open(i) != i || ContainmentDoor.isOpenShape(i))
                && level instanceof Level l && ContainmentDoor.controllerOf(l, pos) != null;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return doorCell(state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide || neighborBlock instanceof BigShellBlock || !doorCell(state, level, pos)) return;
        BigMachineBlockEntity hull = ContainmentDoor.controllerOf(level, pos);
        if (hull != null) hull.doorRedstone();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    private boolean lightTight(BlockState state) {
        return machine == BigMachine.CONTAINMENT && !ContainmentDoor.isOpenShape(state.getValue(shape));
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return lightTight(state) ? 15 : super.getLightBlock(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !lightTight(state);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        int i = state.getValue(shape);
        return i < machine.ladder.length && machine.ladder[i];
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
}
