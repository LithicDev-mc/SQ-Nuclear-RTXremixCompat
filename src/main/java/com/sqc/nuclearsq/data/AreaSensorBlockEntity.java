package com.sqc.nuclearsq.data;

import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AreaSensorBlockEntity extends AreaMonitorBlockEntity {
    public static final int DEFAULT_RADIUS = RadiationValues.AREA_SENSOR_RADIUS, MIN_RADIUS = 1, MAX_RADIUS = 16;
    private static final int GRID = 2;

    private int radius = DEFAULT_RADIUS;
    private double average = RadiationValues.AMBIENT_SV_H;
    private BlockPos hottest;

    public AreaSensorBlockEntity(BlockPos pos, BlockState state) {
        super(DataRegistry.AREA_SENSOR_BE.get(), pos, state);
        hottest = pos;
    }

    public int radius() {
        return radius;
    }

    public double average() {
        return average;
    }

    public BlockPos hottest() {
        return hottest;
    }

    public void setRadius(int r) {
        radius = Mth.clamp(r, MIN_RADIUS, MAX_RADIUS);
        setChanged();
        measure();
    }

    @Override
    protected double sample(ServerLevel sl) {
        double r2 = (double) radius * radius;
        int step = Math.max(1, (int) Math.ceil(radius / (double) GRID));
        Vec3 c = Vec3.atCenterOf(worldPosition);
        double max = RadiationManager.rateAt(sl, c), sum = max;
        int n = 1;
        BlockPos hot = worldPosition;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -GRID * step; dx <= GRID * step; dx += step) {
            for (int dy = -GRID * step; dy <= GRID * step; dy += step) {
                for (int dz = -GRID * step; dz <= GRID * step; dz += step) {
                    if (dx == 0 && dy == 0 && dz == 0 || dx * dx + dy * dy + dz * dz > r2) continue;
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    if (!sl.isLoaded(p) || sl.getBlockState(p).isSolidRender(sl, p)) continue;
                    double v = RadiationManager.rateAt(sl, Vec3.atCenterOf(p));
                    sum += v;
                    n++;
                    if (v > max) {
                        max = v;
                        hot = p.immutable();
                    }
                }
            }
        }
        for (long l : RadiationManager.debugSources(sl, c, radius).keySet()) {
            Vec3 s = Vec3.atCenterOf(BlockPos.of(l));
            Vec3 d = c.subtract(s);
            Vec3 at = d.lengthSqr() < 1 ? c : s.add(d.normalize());
            BlockPos ab = BlockPos.containing(at);
            if (!ab.equals(worldPosition) && (!sl.isLoaded(ab) || sl.getBlockState(ab).isSolidRender(sl, ab))) continue;
            double v = RadiationManager.rateAt(sl, at);
            if (v > max) {
                max = v;
                hot = ab;
            }
        }
        average = sum / n + RadiationValues.AMBIENT_SV_H;
        hottest = hot;
        return max + RadiationValues.AMBIENT_SV_H;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("radius", radius);
        tag.putDouble("average", average);
        tag.putLong("hottest", hottest.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("radius")) radius = Mth.clamp(tag.getInt("radius"), MIN_RADIUS, MAX_RADIUS);
        if (tag.contains("average")) average = tag.getDouble("average");
        if (tag.contains("hottest")) hottest = BlockPos.of(tag.getLong("hottest"));
    }
}
