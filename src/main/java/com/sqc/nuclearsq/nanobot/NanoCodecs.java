package com.sqc.nuclearsq.nanobot;

import com.mojang.serialization.Codec;

import net.minecraft.util.Mth;

public final class NanoCodecs {
    private NanoCodecs() {}

    public static Codec<Integer> clamped(int min, int max) {
        return Codec.INT.xmap(v -> Mth.clamp(v, min, max), v -> Mth.clamp(v, min, max));
    }
}
