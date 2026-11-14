package com.sqc.nuclearsq.nanobot;

import net.minecraft.world.phys.Vec3;

public final class NanoSmooth {
    private NanoSmooth() {}

    public static double damp(double cur, double target, double[] vel, double smoothTime, double dt) {
        smoothTime = Math.max(0.0001, smoothTime);
        double omega = 2.0 / smoothTime;
        double x = omega * dt;
        double exp = 1.0 / (1.0 + x + 0.48 * x * x + 0.235 * x * x * x);
        double change = cur - target;
        double temp = (vel[0] + omega * change) * dt;
        vel[0] = (vel[0] - omega * temp) * exp;
        double out = target + (change + temp) * exp;
        if ((target - cur > 0.0) == (out > target)) {
            out = target;
            vel[0] = (out - target) / dt;
        }
        return out;
    }

    public static final class Follower {
        private double x, y, z, px, py, pz;
        private final double[] vx = new double[1], vy = new double[1], vz = new double[1];
        private boolean init;

        public boolean initialized() {
            return init;
        }

        public void snap(Vec3 p) {
            x = px = p.x;
            y = py = p.y;
            z = pz = p.z;
            vx[0] = vy[0] = vz[0] = 0;
            init = true;
        }

        public void step(Vec3 target, double smoothTime) {
            if (!init) {
                snap(target);
                return;
            }
            px = x;
            py = y;
            pz = z;
            x = damp(x, target.x, vx, smoothTime, 1.0);
            y = damp(y, target.y, vy, smoothTime, 1.0);
            z = damp(z, target.z, vz, smoothTime, 1.0);
        }

        public Vec3 pos() {
            return new Vec3(x, y, z);
        }

        public Vec3 lerp(double t) {
            return new Vec3(px + (x - px) * t, py + (y - py) * t, pz + (z - pz) * t);
        }
    }
}
