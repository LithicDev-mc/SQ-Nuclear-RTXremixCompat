package com.sqc.nuclearsq.compat.sable;

import net.minecraft.core.Position;
import java.util.function.Predicate;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class SableCompat {
    private SableCompat() {}

    public static final String SABLE_ID = "sable";

    private static boolean broken, physicsBroken;
    private static Boolean sableLoaded;

    public static boolean isOnSubLevel(Level level, BlockPos pos) {
        if (broken) return false;
        try {
            return SableCompanion.INSTANCE.getContaining(level, pos) != null;
        } catch (Throwable t) {
            broken = true;
            NuclearSQ.LOGGER.warn("NuclearSQ: Sable compatibility disabled after an error", t);
            return false;
        }
    }

    public static Vec3 toWorld(Level level, Vec3 pos) {
        if (broken) return pos;
        try {
            return SableCompanion.INSTANCE.projectOutOfSubLevel(level, (Position) pos);
        } catch (Throwable t) {
            broken = true;
            NuclearSQ.LOGGER.warn("NuclearSQ: Sable compatibility disabled after an error", t);
            return pos;
        }
    }

    public static BlockPos toWorld(Level level, BlockPos pos) {
        if (!isOnSubLevel(level, pos)) return pos;
        return BlockPos.containing(toWorld(level, Vec3.atCenterOf(pos)));
    }

    public static double distSqr(Level level, Vec3 a, Vec3 b) {
        return toWorld(level, a).distanceToSqr(toWorld(level, b));
    }

    public static double distSqr(Level level, BlockPos a, BlockPos b) {
        return distSqr(level, Vec3.atCenterOf(a), Vec3.atCenterOf(b));
    }

    public static <T extends Entity> List<T> entitiesIn(Level level, Class<T> type, AABB box, Predicate<? super T> filter) {
        if (!broken) {
            try {
                SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, (Position) box.getCenter());
                if (sub != null) {
                    Pose3dc pose = sub.logicalPose();
                    double x0 = Double.MAX_VALUE, y0 = x0, z0 = x0, x1 = -x0, y1 = x1, z1 = x1;
                    for (int i = 0; i < 8; i++) {
                        Vec3 w = pose.transformPosition(new Vec3((i & 1) == 0 ? box.minX : box.maxX,
                                (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ));
                        x0 = Math.min(x0, w.x); y0 = Math.min(y0, w.y); z0 = Math.min(z0, w.z);
                        x1 = Math.max(x1, w.x); y1 = Math.max(y1, w.y); z1 = Math.max(z1, w.z);
                    }
                    return level.getEntitiesOfClass(type, new AABB(x0, y0, z0, x1, y1, z1),
                            e -> box.contains(pose.transformPositionInverse(e.position())) && (filter == null || filter.test(e)));
                }
            } catch (Throwable t) {
                broken = true;
                NuclearSQ.LOGGER.warn("NuclearSQ: Sable compatibility disabled after an error", t);
            }
        }
        return filter == null ? level.getEntitiesOfClass(type, box) : level.getEntitiesOfClass(type, box, filter);
    }

    public static boolean hasPhysics() {
        if (physicsBroken) return false;
        if (sableLoaded == null) sableLoaded = ModList.get().isLoaded(SABLE_ID);
        return sableLoaded;
    }

    public static boolean makePhysical(ServerLevel level, BlockPos anchor, List<BlockPos> blocks, Vec3 motion) {
        if (!hasPhysics()) return false;
        try {
            return SablePhysics.makePhysical(level, anchor, blocks, motion);
        } catch (Throwable t) {
            physicsBroken = true;
            NuclearSQ.LOGGER.warn("NuclearSQ: Sable physics disabled after an error, bombs fall as entities", t);
            return false;
        }
    }
}
