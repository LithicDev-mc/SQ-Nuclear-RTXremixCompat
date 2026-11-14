package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.Vec3;

public class ControlWallBlock extends DataPanelBlock implements EntityBlock {
    public static final int MIN_W = 3, MIN_H = 2, MAX_W = 5, MAX_H = 3;
    public static final float[] TILE = {2f, 1.5f, 1f, 0.75f, 0.5f};
    public static final int PAGE_TICKS = 100;

    public ControlWallBlock(Properties props) {
        super(props, false, 0, 0, 0, 16, 16, 16);
    }

    public record Layout(BlockPos origin, int w, int h, Direction right, int col, int row) {
        public List<BlockPos> cells() {
            List<BlockPos> out = new ArrayList<>();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.add(origin.relative(right, x).above(y));
            return out;
        }
    }

    public record Grid(int cols, int rows) {
        public int tiles() {
            return cols * rows;
        }
    }

    private static Grid fixed(int w, int h, float tile) {
        return new Grid(Math.max(1, (int) Math.floor(w / tile + 1e-4)), Math.max(1, (int) Math.floor(h / tile + 1e-4)));
    }

    public static Grid grid(int w, int h, int count) {
        for (int k = 0; k < TILE.length; k++) {
            Grid g = fixed(w, h, TILE[k]);
            if (g.tiles() >= count) return g;
        }
        return fixed(w, h, TILE[TILE.length - 1]);
    }

    public static int tileIndex(Grid g, int col, int row, int count, long gameTime) {
        int pages = Math.max(1, (count + g.tiles() - 1) / g.tiles());
        int i = (int) ((gameTime / PAGE_TICKS) % pages) * g.tiles() + row * g.cols() + col;
        return i < count ? i : -1;
    }

    private static boolean same(BlockGetter level, BlockPos p, Direction facing) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof ControlWallBlock && s.getValue(FACING) == facing;
    }

    public static boolean originCandidate(BlockGetter level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof ControlWallBlock)) return false;
        Direction facing = st.getValue(FACING);
        Direction left = facing.getClockWise();
        return !same(level, pos.relative(left), facing) && !same(level, pos.below(), facing);
    }

    @Nullable
    public static Layout layout(BlockGetter level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof ControlWallBlock)) return null;
        Direction facing = st.getValue(FACING);
        Direction right = facing.getCounterClockWise(), left = right.getOpposite();
        BlockPos o = pos;
        for (int i = 0; i < MAX_W && same(level, o.relative(left), facing); i++) o = o.relative(left);
        for (int i = 0; i < MAX_H && same(level, o.below(), facing); i++) o = o.below();
        if (same(level, o.relative(left), facing) || same(level, o.below(), facing)) return null;
        int w = 1, h = 1;
        while (w <= MAX_W && same(level, o.relative(right, w), facing)) w++;
        while (h <= MAX_H && same(level, o.above(h), facing)) h++;
        if (w > MAX_W || h > MAX_H || w < MIN_W || h < MIN_H) return null;
        for (int y = -1; y <= h; y++) {
            for (int x = -1; x <= w; x++) {
                boolean inside = x >= 0 && y >= 0 && x < w && y < h;
                if (same(level, o.relative(right, x).above(y), facing) != inside) return null;
            }
        }
        int col = -1, row = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (o.relative(right, x).above(y).equals(pos)) {
                    col = x;
                    row = h - 1 - y;
                }
            }
        }
        return col < 0 ? null : new Layout(o, w, h, right, col, row);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(level.isClientSide);
        Layout l = layout(level, pos);
        if (l == null) {
            sp.displayClientMessage(Component.translatable("data.nuclearsq.msg.wall", MIN_W, MIN_H, MAX_W, MAX_H), true);
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(l.origin()) instanceof ControlWallBlockEntity master)) return InteractionResult.SUCCESS;
        Grid g = grid(l.w(), l.h(), master.tiles().size());
        Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) return InteractionResult.SUCCESS;
        Vec3 p = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double fx = l.right().getStepX() * (p.x - 0.5) + l.right().getStepZ() * (p.z - 0.5) + 0.5;
        double u = l.col() + fx, v = l.row() + (1 - p.y);
        int col = Math.min(g.cols() - 1, (int) (u / l.w() * g.cols())), row = Math.min(g.rows() - 1, (int) (v / l.h() * g.rows()));
        int i = tileIndex(g, col, row, master.tiles().size(), level.getGameTime());
        if (i < 0) {
            sp.displayClientMessage(Component.translatable("data.nuclearsq.msg.none"), true);
            return InteractionResult.SUCCESS;
        }
        DataSource s = DataNet.find(level, l.cells(), master.tiles().get(i).pos());
        if (s == null) sp.displayClientMessage(Component.translatable("data.nuclearsq.msg.gone"), true);
        else DataTerminalBlock.open(sp, pos, s);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControlWallBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != DataRegistry.WALL_BE.get()) return null;
        return (lvl, pos, st, be) -> ((ControlWallBlockEntity) be).serverTick();
    }
}
