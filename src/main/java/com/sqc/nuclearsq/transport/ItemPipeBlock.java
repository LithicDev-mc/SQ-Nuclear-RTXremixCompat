package com.sqc.nuclearsq.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

public class ItemPipeBlock extends ConduitBlock {
    public ItemPipeBlock(Properties props, TransportTier tier) {
        super(props, false, tier, 4);
    }

    @Override
    public int rate() {
        return tier.itemRate;
    }

    @Override
    public boolean items() {
        return true;
    }

    @Override
    public boolean canPull() {
        return true;
    }

    @Override
    protected boolean hasTarget(Level level, BlockPos n, Direction side) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, n, side) != null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ConduitBlockEntity be && !be.item().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.item().copy());
            be.clearItem();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
