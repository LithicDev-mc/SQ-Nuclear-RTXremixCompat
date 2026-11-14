package com.sqc.nuclearsq.explosion.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class NukeCloudMesh {
    private NukeCloudMesh() {}

    private static final float[] SMOKE = {0.115f, 0.105f, 0.10f};
    private static final float[] SURGE = {0.37f, 0.32f, 0.26f};
    private static final float ROLLER_ALPHA = 0.86f;
    private static final float[] GLOW = {1.0f, 0.42f, 0.10f};
    private static final float[] RING = {0.80f, 0.79f, 0.78f};

    private static double camX, camY, camZ;
    private static float light, fogR, fogG, fogB, fogStart, fogEnd;
    private static float alpha = 1f;
    private static boolean soft = false;
    private static final float[][] SHELLS = {{0.07f, 0.55f}, {0.16f, 0.34f}, {0.28f, 0.18f}, {0.42f, 0.08f}};

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || MushroomCloud.CLOUDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        camX = cam.x; camY = cam.y; camZ = cam.z;
        light = 0.3f + 0.7f * level.getSkyDarken(pt);
        float[] fog = RenderSystem.getShaderFogColor();
        fogR = fog[0]; fogG = fog[1]; fogB = fog[2];
        fogStart = RenderSystem.getShaderFogStart();
        fogEnd = Math.max(fogStart + 1f, RenderSystem.getShaderFogEnd());

        Matrix4f m = event.getPoseStack() != null ? event.getPoseStack().last().pose() : new Matrix4f();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        alpha = 1f;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean translucent = false;
        for (MushroomCloud.Cloud c : MushroomCloud.CLOUDS) {
            float age = c.age + pt - MushroomCloud.START_DELAY;
            if (age <= 0) continue;
            float t = age / 20f;
            if (MushroomCloud.bodyAlpha(c, age) >= 0.999f) mushroom(b, m, c, t, 0f);
            translucent = true;
        }
        MeshData mesh = b.build();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        if (translucent) {
            b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (MushroomCloud.Cloud c : MushroomCloud.CLOUDS) {
                float age = c.age + pt - MushroomCloud.START_DELAY;
                if (age <= 0) continue;
                float t = age / 20f, a = MushroomCloud.bodyAlpha(c, age);
                soft = false;
                if (a < 0.999f && a > 0.01f) {
                    alpha = a * a;
                    mushroom(b, m, c, t, 0f);
                }
                soft = true;
                if (a > 0.01f)
                    for (float[] sh : SHELLS) {
                        alpha = sh[1] * a * a;
                        mushroom(b, m, c, t, sh[0]);
                    }
                double rk = MushroomCloud.rollerKeep(t);
                if (t < MushroomCloud.ROLLER_TIME) {
                    alpha = ROLLER_ALPHA * (float) Math.min(1.0, rk / 0.35);
                    roller(b, m, level, c, t);
                }
                for (int i = 0; i < MushroomCloud.RINGS.length; i++) {
                    double[] rg = MushroomCloud.ring(c, i, t);
                    if (rg == null) continue;
                    alpha = (float) rg[3] * 0.8f;
                    ring(b, m, c, rg, t, i);
                }
            }
            alpha = 1f;
            soft = false;
            mesh = b.build();
            if (mesh != null) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.depthMask(false);
                BufferUploader.drawWithShader(mesh);
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
            }
        }

        NukeFireball.render(m, level, camX, camY, camZ, pt);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();

        float inside = 0f;
        for (MushroomCloud.Cloud c : MushroomCloud.CLOUDS) {
            float age = c.age + pt - MushroomCloud.START_DELAY;
            if (age > 0 && age / 20f < MushroomCloud.ROLLER_TIME) inside = Math.max(inside, insideRoller(level, c, age / 20f));
        }
        if (inside > 0.01f) fillView(m, inside);
        RenderSystem.enableCull();
    }

    private static float insideRoller(ClientLevel level, MushroomCloud.Cloud c, float t) {
        float s = c.s;
        double front = MushroomCloud.rollerFront(s, t), h = MushroomCloud.rollerH(s, t);
        double rr0 = h * (0.25 + 0.75 * MushroomCloud.rollerKeep(t));
        if (rr0 < 0.5) return 0f;
        double mid = front - h;
        double dx = camX - c.x, dz = camZ - c.z;
        double radial = Math.sqrt(dx * dx + dz * dz) - mid;
        double cy = NukeDebris.groundY(level, camX, camZ, c.y) + rr0 * 0.55;
        double vert = (camY - cy) / 0.8;
        double d = Math.sqrt(radial * radial + vert * vert) / rr0;
        float a = (float) Mth.clamp((1.0 - d) / 0.3, 0.0, 1.0);
        return a * (float) Math.min(1.0, MushroomCloud.rollerKeep(t) / 0.35);
    }

    private static void fillView(Matrix4f m, float a) {
        float sh = light * 0.75f;
        float r = SURGE[0] * sh, g = SURGE[1] * sh, bl = SURGE[2] * sh;
        float k = 0.95f * a;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float e = 0.3f;
        float[][] v = {{-e, -e, -e}, {e, -e, -e}, {e, e, -e}, {-e, e, -e}, {-e, -e, e}, {e, -e, e}, {e, e, e}, {-e, e, e}};
        int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
        for (int[] f : faces)
            for (int i : f) b.addVertex(m, v[i][0], v[i][1], v[i][2]).setColor(r, g, bl, k);
        MeshData mesh = b.build();
        if (mesh == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void mushroom(BufferBuilder b, Matrix4f m, MushroomCloud.Cloud c, float t, float grow) {
        float s = c.s;
        float headH = MushroomCloud.headH(s, t), capR = MushroomCloud.capR(s, t), stemR = MushroomCloud.stemR(s, t);
        float fire = MushroomCloud.fire(t);
        double major = capR * 0.72, minor = capR * 0.31 * (1 + grow);
        double roll = t * 0.35;

        int nu = 72, nv = 24;
        begin(nu, nv);
        for (int ii = 0; ii <= nu; ii++)
            for (int jj = 0; jj <= nv; jj++) {
                    double phi = ii * Math.PI * 2 / nu, th = jj * Math.PI * 2 / nv;
                    double nx = Math.cos(th) * Math.cos(phi), ny = Math.sin(th), nz = Math.cos(th) * Math.sin(phi);
                    double tr = th - roll;
                    double lump = 1.0 + 0.28 * (noise(Math.cos(phi) * 3 + Math.cos(tr) * 0.9, Math.sin(tr) * 0.9, Math.sin(phi) * 3) - 0.5);
                    double rr = minor * lump;
                    double px = c.x + Math.cos(phi) * (major + Math.cos(th) * rr);
                    double py = c.y + headH + Math.sin(th) * rr * 0.85;
                    double pz = c.z + Math.sin(phi) * (major + Math.cos(th) * rr);
                    float g = (float) (fire * (0.35 + 0.65 * Math.max(0, -nx * 0.5 - ny * 0.5 + 0.3)));
                    point(ii, jj, px, py, pz, nx, ny, nz, lump, SMOKE, g);
                }
        quads(b, m, nu, nv);
        int dr = 8, da = 40;
        begin(da, dr);
        for (int ii = 0; ii <= dr; ii++)
            for (int jj = 0; jj <= da; jj++) {
                    double f = ii / (double) dr, a = jj * Math.PI * 2 / da;
                    double rad = major * f * 1.02;
                    double lump = 1.0 + 0.2 * (noise(Math.cos(a) * 2 * f, roll * 0.5, Math.sin(a) * 2 * f) - 0.5);
                    double py = c.y + headH + minor * (0.75 * Math.sqrt(Math.max(0, 1 - f * f)) * lump + 0.25);
                    point(jj, ii, c.x + Math.cos(a) * rad, py, c.z + Math.sin(a) * rad, 0.0, 1.0, 0.0, lump, SMOKE, fire * 0.25f);
                }
        quads(b, m, da, dr);
        double base = MushroomCloud.stemBase(s), top = headH - minor * 0.3;
        if (top <= base) return;
        int sa = 28, sh = 14;
        begin(sa, sh);
        for (int ii = 0; ii <= sa; ii++)
            for (int jj = 0; jj <= sh; jj++) {
                    double a = ii * Math.PI * 2 / sa, f = jj / (double) sh;
                    double h = base + f * (top - base);
                    double lump = 1.0 + 0.3 * (noise(Math.cos(a) * 2, h / (8 * s) - t * 0.8, Math.sin(a) * 2) - 0.5);
                    double rad = stemR * (1.25 - 0.55 * f) * lump * (1 + grow * 1.5);
                    double wob = Math.sin(h * 0.05 + t * 0.3) * stemR * 0.25;
                    float g = fire * 0.8f * (float) (1 - f * 0.6);
                    point(ii, jj, c.x + Math.cos(a) * rad + wob, c.y + h, c.z + Math.sin(a) * rad, Math.cos(a), 0.0, Math.sin(a), lump, SMOKE, g);
                }
        quads(b, m, sa, sh);
    }

    private static void roller(BufferBuilder b, Matrix4f m, ClientLevel level, MushroomCloud.Cloud c, float t) {
        float s = c.s;
        double front = MushroomCloud.rollerFront(s, t);
        double h = MushroomCloud.rollerH(s, t);
        double rr0 = h * (0.25 + 0.75 * MushroomCloud.rollerKeep(t));
        if (rr0 < 0.5) return;
        double mid = front - h;
        double roll = t * 1.6;
        int nu = Math.min(160, 48 + (int) (front / 3)), nv = 14;
        double[] ground = new double[nu + 1];
        for (int i = 0; i <= nu; i++) {
            double phi = i * Math.PI * 2 / nu;
            ground[i] = NukeDebris.groundY(level, c.x + Math.cos(phi) * mid, c.z + Math.sin(phi) * mid, c.y);
        }
        begin(nu, nv);
        for (int ii = 0; ii <= nu; ii++)
            for (int jj = 0; jj <= nv; jj++) {
                    double phi = ii * Math.PI * 2 / nu, th = jj * Math.PI * 2 / nv;
                    double tr = th + roll;
                    double lump = 1.0 + 0.32 * (noise(Math.cos(phi) * front / 18 + Math.cos(tr), Math.sin(tr), Math.sin(phi) * front / 18) - 0.5);
                    double rr = rr0 * lump;
                    double rad = mid + Math.cos(th) * rr;
                    double py = ground[ii] + rr0 * 0.55 + Math.sin(th) * rr * 0.8;
                    point(ii, jj, c.x + Math.cos(phi) * rad, py, c.z + Math.sin(phi) * rad, Math.cos(th) * Math.cos(phi), Math.sin(th), Math.cos(th) * Math.sin(phi), lump, SURGE, 0f);
                }
        quads(b, m, nu, nv);
    }

    private static void ring(BufferBuilder b, Matrix4f m, MushroomCloud.Cloud c, double[] rg, float t, int seed) {
        double major = rg[0], minor = rg[2] * 0.5, y = c.y + rg[1];
        double roll = t * 0.6;
        int nu = Math.min(120, 40 + (int) (major / 3)), nv = 10;
        begin(nu, nv);
        for (int ii = 0; ii <= nu; ii++)
            for (int jj = 0; jj <= nv; jj++) {
                    double phi = ii * Math.PI * 2 / nu, th = jj * Math.PI * 2 / nv;
                    double tr = th + roll;
                    double lump = 1.0 + 0.4 * (noise(Math.cos(phi) * 4 + Math.cos(tr) + seed * 31, Math.sin(tr), Math.sin(phi) * 4) - 0.5);
                    double rr = minor * lump;
                    double rad = major + Math.cos(th) * rr * 1.4;
                    double py = y + Math.sin(th) * rr * 0.6;
                    point(ii, jj, c.x + Math.cos(phi) * rad, py, c.z + Math.sin(phi) * rad, Math.cos(th) * Math.cos(phi), Math.sin(th), Math.cos(th) * Math.sin(phi), lump, RING, 0f);
                }
        quads(b, m, nu, nv);
    }

    private static float[] grid = new float[0];
    private static int gridW;

    static void begin(int nu, int nv) {
        gridW = nu + 1;
        if (grid.length < gridW * (nv + 1) * 7) grid = new float[gridW * (nv + 1) * 7];
    }

    static void put(int ii, int jj, float x, float y, float z, float r, float g, float bl, float a) {
        int o = (jj * gridW + ii) * 7;
        grid[o] = x; grid[o + 1] = y; grid[o + 2] = z; grid[o + 3] = r; grid[o + 4] = g; grid[o + 5] = bl; grid[o + 6] = a;
    }

    static void quads(BufferBuilder b, Matrix4f m, int nu, int nv) {
        for (int i = 0; i < nu; i++)
            for (int j = 0; j < nv; j++)
                for (int k = 0; k < 4; k++) {
                    int o = ((j + (k >= 2 ? 1 : 0)) * gridW + i + (k == 1 || k == 2 ? 1 : 0)) * 7;
                    b.addVertex(m, grid[o], grid[o + 1], grid[o + 2]).setColor(grid[o + 3], grid[o + 4], grid[o + 5], grid[o + 6]);
                }
    }

    private static void point(int ii, int jj, double x, double y, double z, double nx, double up, double nz, double lump,
            float[] col, float glow) {
        float shade = (float) ((0.55 + 0.45 * Math.max(0, up)) * (0.55 + 0.9 * (lump - 0.86))) * light;
        float r = col[0] * shade, g = col[1] * shade, bl = col[2] * shade;
        glow = Mth.clamp(glow, 0f, 1f);
        r += (GLOW[0] - r) * glow; g += (GLOW[1] - g) * glow * 0.8f; bl += (GLOW[2] - bl) * glow * 0.6f;
        float dx = (float) (x - camX), dy = (float) (y - camY), dz = (float) (z - camZ);
        float dist = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float f = Mth.clamp((dist - fogStart) / (fogEnd - fogStart), 0f, 1f) * 0.55f;
        r += (fogR - r) * f; g += (fogG - g) * f; bl += (fogB - bl) * f;
        float a = alpha;
        if (soft) a *= Math.min(1f, 1.7f * (float) Math.abs(nx * dx + up * dy + nz * dz) / Math.max(1e-3f, dist));
        put(ii, jj, dx, dy, dz, Mth.clamp(r, 0f, 1f), Mth.clamp(g, 0f, 1f), Mth.clamp(bl, 0f, 1f), a);
    }

    private static double noise(double x, double y, double z) {
        return 0.65 * value(x, y, z) + 0.35 * value(x * 2.1 + 17, y * 2.1 - 5, z * 2.1 + 9);
    }

    private static double value(double x, double y, double z) {
        int ix = Mth.floor(x), iy = Mth.floor(y), iz = Mth.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        double c000 = hash(ix, iy, iz), c100 = hash(ix + 1, iy, iz), c010 = hash(ix, iy + 1, iz), c110 = hash(ix + 1, iy + 1, iz);
        double c001 = hash(ix, iy, iz + 1), c101 = hash(ix + 1, iy, iz + 1), c011 = hash(ix, iy + 1, iz + 1), c111 = hash(ix + 1, iy + 1, iz + 1);
        double x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx;
        double x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
        double y0 = x00 + (x10 - x00) * fy, y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }
}
