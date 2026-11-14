package com.sqc.nuclearsq.explosion.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sqc.nuclearsq.NuclearClientConfig;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class NukeEffects {
    private NukeEffects() {}

    private static final double SHOCK_SPEED = com.sqc.nuclearsq.explosion.NuclearExplosion.SHOCK_SPEED;
    private static final int SKY_IN0 = 20, SKY_IN1 = 80, SKY_HOLD = 300, SKY_OUT = 560;
    private static final int ASH_START = 400, ASH_FULL = 520, ASH_FADE = 1300, ASH_END = 1700;
    private static final BlockParticleOption[] ASH = {
            new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState()),
            new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BLACK_CONCRETE_POWDER.defaultBlockState()),
            new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState()),
    };

    private static final class Blast {
        final ResourceKey<Level> dim;
        final Vec3 at;
        final float s;
        int age;
        int hitAge = -1;
        float hitStrength;

        Blast(ResourceKey<Level> dim, Vec3 at, float scale) {
            this.dim = dim;
            this.at = at;
            this.s = Math.max(0.2f, scale);
        }
    }

    private static final List<Blast> BLASTS = new ArrayList<>();
    private static float red;

    public static void start(ResourceKey<Level> dim, double x, double y, double z, float scale) {
        BLASTS.add(new Blast(dim, new Vec3(x, y, z), scale));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { BLASTS.clear(); red = 0; return; }
        ResourceKey<Level> here = mc.level.dimension();
        BLASTS.removeIf(b -> !b.dim.equals(here));
        if (mc.isPaused()) return;
        Vec3 me = mc.player.position();
        float sky = 0f;
        for (int i = BLASTS.size() - 1; i >= 0; i--) {
            Blast b = BLASTS.get(i);
            b.age++;
            double d = me.distanceTo(b.at);
            if (b.hitAge < 0 && b.age >= d / SHOCK_SPEED) {
                b.hitAge = b.age;
                b.hitStrength = (float) Mth.clamp(1.0 - d / (500.0 * b.s), 0.0, 1.0);
                if (b.hitStrength > 0) {
                    float vol = 0.4f + 0.6f * b.hitStrength;
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_EXPLODE.value(), 0.45f, vol));
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, vol * 0.8f));
                }
            }
            if (b.hitAge >= 0 && b.age - b.hitAge < GUST_TICKS && b.hitStrength > 0) gust(mc, b, me);
            if (b.age > ASH_END && b.hitAge >= 0 && b.age - b.hitAge > 500) { BLASTS.remove(i); continue; }
            sky = Math.max(sky, skyLevel(b, d));
            ash(mc, b, d);
        }
        red = sky;
    }

    private static final int GUST_TICKS = 14;

    private static void gust(Minecraft mc, Blast b, Vec3 me) {
        Vec3 dir = new Vec3(me.x - b.at.x, 0, me.z - b.at.z).normalize();
        RandomSource r = mc.level.random;
        for (int k = 0, n = Math.round(36 * b.hitStrength); k < n; k++) {
            double back = 4 + r.nextDouble() * 16, side = (r.nextDouble() - 0.5) * 28, v = 1.2 + r.nextDouble() * 1.4;
            mc.level.addParticle(k % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.POOF,
                    me.x - dir.x * back - dir.z * side, me.y + r.nextDouble() * 6, me.z - dir.z * back + dir.x * side,
                    dir.x * v, 0.02, dir.z * v);
        }
    }

    private static void ash(Minecraft mc, Blast b, double d) {
        if (b.age < ASH_START || b.age > ASH_END) return;
        float t = b.age < ASH_FULL ? (b.age - ASH_START) / (float) (ASH_FULL - ASH_START)
                : (b.age < ASH_FADE ? 1f : 1f - (b.age - ASH_FADE) / (float) (ASH_END - ASH_FADE));
        float near = (float) Mth.clamp(1.0 - d / (500.0 * b.s), 0.0, 1.0);
        float rate = 28f * t * (0.25f + 0.75f * near);
        RandomSource r = mc.level.random;
        int n = (int) rate + (r.nextFloat() < rate - (int) rate ? 1 : 0);
        Vec3 p = mc.player.position();
        for (int k = 0; k < n; k++) {
            double x = p.x + (r.nextDouble() - 0.5) * 48, z = p.z + (r.nextDouble() - 0.5) * 48;
            double y = p.y + 6 + r.nextDouble() * 18;
            if (r.nextInt(3) == 0) mc.level.addParticle(ParticleTypes.ASH, x, y, z, 0, 0, 0);
            else mc.level.addParticle(ASH[r.nextInt(ASH.length)], x, y, z, 0, 0, 0);
        }
    }

    private static float skyLevel(Blast b, double d) {
        float t;
        if (b.age < SKY_IN0) t = 0f;
        else if (b.age < SKY_IN1) t = (b.age - SKY_IN0) / (float) (SKY_IN1 - SKY_IN0);
        else if (b.age < SKY_HOLD) t = 1f;
        else t = Math.max(0f, 1f - (b.age - SKY_HOLD) / (float) (SKY_OUT - SKY_HOLD));
        float near = (float) Mth.clamp(1.0 - (d - 250.0 * b.s) / (450.0 * b.s), 0.0, 1.0);
        return t * near;
    }

    private static float redLevel() {
        return NuclearClientConfig.RED_SKY.get() ? red : 0f;
    }

    @SubscribeEvent
    public static void onRenderSky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float a = redLevel();
        if (a <= 0.01f) return;
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = new Matrix4f();
        float r = 100f;
        float topA = 0.74f * a, midA = 0.66f * a;
        int[] top = {140, 18, 8}, mid = {235, 80, 25};
        quad(b, m, -r, r, -r, r, r, -r, r, r, r, -r, r, r, top, topA, top, topA);
        quad(b, m, -r, r, -r, -r, r, r, -r, -r, r, -r, -r, -r, top, topA, mid, midA);
        quad(b, m, r, r, -r, r, r, r, r, -r, r, r, -r, -r, top, topA, mid, midA);
        quad(b, m, -r, r, -r, r, r, -r, r, -r, -r, -r, -r, -r, top, topA, mid, midA);
        quad(b, m, -r, r, r, r, r, r, r, -r, r, -r, -r, r, top, topA, mid, midA);
        quad(b, m, -r, -r, -r, r, -r, -r, r, -r, r, -r, -r, r, mid, midA, mid, midA);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3, int[] c0, float a0, int[] c1, float a1) {
        int A0 = Math.round(a0 * 255), A1 = Math.round(a1 * 255);
        b.addVertex(m, x0, y0, z0).setColor(c0[0], c0[1], c0[2], A0);
        b.addVertex(m, x1, y1, z1).setColor(c0[0], c0[1], c0[2], A0);
        b.addVertex(m, x2, y2, z2).setColor(c1[0], c1[1], c1[2], A1);
        b.addVertex(m, x3, y3, z3).setColor(c1[0], c1[1], c1[2], A1);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float a = redLevel() * 0.85f;
        if (a <= 0.01f) return;
        event.setRed(Mth.lerp(a, event.getRed(), 0.78f));
        event.setGreen(Mth.lerp(a, event.getGreen(), 0.22f));
        event.setBlue(Mth.lerp(a, event.getBlue(), 0.08f));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        double cfg = NuclearClientConfig.SCREEN_SHAKE.get();
        if (cfg <= 0 || BLASTS.isEmpty() || !event.usedConfiguredFov()) return;
        double kick = 0;
        for (Blast b : BLASTS)
            if (b.hitAge >= 0) kick += b.hitStrength * Math.exp(-(b.age + event.getPartialTick() - b.hitAge) / 9.0);
        if (kick > 0.005) event.setFOV(event.getFOV() * (1.0 + 0.14 * Math.min(1.0, cfg) * Math.min(1.0, kick)));
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        double cfg = NuclearClientConfig.SCREEN_SHAKE.get();
        if (cfg <= 0 || BLASTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float time = BLASTS.get(0).age + (float) event.getPartialTick();
        float amp = 0f;
        for (Blast b : BLASTS) {
            float t = b.age + (float) event.getPartialTick();
            double d = mc.player.position().distanceTo(b.at);
            float rumble = (float) Mth.clamp(1.0 - d / (300.0 * b.s), 0.0, 1.0) * 0.6f * (float) Math.exp(-t / 220.0);
            float hit = 0f;
            if (b.hitAge >= 0) {
                float th = t - b.hitAge;
                hit = b.hitStrength * (4.0f * (float) Math.exp(-th / 32.0) + 1.0f * (float) Math.exp(-th / 170.0));
            }
            amp += rumble + hit;
        }
        amp *= (float) cfg * 1.4f;
        if (amp < 0.01f) return;
        float yaw = (float) (Math.sin(time * 1.9) * 0.6 + Math.sin(time * 4.3 + 1.0) * 0.4);
        float pitch = (float) (Math.sin(time * 2.3 + 2.0) * 0.6 + Math.sin(time * 5.1) * 0.4);
        float roll = (float) (Math.sin(time * 1.7 + 4.0) * 0.7 + Math.sin(time * 3.7 + 0.5) * 0.3);
        event.setYaw(event.getYaw() + yaw * amp);
        event.setPitch(event.getPitch() + pitch * amp * 0.8f);
        event.setRoll(event.getRoll() + roll * amp * 0.6f);
    }
}
