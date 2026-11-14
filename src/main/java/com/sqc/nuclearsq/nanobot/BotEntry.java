package com.sqc.nuclearsq.nanobot;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class BotEntry {
    private BotEntry() {}

    public static final int APPROACH = 8, CLIMB = 22, ARM = 10, TOTAL = CLIMB + ARM;
    public static final double SIDE = 0.36;

    private static double smooth(double t) {
        t = Mth.clamp(t, 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    public static Vec3 position(int t, Vec3 start, Vec3 feet, double dirX, double dirZ, Vec3 arm) {
        Vec3 foot = new Vec3(feet.x + dirX * SIDE, feet.y + 0.02, feet.z + dirZ * SIDE);
        Vec3 top = new Vec3(foot.x, Math.max(foot.y + 0.3, arm.y), foot.z);
        if (t <= APPROACH) return start.lerp(foot, smooth(t / (double) APPROACH));
        if (t <= CLIMB) return foot.lerp(top, smooth((t - APPROACH) / (double) (CLIMB - APPROACH)));
        return top.lerp(arm, smooth((t - CLIMB) / (double) ARM));
    }

    public static float scale(int t) {
        if (t <= CLIMB) return 1f;
        return 1f - (float) smooth((t - CLIMB) / (double) ARM);
    }

    public static Vec3 armPoint(Vec3 feet, float yawDeg, boolean rightArm) {
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double side = rightArm ? 0.38 : -0.38;
        return new Vec3(feet.x + rx * side + fx * 0.12, feet.y + 1.0, feet.z + rz * side + fz * 0.12);
    }
}
