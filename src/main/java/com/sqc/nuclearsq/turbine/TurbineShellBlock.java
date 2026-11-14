package com.sqc.nuclearsq.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TurbineShellBlock extends Block {
    public static final IntegerProperty SHAPE = IntegerProperty.create("shape", 0, TurbineShapes.COUNT - 1);
    public static final IntegerProperty ROT = IntegerProperty.create("rot", 0, 3);

    private static final VoxelShape[][] CACHE = new VoxelShape[TurbineShapes.COUNT][4];

    public TurbineShellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SHAPE, 0).setValue(ROT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, ROT);
    }

    public static VoxelShape shape(int idx, int rot) {
        VoxelShape s = CACHE[idx][rot];
        if (s != null) return s;
        s = Shapes.empty();
        for (double[] b : TurbineShapes.BOXES[idx]) {
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
        CACHE[idx][rot] = s;
        return s;
    }

    private static VoxelShape shape(BlockState state) {
        return shape(state.getValue(SHAPE), state.getValue(ROT));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return TurbineShapes.LADDER[state.getValue(SHAPE)];
    }
}
