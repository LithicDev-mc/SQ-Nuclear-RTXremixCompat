package com.sqc.nuclearsq.data;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class DataControlBlock extends DataPanelBlock implements EntityBlock {
    public static final int BUTTON = 0, LEVER = 1, SLIDER = 2;
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 15);
    public static final int PRESS_TICKS = 10;
    public static final double TRACK_MIN = 4, TRACK_MAX = 12;

    public final int kind;

    public DataControlBlock(Properties props, int kind) {
        super(props, true, kind == SLIDER ? 2 : kind == LEVER ? 4 : 3, kind == LEVER ? 2 : 3, 0,
                kind == SLIDER ? 14 : kind == LEVER ? 12 : 13, kind == LEVER ? 14 : 13, kind == BUTTON ? 5 : 4);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(ON, false).setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ON, LEVEL);
    }

    public static int levelAt(BlockState state, BlockPos pos, double hitX, double hitZ) {
        double dx = hitX - pos.getX(), dz = hitZ - pos.getZ();
        double lx = switch (state.getValue(FACING)) {
            case NORTH -> 1 - dx;
            case EAST -> 1 - dz;
            case WEST -> dz;
            default -> dx;
        };
        return Mth.clamp((int) Math.round((lx * 16 - TRACK_MIN) / (TRACK_MAX - TRACK_MIN) * 15), 0, 15);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof DataControlBlockEntity be)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            be.cycle(player);
            return InteractionResult.CONSUME;
        }
        switch (kind) {
            case BUTTON -> {
                if (state.getValue(ON)) return InteractionResult.CONSUME;
                setState(level, pos, state.setValue(ON, true));
                level.scheduleTick(pos, this, PRESS_TICKS);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 0.8f);
                be.press(player);
            }
            case LEVER -> {
                boolean on = !state.getValue(ON);
                setState(level, pos, state.setValue(ON, on));
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, on ? 0.6f : 0.5f);
                be.lever(player, on);
            }
            default -> {
                int lvl = levelAt(state, pos, hit.getLocation().x, hit.getLocation().z);
                setState(level, pos, state.setValue(LEVEL, lvl));
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.25f, 0.9f + lvl * 0.04f);
                be.slide(player, lvl);
            }
        }
        return InteractionResult.CONSUME;
    }

    public void setState(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos) == state) return;
        level.setBlock(pos, state, Block.UPDATE_ALL);
        updateNeighbours(level, pos, state);
    }

    private void updateNeighbours(Level level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (kind == BUTTON && state.getValue(ON)) {
            setState(level, pos, state.setValue(ON, false));
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4f, 0.7f);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!moved && !state.is(next.getBlock()) && signal(state) > 0) updateNeighbours(level, pos, state);
        super.onRemove(state, level, pos, next, moved);
    }

    public int signal(BlockState state) {
        return kind == SLIDER ? state.getValue(LEVEL) : state.getValue(ON) ? 15 : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return signal(state);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return dir == state.getValue(FACING) ? signal(state) : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DataControlBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != DataRegistry.CONTROL_BE.get()) return null;
        return (lvl, pos, st, be) -> ((DataControlBlockEntity) be).serverTick();
    }
}
