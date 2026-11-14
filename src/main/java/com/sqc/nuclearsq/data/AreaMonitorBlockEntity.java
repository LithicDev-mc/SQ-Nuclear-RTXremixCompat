package com.sqc.nuclearsq.data;

import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AreaMonitorBlockEntity extends BlockEntity {
    public static final int PERIOD = 20;
    public static final double DEFAULT_THRESHOLD = com.sqc.nuclearsq.radiation.RadiationValues.AREA_ALARM_SV_H;
    public static final double MIN_THRESHOLD = 1e-8, MAX_THRESHOLD = 1e6;

    private double rate = RadiationValues.AMBIENT_SV_H;
    private double peak = RadiationValues.AMBIENT_SV_H;
    private double threshold = DEFAULT_THRESHOLD;

    public AreaMonitorBlockEntity(BlockPos pos, BlockState state) {
        this(DataRegistry.AREA_MONITOR_BE.get(), pos, state);
    }

    protected AreaMonitorBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public double rate() {
        return rate;
    }

    public double peak() {
        return peak;
    }

    public double threshold() {
        return threshold;
    }

    public boolean alarm() {
        return rate > threshold;
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % PERIOD != Math.floorMod(worldPosition.asLong(), PERIOD)) return;
        measure();
    }

    public double measure() {
        if (!(level instanceof ServerLevel sl)) return rate;
        rate = sample(sl);
        peak = Math.max(peak, rate);
        setChanged();
        updateAlarm();
        return rate;
    }

    protected double sample(ServerLevel sl) {
        return RadiationManager.rateAt(sl, Vec3.atCenterOf(worldPosition)) + RadiationValues.AMBIENT_SV_H;
    }

    public void resetPeak() {
        peak = rate;
        setChanged();
    }

    public void setThreshold(double svH) {
        threshold = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, svH));
        setChanged();
        updateAlarm();
    }

    private void updateAlarm() {
        if (level == null || level.isClientSide) return;
        BlockState st = getBlockState();
        if (st.getBlock() instanceof AreaMonitorBlock b && level.getBlockState(worldPosition) == st) b.setAlarm(level, worldPosition, st, alarm());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("rate", rate);
        tag.putDouble("peak", peak);
        tag.putDouble("threshold", threshold);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("rate")) rate = tag.getDouble("rate");
        if (tag.contains("peak")) peak = tag.getDouble("peak");
        if (tag.contains("threshold")) threshold = Math.max(MIN_THRESHOLD, tag.getDouble("threshold"));
    }
}
