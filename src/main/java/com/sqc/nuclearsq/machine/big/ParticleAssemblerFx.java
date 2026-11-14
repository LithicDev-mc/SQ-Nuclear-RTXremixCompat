package com.sqc.nuclearsq.machine.big;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

public final class ParticleAssemblerFx {
    public static final int CX = 2, CY = 1, CZ = 1;
    private static final float AX = 29f, ARM_TRAVEL = 20f, GRIP_DROP = 9f;
    private static final float ROD = 16f, TILT = (float) Math.toRadians(30);
    public static final int SHOW_TICKS = 40;
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.34f, 0.76f, 0.94f), 0.7f);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.87f, 0.34f, 0.27f), 0.7f);

    public record Pose(float travel, float drop, float tilt, float scale, boolean balls, float flash) {
        public float dist() { return AX - travel - ROD * (float) Math.sin(tilt); }

        public float lift() { return drop + ROD * (1f - (float) Math.cos(tilt)); }
    }

    public static final Pose REST = new Pose(0f, 0f, 0f, 1f, false, 0f);

    private static float seg(float t, float a, float b) {
        return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
    }

    private static float ease(float v) {
        return v * v * (3f - 2f * v);
    }

    public static Pose pose(float t) {
        float back = t < 0.10f ? 1f - ease(seg(t, 0f, 0.10f)) : 0f;
        float travel = ARM_TRAVEL * (t < 0.10f ? back : ease(seg(t, 0.30f, 0.80f)));
        float drop = -GRIP_DROP * (t < 0.20f ? ease(seg(t, 0.10f, 0.20f)) : 1f - ease(seg(t, 0.20f, 0.30f)));
        float tilt = TILT * (t < 0.10f ? back : ease(seg(t, 0.80f, 0.90f)));
        float scale = 1f - 0.4f * seg(t, 0.82f, 0.90f);
        float f = seg(t, 0.88f, 0.98f);
        float flash = f <= 0f ? 0f : f < 0.4f ? 1.6f * f / 0.4f : 1.6f * (1f - (f - 0.4f) / 0.6f);
        return new Pose(travel, drop, tilt, scale, t >= 0.15f && t < 0.90f, flash);
    }

    public static Pose pose(BigMachineBlockEntity be, float partialTick) {
        BigCrafter c = be.crafter();
        if (c == null || be.getLevel() == null || c.maxProgress() <= 0 || !(be.isRunning() || c.progress() > 0)) return REST;
        return pose(c.phase(be.getLevel().getGameTime(), partialTick));
    }

    private float lastPhase;
    private int cycles = -1, show;

    public int shown() { return show; }

    public static Vec3 center(BigMachineBlockEntity be) {
        BlockPos c = be.machine().structure.world(be.getBlockPos(), Math.max(be.formedRot(), 0), CX, CY, CZ);
        return Vec3.atCenterOf(c);
    }

    public static Vec3 axis(BigMachineBlockEntity be) {
        var s = be.machine().structure;
        int rot = Math.max(be.formedRot(), 0);
        BlockPos a = s.world(be.getBlockPos(), rot, 0, 0, 0), b = s.world(be.getBlockPos(), rot, 1, 0, 0);
        return new Vec3(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
    }

    void tick(BigMachineBlockEntity be) {
        Level level = be.getLevel();
        BigCrafter c = be.crafter();
        if (level == null || c == null || be.formedRot() < 0) return;
        if (show > 0) show--;
        if (cycles >= 0 && c.cycles() != cycles && !c.lastResult().isEmpty()) show = SHOW_TICKS;
        cycles = c.cycles();
        boolean active = c.maxProgress() > 0 && (be.isRunning() || c.progress() > 0);
        float t = active ? c.phase(level.getGameTime(), 0f) : 0f;
        Vec3 mid = center(be), ex = axis(be);
        RandomSource r = level.random;
        if (active && t >= 0.30f && t < 0.90f && level.getGameTime() % 2 == 0) {
            Pose ps = pose(t);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 p = mid.add(ex.scale(side * ps.dist() / 16f)).add(0, ps.lift() / 16f, 0);
                level.addParticle(side > 0 ? BLUE : RED, p.x + (r.nextDouble() - 0.5) * 0.15, p.y + (r.nextDouble() - 0.5) * 0.15,
                        p.z + (r.nextDouble() - 0.5) * 0.15, 0, 0, 0);
            }
        }
        if (active && lastPhase < 0.90f && t >= 0.90f) {
            for (int i = 0; i < 24; i++) {
                level.addParticle(ParticleTypes.PORTAL, mid.x, mid.y, mid.z, (r.nextDouble() - 0.5) * 1.6, (r.nextDouble() - 0.5) * 1.6,
                        (r.nextDouble() - 0.5) * 1.6);
            }
            for (int i = 0; i < 10; i++) {
                level.addParticle(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, (r.nextDouble() - 0.5) * 0.12, (r.nextDouble() - 0.5) * 0.12,
                        (r.nextDouble() - 0.5) * 0.12);
            }
            level.addParticle(ParticleTypes.FLASH, mid.x, mid.y, mid.z, 0, 0, 0);
        }
        if (show > SHOW_TICKS - 4) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, mid.x + (r.nextDouble() - 0.5) * 0.4, mid.y, mid.z + (r.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
        }
        lastPhase = t;
    }
}
