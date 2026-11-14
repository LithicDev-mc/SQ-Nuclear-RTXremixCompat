package com.sqc.nuclearsq.radiation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class Contamination {
    private Contamination() {}

    public static final float RESIDUE_SV_H = RadiationValues.RESIDUE_GROUND_SV_H;
    public static final double RESIDUE_CHANCE = 0.35;
    public static final float MAX_SV_H = RadiationValues.MAX_GROUND_SV_H;
    public static final float CLEAN_SV_H = RadiationValues.GROUND_CLEAN_SV_H;
    public static final double REACH = 16.0;
    public static final double PICKUP = RadiationValues.BODY_PICKUP;
    public static final double BODY_MAX = RadiationValues.BODY_MAX_SV_H;
    public static final double BODY_DECAY = RadiationValues.BODY_DECAY;
    public static final double SHOWER_KEEP = 0.2;

    public static final class Zones {
        private final RadiationWorldData owner;
        final Long2FloatOpenHashMap level = new Long2FloatOpenHashMap();
        final ChunkIndexedPositions index = new ChunkIndexedPositions();

        Zones(RadiationWorldData owner) {
            this.owner = owner;
        }

        void put(long p, float v) {
            if (v < CLEAN_SV_H) {
                if (level.containsKey(p)) {
                    level.remove(p);
                    index.remove(p);
                }
                return;
            }
            level.put(p, Math.min(MAX_SV_H, v));
            index.add(p);
        }

        public float get(BlockPos p) {
            return level.getOrDefault(p.asLong(), 0f);
        }

        public int size() {
            return level.size();
        }

        long[] positions() {
            return level.keySet().toLongArray();
        }

        public void setDirty() {
            owner.setDirty();
        }

        void forNear(Vec3 p, double reach, LongConsumer action) {
            index.forNear(p, reach, action);
        }
    }

    public static Zones zones(ServerLevel lv) {
        return RadiationWorldData.get(lv).zones;
    }

    public static void add(ServerLevel lv, BlockPos pos, float svH) {
        Zones z = zones(lv);
        z.put(pos.asLong(), z.get(pos) + svH);
        z.setDirty();
    }

    public static void depositNear(ServerLevel lv, BlockPos center, int range, float svH) {
        var r = lv.random;
        for (int tries = 0; tries < 6; tries++) {
            BlockPos p = center.offset(r.nextInt(range * 2 + 1) - range, r.nextInt(range * 2 + 1) - range, r.nextInt(range * 2 + 1) - range);
            if (!lv.isLoaded(p) || !lv.getBlockState(p).isAir()) continue;
            BlockPos below = p.below();
            if (!lv.getBlockState(below).isFaceSturdy(lv, below, net.minecraft.core.Direction.UP)) continue;
            if (isFixed(lv, p)) continue;
            add(lv, p, svH);
            return;
        }
    }

    public static int clean(ServerLevel lv, BlockPos center, int r, float amount) {
        Zones z = zones(lv);
        List<Long> hit = new ArrayList<>();
        z.forNear(Vec3.atCenterOf(center), r + 1, l -> {
            if (Math.abs(BlockPos.getX(l) - center.getX()) <= r && Math.abs(BlockPos.getZ(l) - center.getZ()) <= r
                    && Math.abs(BlockPos.getY(l) - center.getY()) <= r + 1) hit.add(l);
        });
        hit.removeIf(l -> isFixed(lv, BlockPos.of(l)));
        for (long l : hit) z.put(l, z.level.get(l) - amount);
        if (!hit.isEmpty()) z.setDirty();
        return hit.size();
    }

    public static boolean isFixed(ServerLevel lv, BlockPos pos) {
        return RadiationManager.isActiveSource(lv, pos) || RadiationManager.isActiveSource(lv, pos.below())
                || lv.getBlockEntity(pos) instanceof com.sqc.nuclearsq.reactor.FuelRodBlockEntity
                || lv.getBlockEntity(pos.below()) instanceof com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
    }

    public static double rateAt(ServerLevel lv, Vec3 p) {
        Zones z = zones(lv);
        if (z.level.isEmpty()) return 0;
        double[] sum = {0};
        z.forNear(p, REACH, l -> {
            double dx = BlockPos.getX(l) + 0.5 - p.x, dy = BlockPos.getY(l) + 0.5 - p.y, dz = BlockPos.getZ(l) + 0.5 - p.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= REACH * REACH) sum[0] += z.level.get(l) / Math.max(d2, 1.0);
        });
        return sum[0];
    }

    static float underFoot(ServerLevel lv, LivingEntity e) {
        Zones z = zones(lv);
        if (z.level.isEmpty()) return 0;
        BlockPos f = e.blockPosition();
        float best = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 0; dy++) best = Math.max(best, z.level.getOrDefault(f.offset(dx, dy, dz).asLong(), 0f));
            }
        }
        return best;
    }

    public static double body(Player p) {
        Double b = p.getExistingDataOrNull(RadiationAttachments.BODY);
        return b == null ? 0 : b;
    }

    public static void setBody(Player p, double svH) {
        if (svH < 1e-7) p.removeData(RadiationAttachments.BODY);
        else p.setData(RadiationAttachments.BODY, Math.min(BODY_MAX, svH));
    }

    static void tickBody(ServerLevel lv, Player p, double shielding) {
        double b = body(p) * BODY_DECAY;
        float ground = underFoot(lv, p);
        if (fallout(lv, p)) ground = Math.max(ground, (float) RadiationValues.FALLOUT_SV_H);
        if (ground > 0) b += ground * PICKUP * com.sqc.nuclearsq.NuclearConfig.radiationFactor() * (1.0 - shielding) * RadiationManager.iodineFactor(p) * RespiratorItem.dustFactor(p);
        double lose = b * RadiationValues.BODY_SPREAD;
        if (b >= RadiationValues.BODY_SPREAD_MIN_SV_H && lose >= CLEAN_SV_H) {
            BlockPos at = p.blockPosition();
            if (zones(lv).get(at) < b && lv.getBlockState(at.below()).isFaceSturdy(lv, at.below(), net.minecraft.core.Direction.UP)) {
                add(lv, at, (float) lose);
                b -= lose;
            }
        }
        setBody(p, b);
    }

    static boolean dirtyAir(ServerLevel lv, Player p) {
        return underFoot(lv, p) > 0 || fallout(lv, p);
    }

    private static boolean fallout(ServerLevel lv, Player p) {
        BlockPos f = p.blockPosition();
        var block = com.sqc.nuclearsq.registry.CatalogRegistry.FALLOUT.get();
        return lv.getBlockState(f).is(block) || lv.getBlockState(f.below()).is(block);
    }

    public static final double SPRAY_KEEP = 0.25;

    public static boolean sprayBody(Player p) {
        double b = body(p);
        boolean items = ItemContamination.carried(p) > 0;
        if (b <= 0 && !items) return false;
        setBody(p, b * SPRAY_KEEP);
        ItemContamination.washInventory(p, SPRAY_KEEP);
        return true;
    }

    public static void wash(Player p) {
        setBody(p, body(p) * SHOWER_KEEP);
        ItemContamination.washInventory(p, SHOWER_KEEP);
    }
}
