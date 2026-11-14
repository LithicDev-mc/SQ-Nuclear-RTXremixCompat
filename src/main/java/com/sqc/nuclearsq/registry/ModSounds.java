package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    private ModSounds() {}

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, NuclearSQ.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> TURBINE = sound("turbine");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP = sound("pump");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER = sound("geiger");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_ALARM = sound("reactor_alarm");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
