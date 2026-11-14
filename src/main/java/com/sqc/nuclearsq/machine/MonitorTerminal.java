package com.sqc.nuclearsq.machine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

public interface MonitorTerminal {
    @Nullable
    double[] monitorBox();

    int monitorRot();

    void openMonitor(ServerPlayer player);

    @Nullable
    default AABB monitorWorldBox(net.minecraft.core.BlockPos ctrl) {
        double[] m = monitorBox();
        int rot = monitorRot();
        if (m == null || rot < 0) return null;
        double[] a = rotate(m[0], m[2], rot), b = rotate(m[3], m[5], rot);
        return new AABB(ctrl.getX() + a[0], ctrl.getY() + m[1], ctrl.getZ() + a[1],
                ctrl.getX() + b[0], ctrl.getY() + m[4], ctrl.getZ() + b[1]).inflate(1 / 32.0);
    }

    static double[] rotate(double x, double z, int rot) {
        for (int i = 0; i < rot; i++) {
            double nx = 1 - z;
            z = x;
            x = nx;
        }
        return new double[] {x, z};
    }
}
