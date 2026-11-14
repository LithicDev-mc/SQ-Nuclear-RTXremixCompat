package com.sqc.nuclearsq.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RadiationCompass {
    private RadiationCompass() {}

    public static final double RANGE = 64.0;
    public static final double MIN_SV_H = 5e-7;
    public static final double HERE_DISTANCE = 1.5;

    public static int level(double apparentSvH) {
        return GeigerCounterItem.level(apparentSvH);
    }

    public record Target(Vec3 pos, double apparentSvH, double distance) {
        public int level() {
            return RadiationCompass.level(apparentSvH);
        }

        public boolean here(Vec3 from) {
            double dx = pos.x - from.x, dz = pos.z - from.z;
            return Math.sqrt(dx * dx + dz * dz) < HERE_DISTANCE;
        }
    }

    public static final class Best {
        private final Vec3 from;
        private final double range2;
        private double bestSv;
        private double bx, by, bz, bd2;
        private boolean found;

        public Best(Vec3 from, double range) {
            this.from = from;
            this.range2 = range * range;
        }

        public void offer(double x, double y, double z, double svH) {
            double dx = x - from.x, dy = y - from.y, dz = z - from.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > range2) return;
            double apparent = svH / Math.max(d2, 1.0);
            if (apparent < MIN_SV_H || (found && apparent <= bestSv)) return;
            found = true;
            bestSv = apparent;
            bx = x;
            by = y;
            bz = z;
            bd2 = d2;
        }

        public Target result() {
            return found ? new Target(new Vec3(bx, by, bz), bestSv, Math.sqrt(bd2)) : null;
        }
    }

    public static Target scan(ServerLevel lv, Vec3 from) {
        RadiationWorldData data = RadiationWorldData.get(lv);
        Best best = new Best(from, RANGE);
        long tick = lv.getServer().getTickCount();
        for (var en : data.emitters.long2ObjectEntrySet()) {
            if (en.getValue().until() < tick) continue;
            BlockPos p = BlockPos.of(en.getLongKey());
            best.offer(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, en.getValue().svH());
        }
        long now = lv.getGameTime();
        for (RadiationWorldData.Core k : data.cores) {
            best.offer(k.pos().x, k.pos().y, k.pos().z, k.rateAt(now));
        }
        Contamination.Zones z = data.zones;
        z.forNear(from, RANGE, l -> {
            BlockPos p = BlockPos.of(l);
            best.offer(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, z.get(p));
        });
        data.fallout.forNear(from, RANGE, l -> {
            BlockPos p = BlockPos.of(l);
            best.offer(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, RadiationValues.FALLOUT_SV_H);
        });
        return best.result();
    }

    public static float bearingTurns(double playerYawDeg, double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double turns = Mth.wrapDegrees(targetYaw - playerYawDeg) / 360.0;
        return (float) (turns - Math.floor(turns));
    }
}
