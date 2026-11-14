package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    private ModEffects() {}

    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, NuclearSQ.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> RADIATION_PROTECTION =
            EFFECTS.register("radiation_protection", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x43E88D) {});
    public static final DeferredHolder<MobEffect, MobEffect> IODINE =
            EFFECTS.register("iodine", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xE8642C) {});

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
