package com.sqc.nuclearsq.machine;

import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.fluids.FluidStack;

public final class CoolantTypes {
    private CoolantTypes() {}

    public record Type(int index, float factor, int minTier) {}

    private static final Type WATER = new Type(1, 0.4f, 0);
    private static final Type COOLANT = new Type(2, 1.0f, 0);
    private static final Type SODIUM = new Type(3, 1.6f, 2);
    private static final Type CRYO = new Type(4, 2.4f, 3);
    private static final ResourceLocation MEK_SODIUM = ResourceLocation.fromNamespaceAndPath("mekanism", "sodium");

    public static Type of(FluidStack fs) {
        if (fs.isEmpty()) return null;
        if (fs.is(ModFluids.COOLANT.source.get())) return COOLANT;
        if (fs.is(ModFluids.SODIUM_COOLANT.source.get())) return SODIUM;
        if (fs.is(ModFluids.CRYO_COOLANT.source.get())) return CRYO;
        if (fs.is(FluidTags.WATER)) return WATER;
        if (MEK_SODIUM.equals(BuiltInRegistries.FLUID.getKey(fs.getFluid()))) return SODIUM;
        return null;
    }

    public static int heatFactor(FluidStack fs) {
        Type t = of(fs);
        if (t == COOLANT) return 1;
        if (t == SODIUM) return 2;
        if (t == CRYO) return 4;
        return 0;
    }
}
