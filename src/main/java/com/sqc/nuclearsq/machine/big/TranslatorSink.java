package com.sqc.nuclearsq.machine.big;

import net.minecraft.core.BlockPos;

public interface TranslatorSink {
    int acceptTranslated(BlockPos from, int fe, boolean simulate);
}
