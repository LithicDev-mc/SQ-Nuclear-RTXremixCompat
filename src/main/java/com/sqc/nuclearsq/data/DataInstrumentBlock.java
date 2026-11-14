package com.sqc.nuclearsq.data;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class DataInstrumentBlock extends DataPanelBlock implements EntityBlock {
    public static final int GAUGE = 0, BAR = 1, DISPLAY = 2;
    public final int kind;

    public DataInstrumentBlock(Properties props, int kind) {
        super(props, true, 1, 1, 0, 15, 15, 3);
        this.kind = kind;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DataInstrumentBlockEntity be) be.cycle(player, player.isShiftKeyDown());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DataInstrumentBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != DataRegistry.INSTRUMENT_BE.get()) return null;
        return (lvl, pos, st, be) -> ((DataInstrumentBlockEntity) be).serverTick();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DataInstrumentBlockEntity be ? be.comparator() : 0;
    }
}
