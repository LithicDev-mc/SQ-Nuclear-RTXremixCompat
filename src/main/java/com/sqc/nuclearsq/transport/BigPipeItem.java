package com.sqc.nuclearsq.transport;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

public class BigPipeItem extends BlockItem {
    public BigPipeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        Direction face = ctx.getClickedFace();
        Direction.Axis axis = face.getAxis();
        BlockPos t = ctx.getClickedPos();
        BlockPos docked = dock(level, t.relative(face.getOpposite()), axis);
        BlockPos origin = docked == null ? null : docked.relative(face);
        if (origin == null) origin = byHit(ctx.getClickLocation(), t, axis);
        BigPipeBlock block = (BigPipeBlock) getBlock();
        BlockPos[] cells = new BlockPos[4];
        for (int q = 0; q < 4; q++) {
            cells[q] = BigPipeBlock.cell(origin, axis, q);
            BlockState st = level.getBlockState(cells[q]);
            if (!st.canBeReplaced() || level.isOutsideBuildHeight(cells[q]) || player != null && !level.mayInteract(player, cells[q])) {
                return InteractionResult.FAIL;
            }
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        for (int q = 0; q < 4; q++) {
            boolean wet = level.getFluidState(cells[q]).getType() == Fluids.WATER;
            level.setBlock(cells[q], block.defaultBlockState().setValue(BigPipeBlock.AXIS, axis).setValue(BigPipeBlock.QUAD, q)
                    .setValue(BigPipeBlock.WATERLOGGED, wet), Block.UPDATE_ALL);
        }
        level.playSound(null, t, block.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
        if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Nullable
    private static BlockPos dock(Level level, BlockPos behind, Direction.Axis axis) {
        BlockState bs = level.getBlockState(behind);
        if (bs.getBlock() instanceof BigPipeBlock && bs.getValue(BigPipeBlock.AXIS) == axis) {
            return BigPipeBlock.origin(behind, axis, bs.getValue(BigPipeBlock.QUAD));
        }
        if (!(level.getBlockEntity(behind) instanceof BigPortBlockEntity first) || first.kind() != BigPortKind.BIG_WATER) return null;
        for (int q = 0; q < 4; q++) {
            BlockPos o = BigPipeBlock.origin(behind, axis, q);
            boolean ok = true;
            for (int k = 0; k < 4 && ok; k++) {
                ok = level.getBlockEntity(BigPipeBlock.cell(o, axis, k)) instanceof BigPortBlockEntity p
                        && p.kind() == BigPortKind.BIG_WATER && java.util.Objects.equals(p.controllerPos(), first.controllerPos());
            }
            if (ok) return o;
        }
        return null;
    }

    private static BlockPos byHit(Vec3 hit, BlockPos t, Direction.Axis axis) {
        Direction.Axis[] pl = BigPipeBlock.plane(axis);
        BlockPos o = t;
        for (Direction.Axis a : pl) {
            double frac = hit.get(a) - t.get(a);
            if (frac < 0.5) o = o.relative(Direction.fromAxisAndDirection(a, Direction.AxisDirection.NEGATIVE));
        }
        return o;
    }
}
