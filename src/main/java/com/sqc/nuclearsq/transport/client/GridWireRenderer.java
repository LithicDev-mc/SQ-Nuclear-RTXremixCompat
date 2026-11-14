package com.sqc.nuclearsq.transport.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.transport.GridNode;
import com.sqc.nuclearsq.transport.GridNodeBlockEntity;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GridWireRenderer implements BlockEntityRenderer<GridNodeBlockEntity> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/entity/overhead_wire_sqc.png");
    private static final double HALF = 0.25 / 16;

    public GridWireRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public boolean shouldRenderOffScreen(GridNodeBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }

    @Override
    public AABB getRenderBoundingBox(GridNodeBlockEntity be) {
        BlockPos p = be.getBlockPos();
        AABB box = new AABB(p).expandTowards(0, 25, 0);
        for (BlockPos o : be.links()) box = box.minmax(new AABB(o).expandTowards(0, 25, 0));
        return box.inflate(3);
    }

    @Override
    public void render(GridNodeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level != null && !com.sqc.nuclearsq.client.OcclusionCull.visible(level, be.getBlockPos(), getRenderBoundingBox(be))) return;
        if (level != null) renderLinks(level, be, be.getBlockPos(), pose, buffers);
    }

    public static void renderLinks(Level level, GridNode node, BlockPos origin, PoseStack pose, MultiBufferSource buffers) {
        if (node.links().isEmpty()) return;
        if (level != cacheLevel || CACHE.size() > MAX_CACHE) {
            CACHE.clear();
            cacheLevel = level;
        }
        long now = level.getGameTime();
        BlockPos me = node.nodePos();
        VertexConsumer vc = null;
        for (BlockPos o : node.links()) {
            Key key = new Key(origin.asLong(), me.asLong(), o.asLong());
            Mesh mesh = CACHE.get(key);
            if (mesh == null || now - mesh.tick >= REBUILD_TICKS + (key.hashCode() & 31) || now < mesh.tick) {
                mesh = build(level, node, origin, me, o, now);
                CACHE.put(key, mesh);
            }
            if (mesh.n == 0) continue;
            if (vc == null) vc = buffers.getBuffer(RenderType.entitySolid(TEX));
            replay(pose.last(), vc, mesh);
        }
    }

    private static final int REBUILD_TICKS = 40, MAX_CACHE = 4096;
    private static final Map<Key, Mesh> CACHE = new HashMap<>();
    private static Level cacheLevel;
    private static Mesh building;

    private record Key(long origin, long me, long other) {}

    private static final class Mesh {
        final long tick;
        float[] v = new float[8 * 64];
        int[] light = new int[64];
        int n;

        Mesh(long tick) {
            this.tick = tick;
        }
    }

    private static Mesh build(Level level, GridNode node, BlockPos origin, BlockPos me, BlockPos o, long now) {
        Mesh mesh = new Mesh(now);
        GridNode other = GridNode.node(level, o);
        if (other == null) return mesh;
        boolean mast = node instanceof GridNodeBlockEntity, otherMast = other instanceof GridNodeBlockEntity;
        if (mast == otherMast ? me.asLong() > o.asLong() : !mast) return mesh;
        building = mesh;
        try {
            for (Vec3[] pr : pairs(node.attachPoints(), other.attachPoints())) wire(level, origin, pr[0], pr[1]);
            Vec3 ea = node.earthPoint(), eb = other.earthPoint();
            if (ea != null && eb != null) wire(level, origin, ea, eb);
        } finally {
            building = null;
        }
        return mesh;
    }

    private static void replay(PoseStack.Pose last, VertexConsumer vc, Mesh m) {
        float[] v = m.v;
        for (int i = 0, k = 0; i < m.n; i++, k += 8) {
            vc.addVertex(last, v[k], v[k + 1], v[k + 2]).setColor(255, 255, 255, 255).setUv(v[k + 3], v[k + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(m.light[i]).setNormal(last, v[k + 5], v[k + 6], v[k + 7]);
        }
    }

    private static final int SEILE = 2;

    static List<Vec3[]> pairs(List<Vec3> a, List<Vec3> b) {
        if (a.isEmpty() || b.isEmpty()) return List.of();
        Vec3 ca = center(a), cb = center(b);
        double px = -(cb.z - ca.z), pz = cb.x - ca.x;
        List<Vec3> sa = new ArrayList<>(a), sb = new ArrayList<>(b);
        sa.sort(Comparator.comparingDouble(p -> (p.x - ca.x) * px + (p.z - ca.z) * pz));
        sb.sort(Comparator.comparingDouble(p -> (p.x - cb.x) * px + (p.z - cb.z) * pz));
        int k = Math.min(SEILE, Math.min(sa.size(), sb.size()));
        List<Vec3> ta = pick(sa, k), tb = pick(sb, k);
        List<Vec3[]> out = new ArrayList<>(k);
        for (int i = 0; i < k; i++) out.add(new Vec3[] {ta.get(i), tb.get(i)});
        return out;
    }

    private static Vec3 center(List<Vec3> pts) {
        Vec3 c = Vec3.ZERO;
        for (Vec3 p : pts) c = c.add(p);
        return c.scale(1.0 / pts.size());
    }

    private static List<Vec3> pick(List<Vec3> pts, int k) {
        if (pts.size() == k) return pts;
        List<Vec3> out = new ArrayList<>(k);
        for (int i = 0; i < k; i++) out.add(pts.get(k == 1 ? pts.size() / 2 : (int) Math.round(i * (pts.size() - 1) / (double) (k - 1))));
        return out;
    }

    private static void wire(Level level, BlockPos origin, Vec3 a, Vec3 b) {
        double h = Math.hypot(b.x - a.x, b.z - a.z);
        double sag = 0.035 * h + 0.15;
        int n = Math.max(8, Math.min(48, (int) (a.distanceTo(b) / 1.2)));
        Vec3 o = Vec3.atLowerCornerOf(origin);
        Vec3 prev = a.subtract(o);
        double v = 0;
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            Vec3 cur = a.lerp(b, t).subtract(o).subtract(0, 4 * sag * t * (1 - t), 0);
            int lt = LevelRenderer.getLightColor(level, BlockPos.containing(o.add(prev.add(cur).scale(0.5))));
            double len = cur.distanceTo(prev);
            segment(prev, cur, v, v + len * 2, lt);
            v += len * 2;
            prev = cur;
        }
    }

    private static void segment(Vec3 p, Vec3 q, double v0, double v1, int light) {
        Vec3 d = q.subtract(p).normalize();
        Vec3 up = Math.abs(d.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 s1 = d.cross(up).normalize().scale(HALF);
        Vec3 s2 = s1.cross(d).normalize().scale(HALF);
        Vec3[] ring = {s1.add(s2), s1.subtract(s2), s1.scale(-1).subtract(s2), s2.subtract(s1)};
        for (int k = 0; k < 4; k++) {
            Vec3 r0 = ring[k], r1 = ring[(k + 1) % 4];
            Vec3 nrm = r0.add(r1).normalize();
            Vec3 a0 = p.add(r0), a1 = p.add(r1), b0 = q.add(r0), b1 = q.add(r1);
            boolean flip = a1.subtract(a0).cross(b1.subtract(a0)).dot(nrm) < 0;
            float u0 = k * 0.25f, u1 = u0 + 0.25f;
            if (flip) {
                vertex(a0, u0, v0, light, nrm);
                vertex(b0, u0, v1, light, nrm);
                vertex(b1, u1, v1, light, nrm);
                vertex(a1, u1, v0, light, nrm);
            } else {
                vertex(a0, u0, v0, light, nrm);
                vertex(a1, u1, v0, light, nrm);
                vertex(b1, u1, v1, light, nrm);
                vertex(b0, u0, v1, light, nrm);
            }
        }
    }

    private static void vertex(Vec3 p, float u, double v, int light, Vec3 n) {
        Mesh m = building;
        if (m.n == m.light.length) {
            m.v = java.util.Arrays.copyOf(m.v, m.v.length * 2);
            m.light = java.util.Arrays.copyOf(m.light, m.light.length * 2);
        }
        int k = m.n * 8;
        float[] a = m.v;
        a[k] = (float) p.x;
        a[k + 1] = (float) p.y;
        a[k + 2] = (float) p.z;
        a[k + 3] = u;
        a[k + 4] = (float) v;
        a[k + 5] = (float) n.x;
        a[k + 6] = (float) n.y;
        a[k + 7] = (float) n.z;
        m.light[m.n++] = light;
    }
}
