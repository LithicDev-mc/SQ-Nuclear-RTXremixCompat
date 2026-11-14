package com.sqc.nuclearsq.power;

import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.registry.PowerRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class RtgBlockEntity extends BlockEntity {
    public static final int PEAK = 400;
    public static final int STEP_TICKS = 60;
    public static final int HALF_LIFE_STEPS = 1200;
    public static final int CAPSULE_STEPS = 4 * HALF_LIFE_STEPS;
    public static final int PEAK_PU238 = 1000, STEP_TICKS_PU238 = 4 * STEP_TICKS;
    public static final int PEAK_SR = 700, STEP_TICKS_SR = 2 * STEP_TICKS;

    private static final IEnergyStorage PORT = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) { return 0; }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return 0; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return false; }
    };

    private ItemStack capsule = ItemStack.EMPTY;
    private int age, timer, rate;
    private int delivered;

    public RtgBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.RTG_BE.get(), pos, state);
    }

    public static int rateFor(int damage) {
        if (damage >= CAPSULE_STEPS) return 0;
        return (int) Math.round(PEAK * Math.pow(0.5, damage / (double) HALF_LIFE_STEPS));
    }

    public int peak() {
        return capsule.getItem() instanceof RtgCapsuleItem c ? c.peak() : 0;
    }

    private int stepTicks() {
        return capsule.getItem() instanceof RtgCapsuleItem c ? c.stepTicks() : STEP_TICKS;
    }

    public static IEnergyStorage port() {
        return PORT;
    }

    public ItemStack capsule() { return capsule; }

    public boolean hasCapsule() { return !capsule.isEmpty(); }

    public int rate() { return rate; }

    public int delivered() { return delivered; }

    public int remainingPercent() {
        return capsule.isEmpty() ? 0 : Math.round(100f * (CAPSULE_STEPS - Math.min(CAPSULE_STEPS, capsule.getDamageValue())) / CAPSULE_STEPS);
    }

    public boolean insert(ItemStack one) {
        if (!capsule.isEmpty() || one.isEmpty()) return false;
        capsule = one.copyWithCount(1);
        age = 0;
        timer = 0;
        refresh();
        return true;
    }

    public ItemStack take() {
        ItemStack out = capsule;
        capsule = ItemStack.EMPTY;
        rate = 0;
        delivered = 0;
        refresh();
        return out;
    }

    private void refresh() {
        setChanged();
        if (level == null) return;
        boolean on = !capsule.isEmpty() && capsule.getDamageValue() < CAPSULE_STEPS;
        BlockState st = getBlockState();
        if (st.getValue(RtgBlock.LOADED) != on) {
            level.setBlock(worldPosition, st.setValue(RtgBlock.LOADED, on), 3);
            BlockState up = level.getBlockState(worldPosition.above());
            if (up.is(st.getBlock())) level.setBlock(worldPosition.above(), up.setValue(RtgBlock.LOADED, on), 3);
        } else {
            level.sendBlockUpdated(worldPosition, st, st, 3);
        }
    }

    public void serverTick() {
        if (level == null || capsule.isEmpty()) return;
        if (capsule.getDamageValue() >= CAPSULE_STEPS) {
            rate = 0;
            delivered = 0;
            return;
        }
        if (++age >= stepTicks()) {
            age = 0;
            int d = Math.min(CAPSULE_STEPS, capsule.getDamageValue() + 1);
            capsule.setDamageValue(d);
            setChanged();
            if (d >= CAPSULE_STEPS) {
                rate = 0;
                delivered = 0;
                refresh();
                return;
            }
        }
        if (timer-- <= 0) {
            timer = 20;
            rate = capsule.getItem() instanceof RtgCapsuleItem c ? c.rateFor(capsule.getDamageValue()) : rateFor(capsule.getDamageValue());
            if (rate > 0 && level instanceof ServerLevel sl) {
                RadiationManager.emit(sl, worldPosition, RadiationValues.RTG_SV_H * rate / PEAK);
            }
        }
        delivered = rate > 0 ? push(rate) : 0;
    }

    private int push(int amount) {
        int left = amount;
        int given = 0;
        BlockPos top = worldPosition.above();
        for (BlockPos self : new BlockPos[] {worldPosition, top}) {
            for (Direction dir : Direction.values()) {
                if (left <= 0) return given;
                BlockPos n = self.relative(dir);
                if (n.equals(worldPosition) || n.equals(top) || !level.isLoaded(n) || level.getBlockEntity(n) instanceof RtgBlockEntity) continue;
                IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, dir.getOpposite());
                if (target == null || !target.canReceive()) continue;
                int took = target.receiveEnergy(left, false);
                left -= took;
                given += took;
            }
        }
        return given;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!capsule.isEmpty()) tag.put("capsule", capsule.save(registries));
        tag.putInt("age", age);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        capsule = tag.contains("capsule") ? ItemStack.parseOptional(registries, tag.getCompound("capsule")) : ItemStack.EMPTY;
        age = tag.getInt("age");
    }
}
