package com.sqc.nuclearsq.explosion.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sqc.nuclearsq.explosion.NuclearExplosion;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;

final class NukeFireball {
    private NukeFireball() {}

    private static final float MAX_R = 26f;
    private static final float COOL = 3.5f;
    private static final float FADE_FROM = 9f, FADE_TO = 13f;
    private static final double SHOCK_REACH = 500.0;
    private static final float VEIL_TIME = 1.0f;
    private static final float[] DUST = {0.66f, 0.60f, 0.52f};
    private static final float[] VEIL = {0.92f, 0.92f, 0.94f};

    private static final float[][] HEAT = {
            {1.00f, 1.00f, 0.98f, 0.90f},
            {0.70f, 1.00f, 0.86f, 0.45f},
            {0.42f, 1.00f, 0.55f, 0.15f},
            {0.20f, 0.85f, 0.25f, 0.05f},
            {0.05f, 0.45f, 0.07f, 0.02f},
            {0.00f, 0.20f, 0.03f, 0.01f},
    };

    private static double camX, camY, camZ;

    static void render(Matrix4f m, ClientLevel level, double cx, double cy, double cz, float pt) {
        camX = cx; camY = cy; camZ = cz;
        float light = 0.3f + 0.7f * level.getSkyDarken(pt);

        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (MushroomCloud.Cloud c : MushroomCloud.CLOUDS) {
            float ticks = c.age + pt;
            shockRing(b, m, level, c, ticks, light);
            veil(b, m, c, ticks);
        }
        draw(b, false);

        b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (MushroomCloud.Cloud c : MushroomCloud.CLOUDS) fireball(b, m, c, (c.age + pt) / 20f);
        draw(b, true);
    }

    private static void draw(BufferBuilder b, boolean additive) {
        MeshData mesh = b.build();
        if (mesh == null) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    static float radius(float s, float t) {
        float full = MAX_R * s;
        float start = MushroomCloud.START_DELAY / 20f;
        if (t <= start) return full * (0.25f + 0.75f * MushroomCloud.ease(t / 1.0f));
        float f = smooth((t - start) / 7f);
        return full + (MushroomCloud.capR(s, t - start) * 0.55f - full) * f;
    }

    static float lift(float s, float t) {
        float start = MushroomCloud.START_DELAY / 20f;
        float own = radius(s, t) * 0.35f + 2.5f * s * t;
        return t <= start ? own : Math.max(own, MushroomCloud.headH(s, t - start));
    }

    private static void fireball(BufferBuilder b, Matrix4f m, MushroomCloud.Cloud c, float t) {
        if (t >= FADE_TO) return;
        float s = c.s;
        float fade = 1f - smooth((t - FADE_FROM) / (FADE_TO - FADE_FROM));
        float temp = (float) Math.exp(-t / COOL);
        float[] col = heat(temp);
        float glow = fade * (0.35f + 0.65f * temp) * (c.heavy ? 1f : 0.9f);
        double x = c.x, y = c.y + lift(s, t), z = c.z;
        float r = radius(s, t);
        sphere(b, m, x, y, z, r, t, col, glow, 0.13);
        if (temp > 0.25f) sphere(b, m, x, y, z, r * 1.3f, t * 0.5f, col, glow * 0.22f * (temp - 0.25f) / 0.75f, 0.06);
    }

    private static void sphere(BufferBuilder b, Matrix4f m, double x, double y, double z, float r, float t,
            float[] col, float glow, double bumps) {
        if (glow <= 0.005f || r < 0.5f) return;
        int nu = 40, nv = 20;
        double vx = camX - x, vy = camY - y, vz = camZ - z;
        double vl = Math.max(1e-3, Math.sqrt(vx * vx + vy * vy + vz * vz));
        vx /= vl; vy /= vl; vz /= vl;
        NukeCloudMesh.begin(nu, nv);
        for (int ii = 0; ii <= nu; ii++)
            for (int jj = 0; jj <= nv; jj++) {
                    double phi = ii * Math.PI * 2 / nu, th = jj * Math.PI / nv - Math.PI / 2;
                    double nx = Math.cos(th) * Math.cos(phi), ny = Math.sin(th), nz = Math.cos(th) * Math.sin(phi);
                    double lump = 1.0 + bumps * 2 * (noise(nx * 2.2, ny * 2.2 - t * 0.6, nz * 2.2) - 0.5);
                    double rr = r * lump;
                    double facing = Math.abs(nx * vx + ny * vy + nz * vz);
                    float bright = (float) ((0.35 + 0.65 * facing) * (1.0 + 0.25 * (lump - 1.0) / bumps));
                    float a = Mth.clamp(glow * bright, 0f, 1f);
                    point(ii, jj, x + nx * rr, y + ny * rr, z + nz * rr, col[0], col[1], col[2], a);
                }
        NukeCloudMesh.quads(b, m, nu, nv);
    }

    static float[] heat(float temp) {
        for (int i = 1; i < HEAT.length; i++) {
            float[] hi = HEAT[i - 1], lo = HEAT[i];
            if (temp >= lo[0]) {
                float f = (temp - lo[0]) / Math.max(1e-4f, hi[0] - lo[0]);
                return new float[]{lo[1] + (hi[1] - lo[1]) * f, lo[2] + (hi[2] - lo[2]) * f, lo[3] + (hi[3] - lo[3]) * f};
            }
        }
        float[] last = HEAT[HEAT.length - 1];
        return new float[]{last[1], last[2], last[3]};
    }

    private static void veil(BufferBuilder b, Matrix4f m, MushroomCloud.Cloud c, float ticks) {
        float t = ticks / 20f;
        if (t >= VEIL_TIME) return;
        double r = ticks * NuclearExplosion.SHOCK_SPEED;
        if (r < 2) return;
        float a = 0.28f * (1f - t / VEIL_TIME) * Math.min(1f, t * 8f);
        int nu = 48, nv = 10;
        NukeCloudMesh.begin(nu, nv);
        for (int ii = 0; ii <= nu; ii++)
            for (int jj = 0; jj <= nv; jj++) {
                    double phi = ii * Math.PI * 2 / nu, th = jj * Math.PI / 2 / nv;
                    double px = c.x + Math.cos(th) * Math.cos(phi) * r, py = c.y + Math.sin(th) * r * 0.8, pz = c.z + Math.cos(th) * Math.sin(phi) * r;
                    point(ii, jj, px, py, pz, VEIL[0], VEIL[1], VEIL[2], a * (float) (1.0 - 0.7 * Math.sin(th)));
                }
        NukeCloudMesh.quads(b, m, nu, nv);
    }

    private static void shockRing(BufferBuilder b, Matrix4f m, ClientLevel level, MushroomCloud.Cloud c, float ticks, float light) {
        float s = c.s;
        double front = ticks * NuclearExplosion.SHOCK_SPEED;
        double reach = SHOCK_REACH * s;
        if (front < MAX_R * s * 0.8 || front > reach) return;
        double prog = front / reach;
        float a = (float) (0.6 * Math.pow(1.0 - prog, 1.2));
        if (a < 0.01f) return;
        double h = (3.0 + 5.0 * s) * (1.0 - 0.5 * prog);
        double back = 7.0 * s;
        float r0 = DUST[0] * light, g0 = DUST[1] * light, b0 = DUST[2] * light;
        int nu = Math.min(320, 64 + (int) (front / 2));
        double[] gFront = new double[nu + 1], gBack = new double[nu + 1];
        for (int i = 0; i <= nu; i++) {
            double phi = i * Math.PI * 2 / nu, cs = Math.cos(phi), sn = Math.sin(phi);
            gFront[i] = NukeDebris.groundY(level, c.x + cs * front, c.z + sn * front, c.y);
            gBack[i] = NukeDebris.groundY(level, c.x + cs * (front - back), c.z + sn * (front - back), c.y);
        }
        for (int i = 0; i < nu; i++) {
            double p0 = i * Math.PI * 2 / nu, p1 = (i + 1) * Math.PI * 2 / nu;
            float n0 = (float) (0.6 + 0.8 * noise(Math.cos(p0) * 5, ticks * 0.05, Math.sin(p0) * 5));
            float n1 = (float) (0.6 + 0.8 * noise(Math.cos(p1) * 5, ticks * 0.05, Math.sin(p1) * 5));
            double x0 = c.x + Math.cos(p0) * front, z0 = c.z + Math.sin(p0) * front;
            double x1 = c.x + Math.cos(p1) * front, z1 = c.z + Math.sin(p1) * front;
            vertex(b, m, x0, gFront[i] + h * n0, z0, r0, g0, b0, 0f);
            vertex(b, m, x1, gFront[i + 1] + h * n1, z1, r0, g0, b0, 0f);
            vertex(b, m, x1, gFront[i + 1], z1, r0, g0, b0, a * Math.min(1f, n1));
            vertex(b, m, x0, gFront[i], z0, r0, g0, b0, a * Math.min(1f, n0));
            double bx0 = c.x + Math.cos(p0) * (front - back), bz0 = c.z + Math.sin(p0) * (front - back);
            double bx1 = c.x + Math.cos(p1) * (front - back), bz1 = c.z + Math.sin(p1) * (front - back);
            vertex(b, m, bx0, gBack[i] + 0.6, bz0, r0, g0, b0, 0f);
            vertex(b, m, bx1, gBack[i + 1] + 0.6, bz1, r0, g0, b0, 0f);
            vertex(b, m, x1, gFront[i + 1] + 0.6, z1, r0, g0, b0, a * 0.5f);
            vertex(b, m, x0, gFront[i] + 0.6, z0, r0, g0, b0, a * 0.5f);
        }
    }

    private static void point(int ii, int jj, double x, double y, double z, float r, float g, float bl, float a) {
        NukeCloudMesh.put(ii, jj, (float) (x - camX), (float) (y - camY), (float) (z - camZ),
                Mth.clamp(r, 0f, 1f), Mth.clamp(g, 0f, 1f), Mth.clamp(bl, 0f, 1f), Mth.clamp(a, 0f, 1f));
    }

    private static void vertex(BufferBuilder b, Matrix4f m, double x, double y, double z, float r, float g, float bl, float a) {
        b.addVertex(m, (float) (x - camX), (float) (y - camY), (float) (z - camZ))
                .setColor(Mth.clamp(r, 0f, 1f), Mth.clamp(g, 0f, 1f), Mth.clamp(bl, 0f, 1f), Mth.clamp(a, 0f, 1f));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private static double noise(double x, double y, double z) {
        int ix = Mth.floor(x), iy = Mth.floor(y), iz = Mth.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        double x00 = lerp(fx, hash(ix, iy, iz), hash(ix + 1, iy, iz)), x10 = lerp(fx, hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz));
        double x01 = lerp(fx, hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1)), x11 = lerp(fx, hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1));
        return lerp(fz, lerp(fy, x00, x10), lerp(fy, x01, x11));
    }

    private static double lerp(double f, double a, double b) {
        return a + (b - a) * f;
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }
}
