package com.sqc.nuclearsq.explosion.client;

import com.sqc.nuclearsq.registry.ModParticles;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class MushroomCloud {
    private MushroomCloud() {}

    static final int START_DELAY = ExplosionFlash.NEAR_HOLD;
    private static final double CRATER_BASE = -48.0;
    private static final double RIM = 50.0;
    private static final float DUST_SHARE = 0.85f;
    private static final float DUST_DENSITY = 0.55f;
    private static final float BUDGET = 7500f;
    private static double vanillaLive = 0;
    private static float throttle = 1f;

    private static ParticleOptions dust() { return ModParticles.NUKE_DUST.get(); }
    private static final SoundEvent BEEP = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "nuke_beep"));
    private static final int DURATION = 620;
    static final int HOLD = 100;
    static final int FADE = 220;

    static float bodyAlpha(Cloud c, float age) {
        float f = (age - c.duration - HOLD) / FADE;
        return Math.max(0f, Math.min(1f, 1f - f));
    }

    static final float[][] RINGS = {
            {0f, 4.5f, 0.42f, 0.9f, 2.6f, 3.0f},
            {4f, 8.0f, 0.58f, 1.1f, 6.5f, 7.5f},
    };

    static double[] ring(Cloud c, int i, float t) {
        float[] rg = RINGS[i];
        float tr = (t - rg[0]) / rg[1];
        if (tr < 0f || tr > 1f) return null;
        float s = c.s, capR = capR(s, rg[0] + 1.5f), headH = headH(s, t);
        double rad = capR * (rg[3] + (rg[4] - rg[3]) * ease(tr));
        double thick = rg[5] * s * (0.6 + 0.8 * tr);
        float alpha = Math.min(1f, tr * 8f) * (1f - tr * tr);
        return new double[]{rad, headH * rg[2] + thick * 0.3 * tr, thick, alpha};
    }

    static final class Cloud {
        final ResourceKey<Level> dim;
        final double x, y, z;
        final float s, n;
        final boolean heavy;
        final int duration;
        int age;

        Cloud(ResourceKey<Level> dim, double x, double y, double z, float scale, boolean heavy) {
            this.dim = dim;
            this.x = x; this.y = y; this.z = z;
            this.s = Math.max(0.2f, scale);
            this.n = Math.min(6f, s * s);
            this.heavy = heavy;
            this.duration = Math.round(DURATION * Math.min(1.5f, Math.max(1f, s * 0.75f)));
        }

        int count(int base) {
            return Math.max(1, Math.round(base * n));
        }
    }

    static final List<Cloud> CLOUDS = new ArrayList<>();
    private static ClientLevel lastLevel;

    static float headH(float s, float t) { return (4f + 92f * ease(t / 26f)) * s; }
    static float capR(float s, float t) { return (7f + 32f * ease(t / 20f)) * s; }
    static float stemR(float s, float t) { return (4f + 3f * ease(t / 12f)) * s; }
    static float fire(float t) { return Math.max(0f, 1f - t / 14f); }
    static double stemBase(float s) { return CRATER_BASE * s; }
    static double rollerH(float s, float t) {
        return (9.0 + 13.0 * ease(t / 9f)) * s * (1.0 - 0.3 * Math.min(1f, Math.max(0f, (t - 18f) / 12f)));
    }
    static final float ROLLER_MOVE = 30f;
    static final float ROLLER_SHRINK = 24f;
    static final float ROLLER_TIME = 50f;

    static double rollerKeep(float t) {
        if (t < ROLLER_SHRINK) return 1.0;
        double f = Math.min(1.0, (t - ROLLER_SHRINK) / (ROLLER_TIME - ROLLER_SHRINK));
        return 1.0 - f * f * (3 - 2 * f);
    }

    public static void start(double x, double y, double z, float scale, boolean heavy) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        CLOUDS.add(new Cloud(level.dimension(), x, y, z, scale, heavy));
        NukeEffects.start(level.dimension(), x, y, z, scale);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(BEEP, 1.0f, heavy ? 0.8f : 1.0f));
    }

    private static float budget() {
        float n = 1f;
        for (Cloud c : CLOUDS) n = Math.max(n, c.n);
        return BUDGET * Math.min(2.2f, n);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { CLOUDS.clear(); vanillaLive = 0; NukeDustParticle.resetLive(); lastLevel = null; return; }
        if (level != lastLevel) {
            lastLevel = level;
            CLOUDS.removeIf(c -> !c.dim.equals(level.dimension()));
            vanillaLive = 0;
            NukeDustParticle.resetLive();
        }
        if (mc.isPaused() || CLOUDS.isEmpty()) return;
        vanillaLive *= 0.996;
        float total = (float) (vanillaLive + NukeDustParticle.live());
        float budget = budget();
        throttle = Math.max(0f, Math.min(1f, (budget - total) / (budget * 0.35f)));
        Iterator<Cloud> it = CLOUDS.iterator();
        while (it.hasNext()) {
            Cloud c = it.next();
            c.age++;
            if (c.age > START_DELAY + c.duration + HOLD + FADE) { it.remove(); continue; }
            if (c.age >= START_DELAY && c.age < START_DELAY + c.duration + HOLD) {
                spawn(level, c, (c.age - START_DELAY) / 20f);
                NukeDustParticle.nextGlow = 0f;
            }
        }
    }

    static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return 1f - (1f - x) * (1f - x);
    }

    private static void add(ClientLevel l, ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
        boolean fire = p == ParticleTypes.FLAME || p == ParticleTypes.LAVA || p == ParticleTypes.EXPLOSION_EMITTER;
        if (!fire && l.random.nextFloat() > throttle) return;
        if (p == dust() && l.random.nextFloat() > DUST_DENSITY) return;
        if (p == ParticleTypes.CAMPFIRE_COSY_SMOKE || p == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE) p = l.random.nextBoolean() ? dust() : ParticleTypes.LARGE_SMOKE;
        if (p == dust() && l.random.nextFloat() > DUST_SHARE) p = ParticleTypes.LARGE_SMOKE;
        if (!fire && p != dust()) vanillaLive++;
        l.addAlwaysVisibleParticle(p, true, x, y, z, vx, vy, vz);
    }

    private static void spawn(ClientLevel l, Cloud c, float t) {
        RandomSource r = l.random;
        float s = c.s;
        float headH = headH(s, t), capR = capR(s, t), stemR = stemR(s, t), fire = fire(t);
        NukeDustParticle.nextGlow = fire;

        if (t < 6f) {
            double rad = (4 + t * 6.5) * s;
            for (int k = 0, m = c.count(70); k < m; k++) {
                double dx = r.nextGaussian(), dy = r.nextGaussian(), dz = r.nextGaussian();
                double len = Math.max(0.2, Math.sqrt(dx * dx + dy * dy + dz * dz));
                double px = c.x + dx / len * rad * r.nextDouble(), py = c.y + 2 + t * 2.5 * s + dy / len * rad * r.nextDouble(), pz = c.z + dz / len * rad * r.nextDouble();
                add(l, r.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, px, py, pz, dx / len * 0.15, 0.1 + dy / len * 0.1, dz / len * 0.15);
            }
            if ((int) (t * 20) % 5 == 0)
                add(l, ParticleTypes.EXPLOSION_EMITTER, c.x + r.nextGaussian() * rad * 0.3, c.y + 2 + t * 2.5 * s, c.z + r.nextGaussian() * rad * 0.3, 0, 0, 0);
        }

        double base = stemBase(s);
        double span = Math.max(1.0, headH - base - capR * 0.3f);
        for (int k = 0, m = c.count(30); k < m; k++) {
            int kk = k % 30;
            double frac = r.nextDouble();
            double h = base + frac * span;
            double rad = stemR * Math.sqrt(r.nextDouble()) * (1.5 - 0.8 * frac);
            double a = r.nextDouble() * Math.PI * 2;
            double px = c.x + Math.cos(a) * rad, py = c.y + h, pz = c.z + Math.sin(a) * rad;
            double vy = 0.03 + r.nextDouble() * 0.07;
            ParticleOptions type = r.nextFloat() < fire * 0.5f ? ParticleTypes.FLAME
                    : (kk < 4 ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : (kk < 22 ? dust() : (kk < 26 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE)));
            add(l, type, px, py, pz, Math.cos(a) * 0.01, vy, Math.sin(a) * 0.01);
        }

        double major = capR * 0.72, minor = capR * 0.34;
        double speed = 0.03 + 0.03 * ease(1f - t / 24f);
        for (int k = 0, m = c.count(44); k < m; k++) {
            int kk = k % 44;
            double phi = r.nextDouble() * Math.PI * 2, th = r.nextDouble() * Math.PI * 2;
            double rr = major + minor * Math.cos(th);
            double px = c.x + Math.cos(phi) * rr, py = c.y + headH + minor * Math.sin(th) * 0.85, pz = c.z + Math.sin(phi) * rr;
            double vr = Math.sin(th) * speed, vy = -Math.cos(th) * speed;
            boolean hot = r.nextFloat() < fire * 0.6f && Math.cos(th) < 0.2;
            ParticleOptions type = hot ? ParticleTypes.FLAME
                    : (kk < 8 ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : (kk < 34 ? dust() : (kk < 40 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE)));
            add(l, type, px, py, pz, Math.cos(phi) * vr, vy, Math.sin(phi) * vr);
        }
        for (int k = 0, m = c.count(10); k < m; k++) {
            double a = r.nextDouble() * Math.PI * 2, rad = major * Math.sqrt(r.nextDouble());
            add(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x + Math.cos(a) * rad, c.y + headH + minor * 0.35 + r.nextDouble() * 3 * s, c.z + Math.sin(a) * rad, 0, 0.02, 0);
        }

        if (t < ROLLER_TIME) {
            NukeDustParticle.nextGlow = 0f;
            roller(l, c, t);
            NukeDustParticle.nextGlow = fire;
        }

        if (t < 40f) {
            for (int k = 0, m = c.count(14); k < m; k++) {
                double dx = r.nextGaussian(), dy = -Math.abs(r.nextGaussian()) - 0.3, dz = r.nextGaussian();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double rr = (46 + r.nextDouble() * 2) * s;
                double px = c.x + dx / len * rr, py = c.y + dy / len * rr, pz = c.z + dz / len * rr;
                add(l, k % 14 < 10 ? dust() : ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz,
                        -dx / len * 0.03, 0.12 + r.nextDouble() * 0.12, -dz / len * 0.03);
            }
        }

        rings(l, c, t);
        if (c.heavy) heavyExtras(l, c, t, headH, capR);
    }

    static double rollerFront(float s, float t) {
        return (RIM * 1.25 + 125.0 * ease(t / ROLLER_MOVE) + 0.8 * Math.max(0f, t - ROLLER_MOVE)) * s;
    }

    private static void roller(ClientLevel l, Cloud c, float t) {
        RandomSource r = l.random;
        float s = c.s;
        double front = rollerFront(s, t);
        double speed = rollerFront(s, t + 0.05f) - front;
        double h = rollerH(s, t);
        double fade = rollerKeep(t);
        h *= 0.25 + 0.75 * fade;
        for (int k = 0, m = (int) Math.round(c.count(16) * fade); k < m; k++) {
            int kk = k % 20;
            double a = r.nextDouble() * Math.PI * 2;
            double th = r.nextDouble() * Math.PI * 2;
            double rr = h * (0.85 + 0.35 * Math.sqrt(r.nextDouble()));
            double rad = front - h + Math.cos(th) * rr;
            double px = c.x + Math.cos(a) * rad, pz = c.z + Math.sin(a) * rad;
            double ground = NukeDebris.groundY(l, px, pz, c.y);
            double py = ground + h * 0.8 + Math.sin(th) * rr * 0.8;
            if (py < ground + 0.5) py = ground + 0.5 + r.nextDouble() * 2;
            double spin = 0.06 * s;
            double vr = speed * 0.8 - Math.sin(th) * spin, vy = Math.cos(th) * spin * 0.6;
            ParticleOptions type = kk < 15 ? ModParticles.NUKE_SURGE.get() : (kk < 18 ? dust() : ParticleTypes.LARGE_SMOKE);
            add(l, type, px, py, pz, Math.cos(a) * vr, vy, Math.sin(a) * vr);
        }
        if (t < 0.06f) {
            for (int k = 0, m = Math.min(Math.round(180 * c.n), NukeDebris.max() / 3); k < m; k++) {
                double a = r.nextDouble() * Math.PI * 2, rad = RIM * s * (0.5 + 0.6 * r.nextDouble());
                double out = 0.8 + r.nextDouble() * 1.8;
                NukeDebris.throwChunk(l, c.x + Math.cos(a) * rad, c.y + 2, c.z + Math.sin(a) * rad,
                        Math.cos(a) * out, 1.0 + r.nextDouble() * 1.8, Math.sin(a) * out, 0.6f + r.nextFloat() * 1.2f);
            }
        }
        if (t < 6f) {
            float f = 1f - t / 6f;
            for (int k = 0, m = (int) Math.round(15 * c.n * f); k < m; k++) {
                double a = r.nextDouble() * Math.PI * 2, rad = RIM * s * (0.9 + 0.55 * r.nextDouble());
                double px = c.x + Math.cos(a) * rad, pz = c.z + Math.sin(a) * rad;
                double out = 0.7 + r.nextDouble() * 1.6;
                NukeDebris.throwChunk(l, px, NukeDebris.groundY(l, px, pz, c.y) + 0.5, pz,
                        Math.cos(a) * out, 0.9 + r.nextDouble() * 1.7, Math.sin(a) * out, 0.4f + r.nextFloat() * 1.4f);
            }
        }
        if (t < 5f) {
            for (int k = 0, m = Math.round(14 * c.n); k < m; k++) {
                double a = r.nextDouble() * Math.PI * 2, rad = RIM * s * (1.1 + 0.65 * r.nextDouble());
                double out = 0.9 + r.nextDouble() * 1.8;
                NukeDebris.throwTop(l, c.x + Math.cos(a) * rad, c.z + Math.sin(a) * rad,
                        Math.cos(a) * out, 0.8 + r.nextDouble() * 1.6, Math.sin(a) * out, 0.8f + r.nextFloat() * 1.2f);
            }
        }
        if (t >= 0.06f && fade > 0) {
            for (int k = 0, m = (int) Math.round(8 * c.n * fade); k < m; k++) {
                double a = r.nextDouble() * Math.PI * 2, rad = front - r.nextDouble() * h;
                double px = c.x + Math.cos(a) * rad, pz = c.z + Math.sin(a) * rad;
                double out = speed * (1.0 + r.nextDouble()) + 0.2;
                NukeDebris.throwChunk(l, px, NukeDebris.groundY(l, px, pz, c.y) + 1, pz,
                        Math.cos(a) * out, 0.4 + r.nextDouble() * 0.8, Math.sin(a) * out, r.nextFloat());
            }
        }
    }

    private static void rings(ClientLevel l, Cloud c, float t) {
        RandomSource r = l.random;
        for (int i = 0; i < RINGS.length; i++) {
            double[] rg = ring(c, i, t);
            if (rg == null || rg[3] < 0.1) continue;
            for (int k = 0, m = (int) Math.round(c.count(i == 0 ? 14 : 30) * rg[3]); k < m; k++) {
                double a = r.nextDouble() * Math.PI * 2, rad = rg[0] + r.nextGaussian() * rg[2] * 0.6;
                add(l, k % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.POOF, c.x + Math.cos(a) * rad, c.y + rg[1] + r.nextGaussian() * rg[2] * 0.4,
                        c.z + Math.sin(a) * rad, Math.cos(a) * 0.25, 0.0, Math.sin(a) * 0.25);
            }
        }
    }

    private static void heavyExtras(ClientLevel l, Cloud c, float t, float headH, float capR) {
        RandomSource r = l.random;
        float s = c.s;
        if (t < 18f) {
            for (int k = 0, m = c.count(14); k < m; k++) {
                double a = r.nextDouble() * Math.PI * 2, rad = capR * Math.sqrt(r.nextDouble());
                add(l, k % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, c.x + Math.cos(a) * rad, c.y + headH, c.z + Math.sin(a) * rad,
                        (r.nextDouble() - 0.5) * 0.3, -0.2 - r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 0.3);
            }
        }
        if (t > 2f && t < 8f && (int) (t * 20) % 5 == 0) {
            double h = (t - 2f) / 6f * headH;
            add(l, ParticleTypes.EXPLOSION_EMITTER, c.x + r.nextGaussian() * 3 * s, c.y + h, c.z + r.nextGaussian() * 3 * s, 0, 0, 0);
        }
    }
}
