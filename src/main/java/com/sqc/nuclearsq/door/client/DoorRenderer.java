package com.sqc.nuclearsq.door.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.door.DoorBlockEntity;
import com.sqc.nuclearsq.door.DoorRegistry;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public class DoorRenderer implements BlockEntityRenderer<DoorBlockEntity> {
    private static final float P = 1 / 16f, GAP = 0.02f, E = P / 4;
    private static final ResourceLocation SLATS = tex("slats"), HAZARD = tex("hazard"), FRAME = tex("frame"),
            HULL = tex("hull"), VAULT = tex("vault"), WHEEL_RED = tex("wheel_red"),
            SECTIONS = tex("sections"), WINDOW = tex("window"), GRILLE = tex("grille"), FOLD = tex("fold"),
            LOCK_PLATE = tex("lock_plate"), LOCK_WEAR = tex("lock_wear"), LOCK_RIM = tex("lock_rim"), LOCK_FRAME = tex("lock_frame"),
            LOCK_WHEEL = tex("lock_wheel"), LOCK_GLASS = tex("lock_glass"), LOCK_LAMP = tex("lock_lamp");

    private PoseStack stack;
    private PoseStack.Pose pose;
    private MultiBufferSource buffers;
    private VertexConsumer vc;
    private DoorBlockEntity be;
    private final int[] uDir = new int[3], vDir = new int[3], nDir = new int[3];
    private final float[] base = new float[3];
    private int w, h;
    private boolean swap, glow;
    private int[] lights = new int[0];

    public DoorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/entity/door/" + name + ".png");
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DoorRegistry.CONTROLLER_BE.get(), DoorRenderer::new);
    }

    @Override
    public AABB getRenderBoundingBox(DoorBlockEntity be) {
        AABB own = new AABB(be.getBlockPos());
        return be.panels().isEmpty() ? own : own.minmax(AABB.encapsulatingFullBlocks(be.min(), be.max()).inflate(be.way()));
    }

    private static void unit(Direction.Axis a, int[] out) {
        out[0] = a == Direction.Axis.X ? 1 : 0;
        out[1] = a == Direction.Axis.Y ? 1 : 0;
        out[2] = a == Direction.Axis.Z ? 1 : 0;
    }

    @Override
    public void render(DoorBlockEntity door, float partial, PoseStack stack, MultiBufferSource source, int light, int overlay) {
        if (door.getLevel() == null || door.panels().isEmpty() || door.retired()) return;
        be = door;
        this.stack = stack;
        pose = stack.last();
        buffers = source;
        unit(door.wide(), uDir);
        unit(door.tall(), vDir);
        unit(door.axis(), nDir);
        BlockPos o = door.getBlockPos(), m = door.min();
        base[0] = m.getX() - o.getX();
        base[1] = m.getY() - o.getY();
        base[2] = m.getZ() - o.getZ();
        w = door.width();
        h = door.height();
        if (lights.length < w * h) lights = new int[w * h];
        java.util.Arrays.fill(lights, 0, w * h, -1);
        float f = Mth.clamp(door.fraction(partial), 0, 1);
        switch (door.kind()) {
            case HANGAR -> hangar(f);
            case AIRLOCK -> airlock(f);
            case VAULT -> vault(f);
            case SECTIONAL -> sectional(f);
            case GRILLE -> grille(f);
            case FOLDING -> folding(f);
        }
    }

    private void hangar(float f) {
        float top = h - 6 * P, bottom = f * (top - 3 * P);
        use(FRAME);
        box(0, 0, 4 * P, 2 * P, top, 12 * P, 0, 0);
        box(w - 2 * P, 0, 4 * P, w, top, 12 * P, 0, 0);
        box(0, top, 2 * P, w, h, 14 * P, 0, 0);
        use(SLATS);
        box(2 * P, bottom + 3 * P, 7 * P, w - 2 * P, top, 9 * P, 0, bottom);
        use(HAZARD);
        box(2 * P, bottom, 6 * P, w - 2 * P, bottom + 3 * P, 10 * P, 0, bottom);
    }

    private void airlock(float f) {
        float t = Mth.clamp(f / 0.25f, 0, 1), sl = Mth.clamp((f - 0.25f) / 0.75f, 0, 1), swing = sl * sl * (3 - 2 * sl);
        float u0 = 2 * P, u1 = w - 2 * P, top = h - 4 * P, mid = w / 2f, vt = top + P / 2;
        float rim = w == 1 ? 2 * P : 3 * P, a = u0 + P + rim, b = u1 - P - rim;
        boolean tall = h >= 2;
        float rw = P * Math.min(w == 1 ? 3.5f : w == 2 ? 5.5f : 6.5f, h == 1 ? 3.5f : h == 2 ? 4 : 6.5f);
        int g = Math.min(w == 1 ? 2 : w == 2 ? 3 : 4, h == 2 ? 3 : 4);
        float rg = g * P, cw = P * (h == 1 ? 7 : h == 2 ? 11 : 14), cp = P * (h == 2 ? 20 : 27);
        use(HAZARD);
        box(0, 0, 2 * P, w, P, 14 * P, 0, 0);
        use(LOCK_FRAME);
        box(0, P, 2 * P, 3 * P, top, 14 * P, 0, 0);
        box(w - 3 * P, P, 2 * P, w, top, 14 * P, w - 3 * P, 0);
        swap = true;
        box(0, top, 2 * P, w, h, 14 * P, 0, h);
        swap = false;
        box(w / 2f - 2 * P, h - 3.5f * P, P, w / 2f + 2 * P, h - 0.5f * P, 15 * P, w / 2f - 6 * P, 0);
        use(LOCK_LAMP);
        glow = true;
        box(w / 2f - P, h - 2.5f * P, P - 0.01f, w / 2f + P, h - 1.5f * P, 15 * P + 0.01f, 0, h - 2.5f * P + (f <= 0 ? 2 : f >= 1 ? 12 : 7) * P);
        glow = false;
        use(LOCK_FRAME);
        for (float y : tall ? new float[] {4 * P, top - 7 * P} : new float[] {2 * P, top - 5 * P}) box(P, y, P, 3 * P, y + 3 * P, 2 * P, 0, 0);
        turn((be.axis() == Direction.Axis.Z ? 90 : -90) * swing, vDir, 2 * P, 0, 2 * P);
        use(LOCK_WEAR);
        box(u0, P, 6 * P, u1, tall ? P + 1 : vt, 10 * P, u0, P + 1);
        if (tall) {
            use(LOCK_PLATE);
            box(u0, P + 1, 6 * P, u1, cp - rg, 10 * P, u0, 0);
            box(u0, cp + rg, 6 * P, u1, vt, 10 * P, u0, 0);
            for (int j = -g; j < g; j++) {
                float hw = P * Math.round(Math.sqrt(g * g - (j + 0.5f) * (j + 0.5f)));
                box(u0, cp + j * P, 6 * P, mid - hw, cp + (j + 1) * P, 10 * P, u0, 0);
                box(mid + hw, cp + j * P, 6 * P, u1, cp + (j + 1) * P, 10 * P, u0, 0);
            }
            use(LOCK_GLASS);
            box(mid - rg - P / 2, cp - rg - P / 2, 7.75f * P, mid + rg + P / 2, cp + rg + P / 2, 8.25f * P, mid - rg, cp + rg);
        }
        use(LOCK_RIM);
        box(u0 + P, P, 5 * P, a, vt, 11 * P, u0 + P, 0);
        box(b, P, 5 * P, u1 - P, vt, 11 * P, b, 0);
        for (int k = 1; k < 8 && w >= 4; k++) {
            for (int d = -1; d <= 1; d += 2) {
                float x = mid + d * k - P;
                if (x > a + 4 * P && x + 2 * P < b - 4 * P) box(x, P, 5 * P, x + 2 * P, top, 11 * P, x, 0);
            }
        }
        swap = true;
        box(a, top - rim, 5 * P, b, vt, 11 * P, 0, top);
        if (!tall) box(a, P, 5 * P, b, P + rim, 11 * P, 0, P + rim);
        for (float y = 36 * P; y + 2 * P <= top - rim - 2 * P; y++) box(a, y, 5 * P, b, y + 2 * P, 11 * P, 0, y + 2 * P);
        swap = false;
        if (tall) {
            use(HAZARD);
            box(a, P, 5 * P, b, 5 * P, 11 * P, u0, 0);
        }
        use(FRAME);
        box(a, cw - P, 5 * P, b, cw + P, 11 * P, 0, 0);
        if (tall) ring(mid, cp, rg - P / 2, rg + 1.5f * P, 5 * P, 6 * P, 0);
        if (w >= 2 && tall) {
            float pull = 2.5f * P * t;
            for (float x : new float[] {a + P, b - 2 * P}) {
                for (float n : new float[] {4.5f * P, 10.5f * P}) {
                    box(x, cw + P, n, x + P, vt - pull, n + P, 0, 0);
                    box(x, P + pull, n, x + P, cw - P, n + P, 0, 0);
                }
                for (float v : new float[] {(cw + top) / 2, (cw + 5 * P) / 2 + P}) box(x - P / 2, v, 4 * P, x + 1.5f * P, v + P, 12 * P, 0, 0);
            }
        }
        float spin = 270 * t * t * (3 - 2 * t);
        box(mid - P, cw - P, 4 * P, mid + P, cw + P, 12 * P, 0, 0);
        use(LOCK_WHEEL);
        ring(mid, cw, rw - P, rw, 3 * P, 4 * P, spin);
        for (int i = 0; i < 2; i++) {
            turn(spin + 90 * i, nDir, mid, cw, 0);
            box(mid - rw + P / 2, cw - P / 2, 3.2f * P, mid + rw - P / 2, cw + P / 2, 3.8f * P, 0, 0);
            box(mid - rw + P / 2, cw - P / 2, 12.2f * P, mid + rw - P / 2, cw + P / 2, 12.8f * P, 0, 0);
            back();
        }
        use(LOCK_FRAME);
        turn(spin, nDir, mid, cw, 0);
        box(mid - 1.5f * P, cw - 1.5f * P, 2.6f * P, mid + 1.5f * P, cw + 1.5f * P, 4.2f * P, mid - 8 * P, 0);
        box(mid - 1.5f * P, cw - 1.5f * P, 11.8f * P, mid + 1.5f * P, cw + 1.5f * P, 13.4f * P, mid - 8 * P, 0);
        back();
        back();
    }

    private void ring(float cu, float cv, float ri, float ro, float n0, float n1, float spin) {
        float seg = ro * 0.19891237f + 0.001f;
        for (int i = 0; i < 16; i++) {
            turn(spin + 22.5f * i, nDir, cu, cv, 0);
            box(cu - seg, cv + ri, n0, cu + seg, cv + ro, n1, cu, cv);
            box(cu - seg, cv + ri, 1 - n1, cu + seg, cv + ro, 1 - n0, cu, cv);
            back();
        }
    }

    private void sectional(float f) {
        float top = h - 4 * P, bottom = f * (top - 2 * P), band = bottom + Mth.floor(top * 0.62f * 2) / 2f;
        use(FRAME);
        box(0, 0, 4 * P, 2 * P, top, 12 * P, 0, 0);
        box(w - 2 * P, 0, 4 * P, w, top, 12 * P, 0, 0);
        box(0, top, 3 * P, w, h, 13 * P, 0, 0);
        use(SECTIONS);
        box(2 * P, bottom + 2 * P, 7 * P, w - 2 * P, top, 9 * P, 0, bottom);
        if (h >= 2) {
            use(WINDOW);
            box(2 * P, band + P, 6.8f * P, w - 2 * P, Math.min(band + 7 * P, top), 9.2f * P, 0, band);
        }
        use(HAZARD);
        box(2 * P, bottom, 6 * P, w - 2 * P, bottom + 2 * P, 10 * P, 0, bottom);
    }

    private void grille(float f) {
        float top = h - 6 * P, bottom = f * (top - 3 * P);
        use(FRAME);
        box(0, 0, 4 * P, 2 * P, top, 12 * P, 0, 0);
        box(w - 2 * P, 0, 4 * P, w, top, 12 * P, 0, 0);
        box(0, top, 2 * P, w, h, 14 * P, 0, 0);
        use(GRILLE);
        box(2 * P, bottom + 3 * P, 7.5f * P, w - 2 * P, top, 8.5f * P, 0, bottom);
        use(HAZARD);
        box(2 * P, bottom, 6 * P, w - 2 * P, bottom + 3 * P, 10 * P, 0, bottom);
    }

    private void folding(float f) {
        float top = h - 3 * P, half = (w - 4 * P) / 2;
        int n = Math.max(2, Math.round(half * 2) / 2 * 2);
        float pw = half / n, angle = f * 80, c = Mth.cos(angle * Mth.DEG_TO_RAD);
        use(FRAME);
        box(0, 0, 3 * P, 2 * P, top, 13 * P, 0, 0);
        box(w - 2 * P, 0, 3 * P, w, top, 13 * P, 0, 0);
        box(0, top, 3 * P, w, h, 13 * P, 0, 0);
        use(FOLD);
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < n; i++) {
                boolean even = i % 2 == 0;
                float x = 2 * P + (even ? i : i + 1) * pw * c, pivot = side == 0 ? x : w - x;
                turn((even ? angle : -angle) * (side == 0 ? 1 : -1), vDir, pivot, 0, 8 * P);
                span(pivot, (side == 0) == even ? 1 : -1, 0, pw, 0, top, 7 * P, 9 * P);
                back();
            }
        }
    }

    private void vault(float f) {
        float t = 2 * P;
        use(FRAME);
        box(0, 0, 3 * P, t, h - t, 13 * P, 0, 0);
        box(w - t, 0, 3 * P, w, h - t, 13 * P, 0, 0);
        box(t, 0, 3 * P, w - t, P, 13 * P, 0, 0);
        use(HAZARD);
        box(0, h - t, 2.5f * P, w, h, 13.5f * P, 0, 0);
        if (w >= 4) {
            leaf(f, t, w / 2f);
            leaf(f, w - t, w / 2f);
        } else {
            leaf(f, t, w - t);
        }
    }

    private void leaf(float f, float hinge, float edge) {
        float dir = Math.signum(edge - hinge), len = Math.abs(edge - hinge), top = h - 2 * P, cx = len / 2, cv = (P + top) / 2;
        float r = Math.min(1.2f, Math.min(len, h) * 0.3f), in = Mth.clamp((f - 0.15f) / 0.2f, 0, 1) * 3 * P;
        float swing = Mth.clamp((f - 0.35f) / 0.65f, 0, 1);
        for (float v : new float[] {P + (top - P) * 0.18f, P + (top - P) * 0.82f - 4 * P}) {
            use(FRAME);
            span(hinge, -dir, 0, 2 * P, v - P, v + 5 * P, 2 * P, 3 * P);
        }
        turn((be.axis() == Direction.Axis.Z ? 90 : -90) * dir * swing * swing * (3 - 2 * swing), vDir, hinge, 0, 4 * P);
        use(VAULT);
        span(hinge, dir, E, len - E, P + E, top - E, 5 * P, 11 * P);
        use(FRAME);
        span(hinge, dir, 0, len, P, 4 * P, 4 * P, 12 * P);
        span(hinge, dir, 0, len, top - 3 * P, top, 4 * P, 12 * P);
        span(hinge, dir, 0, 3 * P, 4 * P, top - 3 * P, 4 * P, 12 * P);
        span(hinge, dir, len - 3 * P, len, 4 * P, top - 3 * P, 4 * P, 12 * P);
        float cut = r * 0.3f;
        span(hinge, dir, cx - r, cx + r, cv - r + cut, cv + r - cut, 4 * P, 12 * P);
        span(hinge, dir, cx - r + cut, cx + r - cut, cv + r - cut, cv + r, 4 * P, 12 * P);
        span(hinge, dir, cx - r + cut, cx + r - cut, cv - r, cv - r + cut, 4 * P, 12 * P);
        if (h >= 2) {
            for (float v : new float[] {P + (top - P) * 0.18f, P + (top - P) * 0.82f - 4 * P}) {
                span(hinge, dir, E, Math.min(len * 0.45f, cx - 2 * P), v, v + 4 * P, 3.5f * P, 12.5f * P);
            }
        }
        use(HULL);
        span(hinge, dir, E + in, cx - r + E, cv - P, cv + P, 3.5f * P, 12.5f * P);
        span(hinge, dir, cx + r - E, len - E - in, cv - P, cv + P, 3.5f * P, 12.5f * P);
        span(hinge, dir, cx - P, cx + P, cv + r - E, top - E - in, 3.5f * P, 12.5f * P);
        span(hinge, dir, cx - P, cx + P, P + E + in, cv - r + E, 3.5f * P, 12.5f * P);
        if (h >= 2) {
            use(HAZARD);
            span(hinge, dir, 3 * P, len - 3 * P, 4 * P, 7 * P, 4.5f * P, 11.5f * P);
        }
        turn(360 * Mth.clamp(f / 0.3f, 0, 1), nDir, hinge + dir * cx, cv, 0);
        use(WHEEL_RED);
        decal(hinge + dir * cx, cv, r * 0.9f, 4 * P);
        back();
        back();
    }

    private void span(float origin, float dir, float x0, float x1, float v0, float v1, float n0, float n1) {
        box(Math.min(origin + dir * x0, origin + dir * x1), v0, n0, Math.max(origin + dir * x0, origin + dir * x1), v1, n1, 0, 0);
    }

    private void turn(float degrees, int[] axis, float u, float v, float n) {
        stack.pushPose();
        stack.rotateAround(new org.joml.Quaternionf().rotationAxis(degrees * Mth.DEG_TO_RAD, axis[0], axis[1], axis[2]),
                base[0] + u * uDir[0] + v * vDir[0] + n * nDir[0], base[1] + u * uDir[1] + v * vDir[1] + n * nDir[1],
                base[2] + u * uDir[2] + v * vDir[2] + n * nDir[2]);
        pose = stack.last();
    }

    private void back() {
        stack.popPose();
        pose = stack.last();
    }

    private void use(ResourceLocation texture) {
        vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
    }

    private int light(float u, float v) {
        int cu = Mth.clamp(Mth.floor(u), 0, w - 1), cv = Mth.clamp(Mth.floor(v), 0, h - 1), i = cu + cv * w;
        if (lights[i] < 0) {
            BlockPos m = be.min();
            lights[i] = LevelRenderer.getLightColor(be.getLevel(),
                    m.offset(cu * uDir[0] + cv * vDir[0], cu * uDir[1] + cv * vDir[1], cu * uDir[2] + cv * vDir[2]));
        }
        return lights[i];
    }

    private void vertex(float u, float v, float n, float tu, float tv, int[] normal, int sign) {
        vc.addVertex(pose, base[0] + u * uDir[0] + v * vDir[0] + n * nDir[0], base[1] + u * uDir[1] + v * vDir[1] + n * nDir[1],
                        base[2] + u * uDir[2] + v * vDir[2] + n * nDir[2])
                .setColor(255, 255, 255, 255).setUv(swap ? tv : tu, swap ? tu : tv).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(glow ? LightTexture.FULL_BRIGHT : light(u, v))
                .setNormal(pose, sign * normal[0], sign * normal[1], sign * normal[2]);
    }

    private void box(float u0, float v0, float n0, float u1, float v1, float n1, float au, float av) {
        if (u1 <= u0 || v1 <= v0) return;
        for (int k = 0; k < 2; k++) {
            float n = k == 0 ? n0 : n1;
            vertex(u0, v0, n, u0 - au, av - v0, nDir, k * 2 - 1);
            vertex(u1, v0, n, u1 - au, av - v0, nDir, k * 2 - 1);
            vertex(u1, v1, n, u1 - au, av - v1, nDir, k * 2 - 1);
            vertex(u0, v1, n, u0 - au, av - v1, nDir, k * 2 - 1);
            float u = k == 0 ? u0 : u1;
            vertex(u, v0, n0, n0, av - v0, uDir, k * 2 - 1);
            vertex(u, v0, n1, n1, av - v0, uDir, k * 2 - 1);
            vertex(u, v1, n1, n1, av - v1, uDir, k * 2 - 1);
            vertex(u, v1, n0, n0, av - v1, uDir, k * 2 - 1);
            float v = k == 0 ? v0 : v1;
            vertex(u0, v, n0, u0 - au, n0, vDir, k * 2 - 1);
            vertex(u1, v, n0, u1 - au, n0, vDir, k * 2 - 1);
            vertex(u1, v, n1, u1 - au, n1, vDir, k * 2 - 1);
            vertex(u0, v, n1, u0 - au, n1, vDir, k * 2 - 1);
        }
    }

    private void decal(float cu, float cv, float r, float n) {
        for (int k = 0; k < 2; k++) {
            float at = k == 0 ? n - GAP : 1 - n + GAP;
            vertex(cu - r, cv - r, at, 0, 1, nDir, k * 2 - 1);
            vertex(cu + r, cv - r, at, 1, 1, nDir, k * 2 - 1);
            vertex(cu + r, cv + r, at, 1, 0, nDir, k * 2 - 1);
            vertex(cu - r, cv + r, at, 0, 0, nDir, k * 2 - 1);
        }
    }
}
