package com.sqc.nuclearsq.compat.create;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.sqc.nuclearsq.registry.MultiblockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CreateShaftOutputBlock extends DirectionalKineticBlock implements IBE<CreateShaftOutputBlockEntity> {

    public CreateShaftOutputBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        for (Direction d : Direction.values()) {
            BlockState n = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d));
            if (n.is(MultiblockRegistry.SHAFT_COUPLING.get()) && n.getValue(RotatedPillarBlock.AXIS) == d.getAxis()) {
                return defaultBlockState().setValue(FACING, d.getOpposite());
            }
        }
        return super.getStateForPlacement(ctx);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public Class<CreateShaftOutputBlockEntity> getBlockEntityClass() {
        return CreateShaftOutputBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CreateShaftOutputBlockEntity> getBlockEntityType() {
        return CreateShaftRegistry.OUTPUT_BE.get();
    }
}
