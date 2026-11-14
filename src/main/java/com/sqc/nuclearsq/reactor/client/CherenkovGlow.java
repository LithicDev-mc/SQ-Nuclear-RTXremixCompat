package com.sqc.nuclearsq.reactor.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import com.sqc.nuclearsq.NuclearClientConfig;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class CherenkovGlow {
    private CherenkovGlow() {}

    private static final float CORE_MIN = 0.21f, CORE_MAX = 0.79f;
    private static final float RADIUS = 0.42f;
    private static final float MAX_ALPHA = 0.25f;
    private static final int R = 70, G = 150, B = 255;
    private static final int MAX_CELLS = 12, ROWS = 3;
    private static final int MAX_SAMPLES = 12;
    private static final int MAX_QUEUE = 4096;

    private record Pending(Matrix4f pose, float strength, float clip, int height) {}

    private static final List<Pending> QUEUE = new ArrayList<>();
    private static final float[] PTS = new float[(MAX_CELLS + 1) * (MAX_CELLS + 1) * 3];
    private static final int[] ALPHA = new int[(MAX_CELLS + 1) * (MAX_CELLS + 1)];
    private static final double[] P = new double[3], D = new double[3];
    private static final Matrix4f TMP = new Matrix4f(), TMP_INV = new Matrix4f(), JOIN = new Matrix4f();
    private static final Vector3f TMP_V = new Vector3f();

    private static final RenderType GLOW = RenderType.create("nuclearsq_cherenkov", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private enum Face {
        DOWN(1, false, 0, 2), UP(1, true, 2, 0), NORTH(2, false, 1, 0), SOUTH(2, true, 0, 1), WEST(0, false, 2, 1), EAST(0, true, 1, 2);

        final int axis, u, v;
        final boolean max;

        Face(int axis, boolean max, int u, int v) {
            this.axis = axis;
            this.max = max;
            this.u = u;
            this.v = v;
        }
    }

    public static void draw(PoseStack pose, MultiBufferSource buffers, float strength) {
        draw(pose, buffers, strength, 0f);
    }

    public static void draw(PoseStack pose, MultiBufferSource buffers, float strength, float clip) {
        if (strength <= 0.01f || QUEUE.size() >= MAX_QUEUE) return;
        Matrix4f m = pose.last().pose();
        for (int i = QUEUE.size() - 1; i >= 0 && i >= QUEUE.size() - 16; i--) {
            Pending q = QUEUE.get(i);
            if (Math.abs(q.strength() - strength) > 0.02f || q.clip() != clip) continue;
            if (m.equals(JOIN.set(q.pose()).translate(0, -1, 0), 1e-4f)) {
                QUEUE.set(i, new Pending(new Matrix4f(m), q.strength(), clip, q.height() + 1));
                return;
            }
            if (m.equals(JOIN.set(q.pose()).translate(0, q.height(), 0), 1e-4f)) {
                QUEUE.set(i, new Pending(q.pose(), q.strength(), clip, q.height() + 1));
                return;
            }
        }
        QUEUE.add(new Pending(new Matrix4f(m), strength, clip, 1));
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            QUEUE.clear();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || QUEUE.isEmpty()) return;
        MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
        buf.endBatch();
        Matrix4f view = event.getPoseStack().last().pose();
        float maxDist = Math.min(NuclearClientConfig.viewDistance(), NuclearClientConfig.glowDistance());
        VertexConsumer vc = buf.getBuffer(GLOW);
        for (Pending p : QUEUE) render(TMP.set(view).mul(p.pose()), vc, p.strength(), p.clip(), p.height(), maxDist);
        buf.endBatch(GLOW);
        QUEUE.clear();
    }

    private static void render(Matrix4f m, VertexConsumer vc, float strength, float clip, int height, float maxDist) {
        Vector3f c = TMP_INV.set(m).invert().transformPosition(TMP_V.zero());
        double[] cam = {c.x, c.y, c.z};
        double r = RADIUS * (0.6 + 0.4 * strength);
        double[] min = {CORE_MIN - r, 0, CORE_MIN - r}, max = {CORE_MAX + r, height, CORE_MAX + r};
        if (clip > 0f) {
            for (int i = 0; i < 3; i += 2) {
                min[i] = Math.max(min[i], 0.5 - clip);
                max[i] = Math.min(max[i], 0.5 + clip);
            }
        }
        double dist = Math.sqrt(distSq(min, max, cam));
        if (dist > maxDist) return;
        boolean inside = dist == 0;
        int cells = Mth.clamp((int) Math.ceil(MAX_CELLS / Math.max(1, dist / 4)), 3, MAX_CELLS);
        float a = Math.min(1f, strength) * MAX_ALPHA;
        for (Face f : Face.values()) {
            if (!inside && !(f.max ? cam[f.axis] > max[f.axis] : cam[f.axis] < min[f.axis])) continue;
            drawFace(f, min, max, r, cam, inside, cells, a, vc, m);
        }
    }

    private static void drawFace(Face f, double[] min, double[] max, double r, double[] cam, boolean inside, int cells, float a,
                                 VertexConsumer vc, Matrix4f m) {
        int ax = f.axis;
        double plane = f.max ? max[ax] : min[ax];
        double lenU = max[f.u] - min[f.u], lenV = max[f.v] - min[f.v];
        int nu = f.u == 1 ? ROWS : cells, nv = f.v == 1 ? ROWS : cells;
        float[] pts = PTS;
        int[] alpha = ALPHA;
        double[] p = P;
        for (int j = 0; j <= nv; j++) {
            for (int i = 0; i <= nu; i++) {
                p[ax] = plane;
                p[f.u] = min[f.u] + lenU * i / nu;
                p[f.v] = min[f.v] + lenV * j / nv;
                int k = j * (nu + 1) + i;
                pts[k * 3] = (float) p[0];
                pts[k * 3 + 1] = (float) p[1];
                pts[k * 3 + 2] = (float) p[2];
                alpha[k] = Math.round(Math.min(1f, along(cam, p, min, max, r, inside)) * a * 255);
            }
        }
        for (int j = 0; j < nv; j++) {
            for (int i = 0; i < nu; i++) {
                int k00 = j * (nu + 1) + i, k10 = k00 + 1, k01 = k00 + nu + 1, k11 = k01 + 1;
                if (alpha[k00] == 0 && alpha[k10] == 0 && alpha[k01] == 0 && alpha[k11] == 0) continue;
                if (inside) {
                    vertex(vc, m, pts, alpha, k00);
                    vertex(vc, m, pts, alpha, k01);
                    vertex(vc, m, pts, alpha, k11);
                    vertex(vc, m, pts, alpha, k10);
                } else {
                    vertex(vc, m, pts, alpha, k00);
                    vertex(vc, m, pts, alpha, k10);
                    vertex(vc, m, pts, alpha, k11);
                    vertex(vc, m, pts, alpha, k01);
                }
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float[] pts, int[] alpha, int k) {
        vc.addVertex(m, pts[k * 3], pts[k * 3 + 1], pts[k * 3 + 2]).setColor(R, G, B, alpha[k]);
    }

    private static double density(double x, double z, double r) {
        double dx = x < CORE_MIN ? CORE_MIN - x : x > CORE_MAX ? x - CORE_MAX : 0;
        double dz = z < CORE_MIN ? CORE_MIN - z : z > CORE_MAX ? z - CORE_MAX : 0;
        if (dx == 0 && dz == 0) return -1;
        double t = Math.sqrt(dx * dx + dz * dz) / r;
        if (t >= 1) return 0;
        return (1 - t) * (1 - t) * (1 - t);
    }

    private static float along(double[] cam, double[] point, double[] min, double[] max, double r, boolean inside) {
        double dx = point[0] - cam[0], dy = point[1] - cam[1], dz = point[2] - cam[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return 0;
        double[] d = D;
        d[0] = dx / len;
        d[1] = dy / len;
        d[2] = dz / len;
        double start, end;
        if (inside) {
            start = 0;
            end = len;
        } else {
            start = len;
            end = Double.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                if (d[i] > 1e-9) end = Math.min(end, (max[i] - cam[i]) / d[i]);
                else if (d[i] < -1e-9) end = Math.min(end, (min[i] - cam[i]) / d[i]);
            }
            if (end <= start) return 0;
        }
        int samples = Mth.clamp((int) Math.ceil((end - start) / (r / 4)), 4, MAX_SAMPLES);
        double step = (end - start) / samples, sum = 0, peak = 0;
        for (int k = 0; k < samples; k++) {
            double t = start + (k + 0.5) * step;
            double den = density(cam[0] + d[0] * t, cam[2] + d[2] * t, r);
            if (den < 0) break;
            sum += den;
            peak = Math.max(peak, den);
        }
        return (float) Math.min(sum * step / (2.0 / 3.0 * r), peak);
    }

    private static double distSq(double[] min, double[] max, double[] p) {
        double s = 0;
        for (int i = 0; i < 3; i++) {
            double d = Math.max(0, Math.max(min[i] - p[i], p[i] - max[i]));
            s += d * d;
        }
        return s;
    }
}
