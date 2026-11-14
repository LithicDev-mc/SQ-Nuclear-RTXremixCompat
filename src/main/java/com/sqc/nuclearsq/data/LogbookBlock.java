package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class LogbookBlock extends DataPanelBlock implements EntityBlock {
    public static final int MAX_W = 4, MAX_H = 4;

    public LogbookBlock(Properties props) {
        super(props, true, 0, 0, 0, 16, 16, 3);
    }

    public record Layout(BlockPos origin, int w, int h, Direction right) {
        public List<BlockPos> cells() {
            List<BlockPos> out = new ArrayList<>();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.add(origin.relative(right, x).above(y));
            return out;
        }
    }

    private static boolean same(BlockGetter level, BlockPos p, Direction facing) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof LogbookBlock && s.getValue(FACING) == facing;
    }

    public static Layout layout(BlockGetter level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        Direction facing = st.getBlock() instanceof LogbookBlock ? st.getValue(FACING) : Direction.NORTH;
        Direction right = facing.getCounterClockWise(), left = right.getOpposite();
        Layout single = new Layout(pos, 1, 1, right);
        if (!(st.getBlock() instanceof LogbookBlock)) return single;
        BlockPos o = pos;
        for (int i = 0; i < MAX_W && same(level, o.relative(left), facing); i++) o = o.relative(left);
        for (int i = 0; i < MAX_H && same(level, o.below(), facing); i++) o = o.below();
        if (same(level, o.relative(left), facing) || same(level, o.below(), facing)) return single;
        int w = 1, h = 1;
        while (w <= MAX_W && same(level, o.relative(right, w), facing)) w++;
        while (h <= MAX_H && same(level, o.above(h), facing)) h++;
        if (w > MAX_W || h > MAX_H) return single;
        for (int y = -1; y <= h; y++) {
            for (int x = -1; x <= w; x++) {
                boolean inside = x >= 0 && y >= 0 && x < w && y < h;
                if (same(level, o.relative(right, x).above(y), facing) != inside) return single;
            }
        }
        return new Layout(o, w, h, right);
    }

    @Nullable
    public static LogbookBlockEntity book(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(layout(level, pos).origin()) instanceof LogbookBlockEntity be ? be : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                if (book(level, pos) instanceof LogbookBlockEntity be) {
                    int size = be.nextTextSize();
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("logbook.nuclearsq.text_size", size + 1,
                            LogbookBlockEntity.TEXT_SIZES), true);
                }
            } else {
                LogbookMenu.open(sp, layout(level, pos).origin());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogbookBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != DataRegistry.LOGBOOK_BE.get()) return null;
        return (lvl, pos, st, be) -> ((LogbookBlockEntity) be).serverTick();
    }
}
