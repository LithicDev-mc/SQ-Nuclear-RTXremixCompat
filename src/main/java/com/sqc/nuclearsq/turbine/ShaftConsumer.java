package com.sqc.nuclearsq.turbine;

import net.minecraft.core.BlockPos;

public interface ShaftConsumer {
    int acceptShaft(BlockPos coupling, int fe, float speed, boolean simulate);

    default boolean direct() {
        return false;
    }
}
