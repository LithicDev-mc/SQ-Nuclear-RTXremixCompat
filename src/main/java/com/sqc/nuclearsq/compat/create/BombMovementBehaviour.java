package com.sqc.nuclearsq.compat.create;

import java.util.List;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.explosion.NuclearExplosion;
import com.sqc.nuclearsq.explosion.bomb.NuclearBombBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;

public class BombMovementBehaviour implements MovementBehaviour {
    private static final String LAST_SPEED = "nuclearsq_last_speed";
    private static final String FUSE = "nuclearsq_fuse";

    @Override
    public void tick(MovementContext ctx) {
        if (!(ctx.world instanceof ServerLevel sl) || ctx.position == null || ctx.contraption == null) return;
        AbstractContraptionEntity entity = ctx.contraption.entity;
        StructureBlockInfo info = ctx.contraption.getBlocks().get(ctx.localPos);
        NuclearBombBlock bomb = info == null ? null : NuclearBombBlock.of(info.state());
        if (bomb == null || entity == null || !bomb.isCore(info.state())) return;
        BlockState state = info.state();
        if (!state.getValue(NuclearBombBlock.ARMED)) {
            ctx.data.remove(FUSE);
            return;
        }
        Vec3 motion = ctx.motion == null ? Vec3.ZERO : ctx.motion;

        int total = NuclearConfig.fuseSeconds() * 20;
        int fuse = ctx.data.contains(FUSE) ? ctx.data.getInt(FUSE) : total;
        if (fuse > 0) {
            fuse--;
            ctx.data.putInt(FUSE, fuse);
            boolean last = fuse < 60;
            if (fuse % (last ? 5 : 20) == 0) {
                float pitch = last ? 2.0f : 1.0f + 0.5f * (1f - fuse / (float) Math.max(1, total));
                sl.playSound(null, ctx.position.x, ctx.position.y, ctx.position.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.5f, pitch);
            }
        } else {
            List<BlockPos> parts = bomb.parts(ctx.localPos);
            boolean free = true;
            for (BlockPos local : bomb.bottom(ctx.localPos)) {
                StructureBlockInfo below = ctx.contraption.getBlocks().get(local.below());
                Vec3 under = entity.toGlobalVector(Vec3.atCenterOf(local).subtract(0, 1, 0), 1f);
                free &= (below == null || below.state().isAir()) && FallingBlock.isFree(sl.getBlockState(BlockPos.containing(under)));
            }
            if (free) {
                Vec3 bottom = entity.toGlobalVector(bomb.bottomCenter(ctx.localPos), 1f);
                removeAll(entity, parts);
                NuclearBombBlock.launch(sl, bottom, state, motion);
                return;
            }
        }

        double was = ctx.data.getDouble(LAST_SPEED), is = motion.length();
        ctx.data.putDouble(LAST_SPEED, is);
        if (was >= NuclearConfig.BOMB_IMPACT_SPEED.get() && is < was * 0.4) {
            Vec3 center = entity.toGlobalVector(bomb.bottomCenter(ctx.localPos).add(0, bomb.width(), 0), 1f);
            removeAll(entity, bomb.parts(ctx.localPos));
            NuclearExplosion.detonate(sl, center, bomb.blast());
        }
    }

    private static void removeAll(AbstractContraptionEntity entity, List<BlockPos> parts) {
        for (BlockPos local : parts) entity.setBlock(local, new StructureBlockInfo(local, Blocks.AIR.defaultBlockState(), null));
    }
}
