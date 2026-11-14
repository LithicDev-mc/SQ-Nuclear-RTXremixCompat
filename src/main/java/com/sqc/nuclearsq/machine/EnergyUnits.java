package com.sqc.nuclearsq.machine;

public final class EnergyUnits {
    public static final int SU_PER_FE_T = 8;

    private EnergyUnits() {}

    public static int su(int fePerTick) {
        return fePerTick * SU_PER_FE_T;
    }
}
