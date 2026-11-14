package com.sqc.nuclearsq.compat.create;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import com.sqc.nuclearsq.reactor.FuelRodBlock;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class FuelRodAttachment {
    private FuelRodAttachment() {}

    static void register() {
        BlockMovementChecks.registerAttachedCheck(FuelRodAttachment::attached);
    }

    private static CheckResult attached(BlockState state, Level level, BlockPos pos, Direction dir) {
        boolean rod = state.getBlock() instanceof FuelRodBlock, head = state.getBlock() instanceof FuelRodHeadBlock;
        if (!rod && !head) return CheckResult.PASS;
        if (!formed(state) || dir.getAxis() != Direction.Axis.Y || (head && dir == Direction.UP)) return CheckResult.PASS;
        BlockState other = level.getBlockState(pos.relative(dir));
        if (!formed(other)) return CheckResult.PASS;
        boolean match = dir == Direction.DOWN ? other.getBlock() instanceof FuelRodBlock
                : other.getBlock() instanceof FuelRodBlock || other.getBlock() instanceof FuelRodHeadBlock;
        return match ? CheckResult.SUCCESS : CheckResult.PASS;
    }

    private static boolean formed(BlockState state) {
        return state.hasProperty(ReactorProps.ROD_FORMED) && state.getValue(ReactorProps.ROD_FORMED);
    }
}
