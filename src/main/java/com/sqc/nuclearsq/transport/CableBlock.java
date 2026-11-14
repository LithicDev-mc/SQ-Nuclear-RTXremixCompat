package com.sqc.nuclearsq.transport;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CableBlock extends ConduitBlock {
    public static final DirectionProperty MOUNT = DirectionProperty.create("mount");
    public CableBlock(Properties props, TransportTier tier) {
        super(props, false, tier);
        registerDefaultState(defaultBlockState().setValue(MOUNT, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOUNT, com.sqc.nuclearsq.multiblock.Waterlog.WATERLOGGED);
    }

    @Override
    protected int shapeKey(BlockState state) {
        return state.getValue(MOUNT).ordinal();
    }

    static double[] toWorld(Direction mount, double a, double b, double c) {
        return switch (mount) {
            case DOWN -> new double[]{a, b, c};
            case UP -> new double[]{a, -b, -c};
            case NORTH -> new double[]{a, -c, b};
            case EAST -> new double[]{-b, -c, a};
            case SOUTH -> new double[]{-a, -c, -b};
            case WEST -> new double[]{b, -c, -a};
        };
    }

    static double[] toLocal(Direction mount, double x, double y, double z) {
        return switch (mount) {
            case DOWN -> new double[]{x, y, z};
            case UP -> new double[]{x, -y, -z};
            case NORTH -> new double[]{x, z, -y};
            case EAST -> new double[]{z, -x, -y};
            case SOUTH -> new double[]{-x, -z, -y};
            case WEST -> new double[]{-z, x, -y};
        };
    }

    public static Direction world(Direction mount, Direction local) {
        double[] v = toWorld(mount, local.getStepX(), local.getStepY(), local.getStepZ());
        return Direction.fromDelta((int) Math.round(v[0]), (int) Math.round(v[1]), (int) Math.round(v[2]));
    }

    private static ConduitSide local(Direction mount, int conn, Direction local) {
        return ConduitSide.of(conn, world(mount, local));
    }

    public static boolean onTray(Direction m, int conn) {
        for (Direction d : Direction.values()) if (d.getAxis() != m.getAxis() && ConduitSide.of(conn, d) != ConduitSide.NONE) return true;
        return ConduitSide.of(conn, m) == ConduitSide.NONE;
    }

    private static VoxelShape box(Direction mount, double[] b) {
        double[] p = toWorld(mount, b[0] - 8, b[1] - 8, b[2] - 8), q = toWorld(mount, b[3] - 8, b[4] - 8, b[5] - 8);
        return Block.box(Math.min(p[0], q[0]) + 8, Math.min(p[1], q[1]) + 8, Math.min(p[2], q[2]) + 8,
                Math.max(p[0], q[0]) + 8, Math.max(p[1], q[1]) + 8, Math.max(p[2], q[2]) + 8);
    }

    private static double[] yaw(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (d) {
            case EAST -> new double[]{16 - z1, y0, x0, 16 - z0, y1, x1};
            case SOUTH -> new double[]{16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0};
            case WEST -> new double[]{z0, y0, 16 - x1, z1, y1, 16 - x0};
            default -> new double[]{x0, y0, z0, x1, y1, z1};
        };
    }

    @Override
    protected VoxelShape shape(BlockState state, int conn) {
        Direction m = state.getValue(MOUNT);
        if (!onTray(m, conn)) return super.shape(state, conn);
        Direction edge = edgeSide(m, conn);
        VoxelShape s = box(m, new double[]{5, 0, 5, 11, 6, 11});
        for (Direction l : Direction.Plane.HORIZONTAL) {
            ConduitSide side = local(m, conn, l);
            if (side == ConduitSide.NONE) continue;
            double[] b = world(m, l) == edge ? yaw(l, 3, 0, 0, 13, 16, 6)
                    : side == ConduitSide.PORT ? yaw(l, 5, 0, 0, 11, 11.25, 5) : yaw(l, 5, 0, 0, 11, 6, 5);
            s = Shapes.or(s, box(m, b));
        }
        if (edge != null) return s;
        ConduitSide up = local(m, conn, Direction.UP);
        if (up != ConduitSide.NONE) s = Shapes.or(s, box(m, new double[]{6, 6, 6, 10, 16, 10}));
        if (up == ConduitSide.PORT) s = Shapes.or(s, box(m, new double[]{3, 13, 3, 13, 16, 13}));
        return s;
    }

    @Override
    public Direction clickedSide(BlockState state, int conn, BlockPos pos, BlockHitResult hit) {
        Direction m = state.getValue(MOUNT);
        if (!onTray(m, conn)) return super.clickedSide(state, conn, pos, hit);
        Vec3 w = hit.getLocation().subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double[] v = toLocal(m, w.x, w.y, w.z);
        double ax = Math.abs(v[0]), az = Math.abs(v[2]);
        Direction l;
        if (Math.max(ax, az) > 3 / 16.0) l = ax > az ? (v[0] > 0 ? Direction.EAST : Direction.WEST) : (v[2] > 0 ? Direction.SOUTH : Direction.NORTH);
        else if (v[1] > -2 / 16.0 + 0.01) l = Direction.UP;
        else return hit.getDirection();
        Direction d = world(m, l);
        return d == edgeSide(m, conn) ? m.getOpposite() : d;
    }

    @Override
    protected BlockState placementState(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        BlockState against = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(face.getOpposite()));
        Direction mount = !ctx.replacingClickedOnBlock() && against.getBlock() instanceof CableBlock
                ? against.getValue(MOUNT) : face.getOpposite();
        return defaultBlockState().setValue(MOUNT, mount);
    }

    @Override
    protected ConduitSide side(BlockState state, LevelAccessor level, BlockPos pos, Direction dir) {
        ConduitSide s = super.side(state, level, pos, dir);
        if (s == ConduitSide.PULL) return ConduitSide.PORT;
        Direction m = state.getValue(MOUNT);
        if (s == ConduitSide.ON && dir.getAxis() != m.getAxis()) {
            BlockState n = level.getBlockState(pos.relative(dir));
            if (n.getBlock() instanceof CableBlock && n.getValue(MOUNT) != m) return n.getValue(MOUNT) == dir ? ConduitSide.ON : ConduitSide.PORT;
        }
        return s;
    }

    @Override
    protected int connections(BlockState state, LevelAccessor level, BlockPos pos) {
        return edge(state.getValue(MOUNT), super.connections(state, level, pos), level, pos);
    }

    @Override
    protected int hold(BlockState state, Level level, BlockPos pos, int conn) {
        Direction m = state.getValue(MOUNT);
        BlockPos under = pos.relative(m);
        if (!onTray(m, conn) || edgeSide(m, conn) != null) return 0;
        boolean stacked = m == Direction.DOWN && ConduitSide.of(conn, m) == ConduitSide.NONE && floorTray(level, under);
        if (!stacked && !level.getBlockState(under).getCollisionShape(level, under).isEmpty()) return 0;
        Direction.Axis run = null;
        int arms = 0;
        for (Direction d : Direction.values()) {
            if (d.getAxis() == m.getAxis() || ConduitSide.of(conn, d) == ConduitSide.NONE) continue;
            arms++;
            run = run == null || run == d.getAxis() ? d.getAxis() : m.getAxis();
        }
        boolean carried = carried(level, pos, m);
        if (arms == 2 && run != m.getAxis() && (pos.get(run) & 3) != 0 && !carried) return 0;
        for (Direction d : Direction.values()) {
            if (!carried && d.getAxis() != m.getAxis() && ConduitSide.of(conn, d) == ConduitSide.NONE && sturdy(level, pos, d)) return 1 << d.ordinal();
        }
        for (int n = 1; m == Direction.DOWN && n <= POST_MAX + 1; n++) {
            BlockPos p = pos.below(n);
            if ((n > 1 || stacked) && floorTray(level, p)) return 1 << Direction.DOWN.ordinal() | ((barX(level, p) ? 1 : 2) * POST_CABLE + n) << 6;
            BlockState b = level.getBlockState(p);
            if (b.getCollisionShape(level, p).isEmpty()) continue;
            if (n > 1 && b.isFaceSturdy(level, p, Direction.UP)) return 1 << Direction.DOWN.ordinal() | (n - 1) << 6;
            break;
        }
        if (carried) return 1 << Direction.DOWN.ordinal() | POST_BAR << 6;
        Direction up = m.getOpposite();
        return ConduitSide.of(conn, up) == ConduitSide.NONE && sturdy(level, pos, up) ? 1 << up.ordinal() : 0;
    }

    public static final int POST_MAX = 8;
    public static final int POST_CABLE = 16;
    public static final int POST_BAR = 15;

    static boolean barX(BlockGetter level, BlockPos p) {
        int c = connOf(level, p);
        return free(c, Direction.EAST);
    }

    private static boolean free(int conn, Direction d) {
        return ConduitSide.of(conn, d) == ConduitSide.NONE && ConduitSide.of(conn, d.getOpposite()) == ConduitSide.NONE;
    }

    static boolean floorTray(BlockGetter level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!(s.getBlock() instanceof CableBlock) || s.getValue(MOUNT) != Direction.DOWN) return false;
        int c = connOf(level, p);
        return onTray(Direction.DOWN, c) && edgeSide(Direction.DOWN, c) == null && (free(c, Direction.EAST) || free(c, Direction.NORTH));
    }

    private static boolean carried(Level level, BlockPos pos, Direction m) {
        if (m != Direction.DOWN) return false;
        for (int n = 1; n <= POST_MAX + 1; n++) {
            BlockPos p = pos.above(n);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof CableBlock) {
                int h = level.getBlockEntity(p) instanceof ConduitBlockEntity be ? be.hold() : 0;
                return s.getValue(MOUNT) == Direction.DOWN && (h & 1 << Direction.DOWN.ordinal()) != 0 && h >>> 6 > POST_CABLE
                        && (h >>> 6) % POST_CABLE == n;
            }
            if (!s.getCollisionShape(level, p).isEmpty()) return false;
        }
        return false;
    }

    @Override
    public void refresh(Level level, BlockPos pos) {
        ConduitBlockEntity be = level.isClientSide ? null : level.getBlockEntity(pos) instanceof ConduitBlockEntity c ? c : null;
        long before = be == null ? 0 : (long) be.conn() << 32 | be.hold() & 0xFFFFFFFFL;
        super.refresh(level, pos);
        if (be != null && before != ((long) be.conn() << 32 | be.hold() & 0xFFFFFFFFL)) refreshColumn(level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!old.is(state.getBlock())) refreshColumn(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        super.onRemove(state, level, pos, now, moved);
        if (!now.is(state.getBlock())) refreshColumn(level, pos);
    }

    private static void refreshColumn(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        for (Direction d : new Direction[]{Direction.UP, Direction.DOWN}) {
            for (int n = 1; n <= POST_MAX + 1; n++) {
                BlockPos p = pos.relative(d, n);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof CableBlock c) {
                    c.refresh(level, p);
                    break;
                }
                if (!s.getCollisionShape(level, p).isEmpty()) break;
            }
        }
    }

    private static boolean sturdy(Level level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        return level.getBlockState(n).isFaceSturdy(level, n, d.getOpposite());
    }

    private int edge(Direction m, int conn, LevelAccessor level, BlockPos pos) {
        Direction up = m.getOpposite();
        Direction e = edgeTarget(m, conn, level, pos);
        if (e != null) {
            conn = ConduitSide.with(ConduitSide.with(conn, up, ConduitSide.PORT), e, ConduitSide.PORT);
            for (Direction d : Direction.values()) {
                if (d.getAxis() != m.getAxis() && d != e && ConduitSide.of(conn, d) == ConduitSide.PORT) conn = ConduitSide.with(conn, d, ConduitSide.ON);
            }
        } else if (edgeSide(m, conn) != null) {
            conn = ConduitSide.with(conn, up, ConduitSide.ON);
        }
        return conn;
    }

    @Nullable
    private Direction edgeTarget(Direction m, int conn, LevelAccessor level, BlockPos pos) {
        Direction up = m.getOpposite();
        if (ConduitSide.of(conn, up) == ConduitSide.NONE) return null;
        BlockState n = level.getBlockState(pos.relative(up));
        if (!(n.getBlock() instanceof CableBlock) || n.getValue(MOUNT).getAxis() == m.getAxis()) return null;
        return n.getValue(MOUNT);
    }

    @Nullable
    public static Direction edgeSide(Direction m, int conn) {
        if (ConduitSide.of(conn, m.getOpposite()) != ConduitSide.PORT) return null;
        Direction found = null;
        for (Direction d : Direction.values()) {
            if (d.getAxis() == m.getAxis() || ConduitSide.of(conn, d) != ConduitSide.PORT) continue;
            if (found != null) return null;
            found = d;
        }
        return found;
    }

    @Nullable
    public static Direction edgeSide(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof CableBlock ? edgeSide(s.getValue(MOUNT), connOf(level, pos)) : null;
    }
}
