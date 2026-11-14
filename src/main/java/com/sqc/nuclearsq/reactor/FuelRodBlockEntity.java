package com.sqc.nuclearsq.reactor;

import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.registry.ModDataComponents;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FuelRodBlockEntity extends BlockEntity {
    public static final int UNIT = 14400;
    static final int OLD_UNIT = 1000;

    public static final double SV_H = RadiationValues.FRESH_ROD_SV_H;

    public static double svH(int remaining, boolean cooled) {
        int r = Mth.clamp(remaining, 0, UNIT);
        if (r <= 0 && cooled) return RadiationValues.COOLED_ROD_SV_H;
        double rest = r / (double) UNIT;
        return RadiationValues.FRESH_ROD_SV_H * rest + RadiationValues.SPENT_ROD_SV_H * (1 - rest);
    }

    private int remaining = UNIT;
    private boolean cooled;
    private boolean unloaded;
    private boolean mox;

    public FuelRodBlockEntity(BlockPos pos, BlockState state) {
        super(ReactorRegistry.FUEL_ROD_BE.get(), pos, state);
    }

    public int getRemaining() {
        return remaining;
    }

    public boolean burnOne() {
        if (remaining <= 0) return false;
        remaining--;
        setChanged();
        syncState();
        return true;
    }

    public void setRemaining(int value) {
        remaining = Mth.clamp(value, 0, UNIT);
        setChanged();
        syncState();
    }

    public boolean isCooled() {
        return cooled && remaining <= 0 && !unloaded;
    }

    public boolean isMox() {
        return mox;
    }

    public void setMox(boolean value) {
        mox = value;
        setChanged();
    }

    public boolean isUnloaded() {
        return unloaded;
    }

    public void setCooled(boolean value) {
        cooled = value;
        setChanged();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (Math.floorMod(sl.getGameTime() + worldPosition.asLong(), 20L) != 0) return;
        if (unloaded) return;
        RadiationManager.emit(sl, worldPosition, svH(remaining, isCooled()));
    }

    public void syncState() {
        if (level == null || level.isClientSide) return;
        BlockState s = getBlockState();
        int charge = Mth.clamp((int) Math.ceil(remaining * 4.0 / UNIT), 0, 4);
        if (s.getValue(FuelRodBlock.CHARGE) != charge) {
            level.setBlock(worldPosition, s.setValue(FuelRodBlock.CHARGE, charge), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("remaining", remaining);
        tag.putInt("unit", UNIT);
        if (cooled) tag.putBoolean("cooled", true);
        if (unloaded) tag.putBoolean("unloaded", true);
        if (mox) tag.putBoolean("mox", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("remaining")) {
            int r = tag.getInt("remaining");
            int unit = tag.contains("unit") ? tag.getInt("unit") : OLD_UNIT;
            if (unit != UNIT && unit > 0) r = (int) ((long) r * UNIT / unit);
            remaining = Mth.clamp(r, 0, UNIT);
        }
        cooled = tag.getBoolean("cooled");
        unloaded = tag.getBoolean("unloaded");
        mox = tag.getBoolean("mox");
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (remaining < UNIT) components.set(ModDataComponents.FUEL_CHARGE.get(), remaining);
        if (unloaded) components.set(ModDataComponents.FUEL_UNLOADED.get(), net.minecraft.util.Unit.INSTANCE);
        if (mox) components.set(ModDataComponents.FUEL_MOX.get(), net.minecraft.util.Unit.INSTANCE);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer charge = input.get(ModDataComponents.FUEL_CHARGE.get());
        if (charge != null) remaining = Mth.clamp(charge, 0, UNIT);
        unloaded = input.get(ModDataComponents.FUEL_UNLOADED.get()) != null;
        mox = input.get(ModDataComponents.FUEL_MOX.get()) != null;
        if (unloaded) remaining = 0;
    }
}
