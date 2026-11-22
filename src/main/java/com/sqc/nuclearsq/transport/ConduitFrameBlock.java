package com.sqc.nuclearsq.transport;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class ConduitFrameBlock extends Block {
    public ConduitFrameBlock(Properties props) {
        super(props);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof ConduitBlock c)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!WrenchItem.mayUse(level, pos, player)) return ItemInteractionResult.FAIL;
        BlockState now = c.defaultBlockState();
        level.setBlock(pos, now, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ConduitBlockEntity be) be.setFrame(true, null);
        c.refresh(level, pos);
        changed(level, pos, now);
        stack.consume(1, player);
        return ItemInteractionResult.SUCCESS;
    }

    static ItemInteractionResult useOnConduit(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof ConduitBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockState camo = be.framed() && be.camo() == null ? camo(stack, level, pos) : null;
        boolean frame = !be.framed() && stack.is(TransportRegistry.CONDUIT_FRAME_ITEM.get());
        if (camo == null && !frame) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!WrenchItem.mayUse(level, pos, player)) return ItemInteractionResult.FAIL;
        be.setFrame(true, camo);
        if (frame) stack.consume(1, player);
        changed(level, pos, camo != null ? camo : TransportRegistry.CONDUIT_FRAME.get().defaultBlockState());
        return ItemInteractionResult.SUCCESS;
    }

    static InteractionResult clearCamo(Level level, BlockPos pos, Player player) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof ConduitBlockEntity be) || be.camo() == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!WrenchItem.mayUse(level, pos, player)) return InteractionResult.FAIL;
        BlockState old = be.camo();
        be.setFrame(true, null);
        changed(level, pos, old);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static BlockState camo(ItemStack stack, Level level, BlockPos pos) {
        if (!(stack.getItem() instanceof BlockItem bi)) return null;
        Block b = bi.getBlock();
        if (b instanceof EntityBlock || b instanceof ConduitBlock || b instanceof ConduitFrameBlock) return null;
        BlockState s = b.defaultBlockState();
        return s.getRenderShape() == RenderShape.MODEL && s.isCollisionShapeFullBlock(level, pos) ? s : null;
    }

    private static void changed(Level level, BlockPos pos, BlockState look) {
        BlockState st = level.getBlockState(pos);
        level.sendBlockUpdated(pos, st, st, Block.UPDATE_ALL);
        level.playSound(null, pos, look.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
    }
}
