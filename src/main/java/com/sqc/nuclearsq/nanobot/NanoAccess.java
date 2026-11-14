package com.sqc.nuclearsq.nanobot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class NanoAccess {
    private NanoAccess() {}

    static final int WINDOW = 16, MAX_VISITED = 60000;

    public record Result(@Nullable String fail, List<BlockPos> tunnel, @Nullable BlockPos ref) {
        public boolean ok() {
            return fail == null;
        }
    }

    public static final String NO_WAY = "no_way";

    static boolean passable(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p) || level.isOutsideBuildHeight(p)) return false;
        BlockState st = level.getBlockState(p);
        return st.getCollisionShape(level, p).isEmpty() && st.getFluidState().isEmpty();
    }

    static boolean node(ServerLevel level, BlockPos p) {
        if (!passable(level, p)) return false;
        for (Direction d : Direction.values()) if (!passable(level, p.relative(d))) return true;
        return false;
    }

    private static final int[][] EDGES = edges();

    private static int[][] edges() {
        List<int[]> out = new ArrayList<>();
        for (int a = -1; a <= 1; a += 2) {
            for (int b = -1; b <= 1; b += 2) {
                out.add(new int[] {a, b, 0});
                out.add(new int[] {a, 0, b});
                out.add(new int[] {0, a, b});
            }
        }
        return out.toArray(new int[0][]);
    }

    static Set<Long> reachable(ServerLevel level, BlockPos start, BlockPos lo, BlockPos hi) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        seen.add(start.asLong());
        open.add(start);
        while (!open.isEmpty() && seen.size() < MAX_VISITED) {
            BlockPos a = open.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = a.relative(d);
                if (inBox(n, lo, hi) && !seen.contains(n.asLong()) && node(level, n)) {
                    seen.add(n.asLong());
                    open.add(n);
                }
            }
            for (int[] e : EDGES) {
                BlockPos n = a.offset(e[0], e[1], e[2]);
                if (!inBox(n, lo, hi) || seen.contains(n.asLong()) || !node(level, n)) continue;
                BlockPos m1, m2;
                if (e[0] == 0) {
                    m1 = a.offset(0, e[1], 0);
                    m2 = a.offset(0, 0, e[2]);
                } else if (e[1] == 0) {
                    m1 = a.offset(e[0], 0, 0);
                    m2 = a.offset(0, 0, e[2]);
                } else {
                    m1 = a.offset(e[0], 0, 0);
                    m2 = a.offset(0, e[1], 0);
                }
                if (!passable(level, m1) && !passable(level, m2)) continue;
                seen.add(n.asLong());
                open.add(n);
            }
        }
        return seen;
    }

    private static boolean inBox(BlockPos p, BlockPos lo, BlockPos hi) {
        return p.getX() >= lo.getX() && p.getX() <= hi.getX() && p.getY() >= lo.getY() && p.getY() <= hi.getY() && p.getZ() >= lo.getZ() && p.getZ() <= hi.getZ();
    }

    public static int topLayer(ServerLevel level, Collection<BlockPos> cells) {
        int top = Integer.MIN_VALUE;
        for (BlockPos p : cells) if (!level.getBlockState(p).isAir()) top = Math.max(top, p.getY());
        return top;
    }

    public static Result plan(ServerLevel level, ServerPlayer sp, Collection<BlockPos> cells, boolean tunnelAllowed, int maxTunnel) {
        int top = topLayer(level, cells);
        if (top == Integer.MIN_VALUE) return new Result(null, List.of(), sp.blockPosition());
        Set<Long> region = new HashSet<>();
        List<BlockPos> layer = new ArrayList<>();
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : cells) {
            region.add(p.asLong());
            x0 = Math.min(x0, p.getX());
            y0 = Math.min(y0, p.getY());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX());
            y1 = Math.max(y1, p.getY());
            z1 = Math.max(z1, p.getZ());
            if (p.getY() == top && !level.getBlockState(p).isAir()) layer.add(p);
        }
        BlockPos pp = sp.blockPosition();
        BlockPos lo = new BlockPos(Math.min(x0, pp.getX()) - WINDOW, Math.min(y0, pp.getY()) - WINDOW, Math.min(z0, pp.getZ()) - WINDOW);
        BlockPos hi = new BlockPos(Math.max(x1, pp.getX()) + WINDOW, Math.max(y1, pp.getY()) + WINDOW, Math.max(z1, pp.getZ()) + WINDOW);
        BlockPos start = pp;
        for (int i = 0; i < 16 && !passable(level, start); i++) start = start.above();
        Set<Long> seen = reachable(level, start, lo, hi);
        BlockPos best = null;
        for (BlockPos t : layer) {
            for (Direction d : Direction.values()) {
                BlockPos n = t.relative(d);
                if (seen.contains(n.asLong()) && !region.contains(n.asLong())) {
                    if (best == null || n.distSqr(pp) < best.distSqr(pp)) best = n;
                }
            }
        }
        if (best != null) return new Result(null, List.of(), pp);
        if (!tunnelAllowed) return new Result(NO_WAY, List.of(), null);

        Set<Long> goals = new HashSet<>();
        for (BlockPos t : layer) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = t.relative(d);
                if (!region.contains(n.asLong())) goals.add(n.asLong());
            }
        }
        Map<Long, List<BlockPos>> byColumn = new HashMap<>();
        for (long l : seen) {
            BlockPos s = BlockPos.of(l);
            byColumn.computeIfAbsent(col(s.getX(), s.getZ()), k -> new ArrayList<>()).add(s);
        }
        List<BlockPos> bestTunnel = null;
        BlockPos bestRef = null;
        for (int stage = 0; stage < 2 && bestTunnel == null; stage++) {
            List<BlockPos> goalList = new ArrayList<>();
            for (long g : goals) goalList.add(BlockPos.of(g));
            goalList.sort(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY));
            List<Long> columns = new ArrayList<>(byColumn.keySet());
            java.util.Collections.sort(columns);
            for (long c : columns) {
                int cx = (int) (c >> 32), cz = (int) c;
                for (BlockPos g : goalList) {
                    int horiz = Math.abs(g.getX() - cx) + Math.abs(g.getZ() - cz);
                    if (stage == 0 ? horiz != 0 : horiz == 0 || horiz > WINDOW / 2) continue;
                    for (BlockPos s : byColumn.get(c)) {
                        List<BlockPos> path = path(level, sp, s, top, g, region, maxTunnel);
                        if (path == null) continue;
                        if (bestTunnel == null || path.size() < bestTunnel.size()
                                || (path.size() == bestTunnel.size() && s.distSqr(pp) < bestRef.distSqr(pp))) {
                            bestTunnel = path;
                            bestRef = g;
                        }
                    }
                }
            }
        }
        if (bestTunnel == null) return new Result(NO_WAY, List.of(), null);
        return new Result(null, bestTunnel, bestRef);
    }

    private static long col(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Nullable
    private static List<BlockPos> path(ServerLevel level, ServerPlayer sp, BlockPos s, int top, BlockPos g, Set<Long> region, int max) {
        List<BlockPos> out = new ArrayList<>();
        int dy = Integer.signum(top - s.getY());
        BlockPos p = s;
        while (p.getY() != top) {
            p = p.offset(0, dy, 0);
            if (!take(level, sp, p, region, out, max)) return null;
        }
        while (p.getX() != g.getX()) {
            p = p.offset(Integer.signum(g.getX() - p.getX()), 0, 0);
            if (!take(level, sp, p, region, out, max)) return null;
        }
        while (p.getZ() != g.getZ()) {
            p = p.offset(0, 0, Integer.signum(g.getZ() - p.getZ()));
            if (!take(level, sp, p, region, out, max)) return null;
        }
        return out;
    }

    private static boolean take(ServerLevel level, ServerPlayer sp, BlockPos p, Set<Long> region, List<BlockPos> out, int max) {
        if (region.contains(p.asLong())) return false;
        if (passable(level, p)) return level.isLoaded(p) && !level.isOutsideBuildHeight(p);
        BlockState st = level.getBlockState(p);
        if (!level.isLoaded(p) || level.isOutsideBuildHeight(p) || st.getDestroySpeed(level, p) < 0 || !level.mayInteract(sp, p)) return false;
        if (!st.getFluidState().isEmpty()) return false;
        out.add(p.immutable());
        return out.size() <= max;
    }

    public static List<BlockPos> topDown(Collection<BlockPos> cells, net.minecraft.world.phys.Vec3 ref) {
        List<BlockPos> out = new ArrayList<>(cells);
        out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed().thenComparingDouble(p -> p.distToCenterSqr(ref))
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return out;
    }
}
