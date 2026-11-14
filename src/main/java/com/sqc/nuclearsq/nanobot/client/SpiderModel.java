package com.sqc.nuclearsq.nanobot.client;

import com.sqc.nuclearsq.NuclearConfig;

import net.minecraft.util.Mth;

public final class SpiderModel {
    private SpiderModel() {}

    public static final int PARTS = 6 + 16 + 6 + 2 + 2 + 8;
    public static float scale() {
        return NuclearConfig.value(NuclearConfig.NANOBOT_BOT_SCALE).floatValue();
    }

    public static float workReach() {
        return 0.22f * scale();
    }

    public static final float STRIDE = 0.06f;
    public static final float DUTY_SLOW = 0.8f, DUTY_FAST = 0.48f;

    public enum Mat { METAL, DARK, GLOW, HAIR, SHINE }

    public static Mat matOf(int index) {
        if (index >= 32) return Mat.GLOW;
        if (index == 2 || index == 4 || index == 5) return Mat.GLOW;
        if (index >= 30) return Mat.DARK;
        if (index >= 28) return Mat.SHINE;
        if (index >= 22) return Mat.HAIR;
        if (index >= 6 && (index - 6) % 2 == 1) return Mat.DARK;
        return Mat.METAL;
    }

    public static BotUv.Kind kindOf(int index) {
        if (index >= 32) return BotUv.Kind.KNEE;
        if (index >= 30) return BotUv.Kind.CHELI;
        if (index == 28) return BotUv.Kind.SHINE_A;
        if (index == 29) return BotUv.Kind.SHINE_B;
        if (index >= 26) return BotUv.Kind.HAIR_H;
        if (index >= 22) return BotUv.Kind.HAIR_A;
        if (index >= 6) {
            int leg = (index - 6) / 2;
            if ((index - 6) % 2 == 1) return BotUv.Kind.TIBIA;
            return leg % 4 == 0 ? BotUv.Kind.FEMUR_WARN : BotUv.Kind.FEMUR;
        }
        return switch (index) {
            case 0 -> BotUv.Kind.BODY;
            case 1 -> BotUv.Kind.ABD;
            case 2 -> BotUv.Kind.LED;
            case 3 -> BotUv.Kind.HEAD;
            default -> BotUv.Kind.EYE;
        };
    }

    public interface PartConsumer {
        void part(int index, Mat mat, float cx, float cy, float cz, float hx, float hy, float hz, float yaw, float pitch);
    }

    public static final int P_U = 0, P_BETA = 1, P_META = 2, P_AMP = 3, P_TAP = 4, P_GROOM = 5, P_CLIMB = 6, P_CLOCK = 7, P_WORK = 8, P_SIZE = 9;

    private static final float[] HIP_Z = {0.045f, 0.015f, -0.015f, -0.045f};
    private static final float[] FOOT_DZ = {0.075f, 0.03f, -0.03f, -0.075f};
    private static final float L1 = 0.075f, L2 = 0.1f, REACH = 0.13f, HIP_X = 0.035f;
    public static final float FOOT_Y = -0.085f;

    public static float duty(float speedNorm) {
        return Mth.lerp(Mth.clamp(speedNorm, 0f, 1f), DUTY_SLOW, DUTY_FAST);
    }

    public static void parts(float[] pose, int bot, PartConsumer raw) {
        float u0 = pose[P_U] + bot * 0.7f, beta = pose[P_BETA], meta = pose[P_META], amp = pose[P_AMP];
        float tap = pose[P_TAP], groom = pose[P_GROOM], climb = pose[P_CLIMB], clock = pose[P_CLOCK], work = pose[P_WORK];
        final float S = scale();
        PartConsumer out = (i, m, cx, cy, cz, hx, hy, hz, yaw, pitch) -> raw.part(i, m, cx * S, cy * S, cz * S, hx * S, hy * S, hz * S, yaw, pitch);
        float bob = 0.006f * Mth.sin(u0 * 2f) * amp - 0.004f * Math.max(tap, groom) + 0.004f * work * Mth.sin(clock * 3.1f);
        float sway = 0.006f * Mth.sin(u0) * amp + 0.003f * Mth.sin(clock * 0.9f + bot) * Math.max(tap, groom);
        out.part(0, Mat.METAL, 0, bob, 0.01f, 0.04f, 0.025f, 0.045f, 0, 0);
        out.part(1, Mat.METAL, sway, bob + 0.01f, -0.085f, 0.05f, 0.035f, 0.06f, 0, 0);
        out.part(2, Mat.GLOW, sway, bob + 0.0455f, -0.085f, 0.012f, 0.004f, 0.045f, 0, 0);
        out.part(3, Mat.METAL, 0, bob + 0.002f, 0.075f, 0.028f, 0.022f, 0.025f, 0, 0);
        out.part(4, Mat.GLOW, -0.014f, bob + 0.01f, 0.1f, 0.008f, 0.008f, 0.004f, 0, 0);
        out.part(5, Mat.GLOW, 0.014f, bob + 0.01f, 0.1f, 0.008f, 0.008f, 0.004f, 0, 0);
        int n = 6;
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 4; k++) {
                int group = (k + (side > 0 ? 1 : 0)) % 2;
                float w = u0 / Mth.TWO_PI + group * 0.5f + meta * 0.07f * k;
                w -= Mth.floor(w);
                float stride = STRIDE * amp, lift = 0f, dz;
                if (w < beta) {
                    dz = stride - 2f * stride * (w / beta);
                } else {
                    float q = (w - beta) / (1f - beta);
                    dz = -stride + 2f * stride * q;
                    lift = Math.min(0.035f, 0.05f * amp) * Mth.sin(q * Mth.PI);
                }
                float hx = side * HIP_X, hy = bob - 0.005f, hz = HIP_Z[k];
                float fx = side * REACH, fy = FOOT_Y + lift, fz = HIP_Z[k] + FOOT_DZ[k] + dz;
                if (k == 0) {
                    float probe = Mth.sin(clock * 1.1f + (side > 0 ? Mth.PI : 0f));
                    fy = FOOT_Y + 0.02f * amp + 0.012f * amp * probe;
                    fz = HIP_Z[0] + FOOT_DZ[0] + 0.02f * amp + 0.025f * amp * probe;
                    fy += tap * (0.035f + 0.04f * Math.max(0f, probe));
                    fz += tap * (0.03f + 0.03f * probe);
                    float wipe = Mth.sin(clock * 1.5f + side);
                    fy += groom * (0.085f + 0.015f * wipe);
                    fx *= 1f - 0.45f * groom;
                    fz += groom * (0.03f + 0.03f * wipe);
                    float hack = Mth.sin(clock * 2.2f + (side > 0 ? Mth.PI : 0f));
                    fy += work * (0.06f + 0.05f * Math.max(0f, hack));
                    fz += work * (0.07f + 0.06f * hack);
                    fx *= 1f - 0.3f * work;
                }
                if (climb > 0f) {
                    if (k <= 1) {
                        fy += 0.03f * climb;
                        fz += 0.03f * climb;
                    }
                    fz += 0.008f * climb * Mth.sin(clock * 2.2f + k + side);
                    fy += 0.006f * climb * Mth.sin(clock * 1.7f + k * 1.3f);
                }
                float dx = fx - hx, dzz = fz - hz, dh = fy - hy;
                float d = Mth.sqrt(dx * dx + dzz * dzz);
                float dist = Math.min(Mth.sqrt(d * d + dh * dh), L1 + L2 - 0.002f);
                float cosA = Mth.clamp((L1 * L1 + dist * dist - L2 * L2) / (2f * L1 * dist), -1f, 1f);
                float alpha = (float) Math.acos(cosA);
                float beta2 = (float) Math.atan2(dh, d);
                float ux = d > 1e-5f ? dx / d : side, uz = d > 1e-5f ? dzz / d : 0f;
                float kx = hx + ux * L1 * Mth.cos(beta2 + alpha), kz = hz + uz * L1 * Mth.cos(beta2 + alpha), ky = hy + L1 * Mth.sin(beta2 + alpha);
                segment(out, n++, Mat.METAL, hx, hy, hz, kx, ky, kz, 0.008f);
                segment(out, n++, Mat.DARK, kx, ky, kz, fx, fy, fz, 0.006f);
                out.part(32 + (side > 0 ? 4 : 0) + k, Mat.GLOW, kx, ky, kz, 0.0105f, 0.0105f, 0.0105f, 0, 0);
            }
        }
        float flutter = 0.12f * Mth.sin(clock * 0.8f + bot);
        for (int h = 0; h < 4; h++) {
            float sx = (h % 2 == 0 ? -1 : 1) * 0.034f, hz = -0.06f - (h / 2) * 0.05f;
            out.part(n++, Mat.HAIR, sway + sx, bob + 0.052f, hz, 0.0025f, 0.011f, 0.0025f, 0, (h % 2 == 0 ? 0.4f : -0.4f) + flutter);
        }
        out.part(n++, Mat.HAIR, -0.016f, bob + 0.03f, 0.09f, 0.002f, 0.009f, 0.002f, 0, 0.5f - flutter);
        out.part(n++, Mat.HAIR, 0.016f, bob + 0.03f, 0.09f, 0.002f, 0.009f, 0.002f, 0, 0.5f - flutter);
        out.part(n++, Mat.SHINE, 0, bob + 0.0275f, 0.01f, 0.011f, 0.0015f, 0.024f, 0, 0);
        out.part(n++, Mat.SHINE, 0, bob + 0.0245f, 0.075f, 0.007f, 0.0015f, 0.012f, 0, 0);
        float fang = 0.012f * work * Mth.sin(clock * 2.4f);
        out.part(n++, Mat.DARK, -0.007f, bob - 0.004f, 0.108f + fang, 0.0035f, 0.0035f, 0.012f, 0, 0.15f);
        out.part(n++, Mat.DARK, 0.007f, bob - 0.004f, 0.108f - fang, 0.0035f, 0.0035f, 0.012f, 0, 0.15f);
    }

    static void segment(PartConsumer out, int index, Mat m, float ax, float ay, float az, float bx, float by, float bz, float w) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float h = Mth.sqrt(dx * dx + dz * dz);
        float len = Mth.sqrt(h * h + dy * dy);
        out.part(index, m, (ax + bx) / 2f, (ay + by) / 2f, (az + bz) / 2f, w, w, len / 2f + 0.003f, (float) Math.atan2(dx, dz), -(float) Math.atan2(dy, h));
    }
}
