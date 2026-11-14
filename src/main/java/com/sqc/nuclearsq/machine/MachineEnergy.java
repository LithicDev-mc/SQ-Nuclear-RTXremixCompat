package com.sqc.nuclearsq.machine;

import net.neoforged.neoforge.energy.EnergyStorage;

public class MachineEnergy extends EnergyStorage {
    public MachineEnergy(int capacity, int maxReceive) {
        super(capacity, maxReceive, 0);
    }

    public MachineEnergy(int capacity, int maxReceive, int maxExtract) {
        super(capacity, maxReceive, maxExtract);
    }

    private Runnable onChange = () -> {};

    public MachineEnergy onChange(Runnable r) {
        onChange = r;
        return this;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int n = super.receiveEnergy(toReceive, simulate);
        if (!simulate && n > 0) onChange.run();
        return n;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        int n = super.extractEnergy(toExtract, simulate);
        if (!simulate && n > 0) onChange.run();
        return n;
    }

    public int add(int amount) {
        int n = Math.max(0, Math.min(capacity - energy, amount));
        energy += n;
        return n;
    }

    public int drain(int amount) {
        int taken = Math.min(energy, amount);
        energy -= taken;
        drained += taken;
        return taken;
    }

    private long drained;

    public long drainedTotal() {
        return drained;
    }

    public void setStored(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }
}
