package com.sqc.nuclearsq.compat.create;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.sqc.nuclearsq.robotics.RodGripperBlock;
import com.sqc.nuclearsq.robotics.RodGripperBlockEntity;
import com.sqc.nuclearsq.robotics.RodHook;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class RodGripperMovementBehaviour implements MovementBehaviour {
    private static final String DEPTH = "nuclearsq_depth", HOLD = "nuclearsq_hold", COOLDOWN = "nuclearsq_cooldown";

    @Override
    public void tick(MovementContext ctx) {
        if (!(ctx.world instanceof ServerLevel sl) || ctx.position == null) return;
        int cooldown = ctx.data.getInt(COOLDOWN);
        RodHook hook = new RodHook();
        hook.depth = ctx.data.getInt(DEPTH);
        hook.holding = ctx.data.getBoolean(HOLD);
        BlockPos pos = BlockPos.containing(ctx.position);
        hook.scan(sl, pos);
        if (cooldown > 0) {
            ctx.data.putInt(COOLDOWN, cooldown - 1);
        } else if (hook.act(sl, pos, RodGripperBlockEntity.signals(sl, pos, ctx.state.getValue(RodGripperBlock.FACING)), true)) {
            ctx.data.putInt(COOLDOWN, RodGripperBlockEntity.STEP_TICKS);
        }
        ctx.data.putInt(DEPTH, hook.depth);
        ctx.data.putBoolean(HOLD, hook.holding);
    }
}
