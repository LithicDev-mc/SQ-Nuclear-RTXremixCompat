package com.sqc.nuclearsq.compat.create;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import com.sqc.nuclearsq.explosion.bomb.MultiPartBombBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class BombAttachment {
    private BombAttachment() {}

    static void register() {
        BlockMovementChecks.registerAttachedCheck(BombAttachment::attached);
    }

    private static CheckResult attached(BlockState state, Level level, BlockPos pos, Direction dir) {
        if (!(state.getBlock() instanceof MultiPartBombBlock bomb)) return CheckResult.PASS;
        BlockPos next = pos.relative(dir);
        BlockState other = level.getBlockState(next);
        if (!other.is(bomb)) return CheckResult.PASS;
        return bomb.core(next, other).equals(bomb.core(pos, state)) ? CheckResult.SUCCESS : CheckResult.PASS;
    }
}
