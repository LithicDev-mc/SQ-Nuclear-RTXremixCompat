package com.sqc.nuclearsq.robotics.decon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class DeconRobotItem extends Item {
    public DeconRobotItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        DeconRobotEntity robot = DeconRegistry.ROBOT.get().create(sl);
        if (robot == null) return InteractionResult.FAIL;
        robot.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, ctx.getHorizontalDirection().toYRot(), 0);
        robot.finalizeSpawn(sl, sl.getCurrentDifficultyAt(at), MobSpawnType.SPAWN_EGG, null);
        if (!sl.noCollision(robot)) return InteractionResult.FAIL;
        robot.setEnergy(DeconRobotEntity.BATTERY);
        robot.setAgent(DeconRobotEntity.AGENT_MAX);
        sl.addFreshEntity(robot);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
