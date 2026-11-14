package com.sqc.nuclearsq.storage;

import java.util.function.IntPredicate;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class ClusterThermal {
    public static final int OFF = 0, RUNNING = 1, NO_POWER = 2, OVERHEATED = 3, INVALID = 4;

    private double heat;
    private boolean overheated;
    private int state = OFF;
    private double throttle = 1;

    public double heat() {
        return heat;
    }

    public int state() {
        return state;
    }

    public double throttle() {
        return throttle;
    }

    public static String stateName(int state) {
        return switch (state) {
            case RUNNING -> "running";
            case NO_POWER -> "no_power";
            case OVERHEATED -> "overheated";
            case INVALID -> "cluster_invalid";
            default -> "off";
        };
    }

    private static double throttleFor(double heat) {
        ClusterValues.Heat h = ClusterValues.heat();
        if (heat <= h.throttleFrom()) return 1;
        double min = h.minPct() / 100.0;
        double t = (heat - h.throttleFrom()) / Math.max(1e-6, h.shutdown() - h.throttleFrom());
        return Math.max(min, 1 - Math.min(1, t) * (1 - min));
    }

    public boolean step(boolean valid, boolean enabled, int fePerTick, IntPredicate takeEnergy, FluidTank water) {
        ClusterValues.Heat h = ClusterValues.heat();
        int was = state;
        double gen = 0;
        if (!valid) {
            state = INVALID;
        } else if (!enabled) {
            state = OFF;
        } else {
            throttle = throttleFor(heat);
            if (overheated && heat <= h.restart()) overheated = false;
            if (overheated) {
                state = OVERHEATED;
            } else {
                int need = (int) Math.ceil(fePerTick * throttle);
                if (takeEnergy.test(need)) {
                    state = RUNNING;
                    gen = need * h.perFe();
                } else {
                    state = NO_POWER;
                }
            }
        }
        heat = Math.max(0, heat + gen - h.passive());
        if (water != null && heat > h.coolTarget() && !water.getFluid().isEmpty()) {
            int mb = (int) Math.ceil((heat - h.coolTarget()) / h.coolPerMb());
            FluidStack used = water.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            heat = Math.max(h.coolTarget(), heat - used.getAmount() * h.coolPerMb());
        }
        if (heat >= h.shutdown()) {
            heat = h.shutdown();
            overheated = true;
        }
        return state != was;
    }

    public void save(CompoundTag tag) {
        tag.putDouble("heat", heat);
        tag.putBoolean("overheated", overheated);
    }

    public void load(CompoundTag tag) {
        heat = tag.getDouble("heat");
        overheated = tag.getBoolean("overheated");
    }
}
