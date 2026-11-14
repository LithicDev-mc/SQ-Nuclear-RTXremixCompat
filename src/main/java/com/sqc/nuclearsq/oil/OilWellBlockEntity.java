package com.sqc.nuclearsq.oil;

import com.sqc.nuclearsq.NuclearConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class OilWellBlockEntity extends BlockEntity {
    public static final long DAY_TICKS = 24000L;

    private int cls = -1;
    private int max;
    private double amount;
    private long last;

    public OilWellBlockEntity(BlockPos pos, BlockState state) {
        super(OilRegistry.WELL_BE.get(), pos, state);
    }

    public void init(OilClass c, int max, int amount, long now) {
        this.cls = c.ordinal();
        this.max = max;
        this.amount = Math.min(amount, max);
        this.last = now;
    }

    private void ensure(Level level) {
        if (cls < 0) init(OilClass.SMALL, OilClass.SMALL.base, OilClass.SMALL.base, level.getGameTime());
    }

    private void sync(Level level) {
        ensure(level);
        long now = level.getGameTime();
        long dt = now - last;
        if (dt > 0 && amount < max) {
            double pct = NuclearConfig.value(NuclearConfig.OIL_REFILL_PERCENT_PER_DAY);
            amount = Math.min(max, amount + dt * max * pct / 100.0 / DAY_TICKS);
            setChanged();
        }
        last = now;
    }

    public OilClass oilClass(Level level) {
        ensure(level);
        return OilClass.values()[cls];
    }

    public int max(Level level) {
        ensure(level);
        return max;
    }

    public int amount(Level level) {
        sync(level);
        return (int) amount;
    }

    public int drain(Level level, int want, boolean simulate) {
        sync(level);
        int take = Math.max(0, Math.min(want, (int) amount));
        if (!simulate && take > 0) {
            amount -= take;
            setChanged();
        }
        return take;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("cls", cls);
        tag.putInt("max", max);
        tag.putDouble("amount", amount);
        tag.putLong("last", last);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cls = tag.contains("cls") ? tag.getInt("cls") : -1;
        max = tag.getInt("max");
        amount = tag.getDouble("amount");
        last = tag.getLong("last");
    }
}
