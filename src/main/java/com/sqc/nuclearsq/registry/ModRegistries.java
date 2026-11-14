package com.sqc.nuclearsq.registry;

import net.neoforged.bus.api.IEventBus;

public final class ModRegistries {
    private ModRegistries() {}

    public static void register(IEventBus modBus) {
        ModDataComponents.register(modBus);
        ModEffects.register(modBus);
        ReactorRegistry.register(modBus);
        ModFluids.register(modBus);
        MultiblockRegistry.register(modBus);
        BigMachineRegistry.register(modBus);
        CatalogRegistry.register(modBus);
        ResourceRegistry.register(modBus);
        FusionRegistry.register(modBus);
        ModCreativeTabs.register(modBus);
        AssemblyRegistry.register(modBus);
        BombRegistry.register(modBus);
        ModParticles.register(modBus);
        ReprocessingRegistry.register(modBus);
        HeavyAssemblyRegistry.register(modBus);
        SmallPartsRegistry.register(modBus);
        EquipmentRegistry.register(modBus);
        TestSourceRegistry.register(modBus);
        com.sqc.nuclearsq.nanobot.NanobotRegistry.register(modBus);
        com.sqc.nuclearsq.transport.TransportRegistry.register(modBus);
        com.sqc.nuclearsq.transport.GridRegistry.register(modBus);
        com.sqc.nuclearsq.kit.KitRegistry.register(modBus);
        SpentFuelRegistry.register(modBus);
        PowerRegistry.register(modBus);
        com.sqc.nuclearsq.data.DataRegistry.register(modBus);
        com.sqc.nuclearsq.storage.StorageRegistry.register(modBus);
        com.sqc.nuclearsq.radiation.ProtectionRegistry.register(modBus);
        com.sqc.nuclearsq.particle.ParticleRegistry.register(modBus);
        com.sqc.nuclearsq.module.ModuleRegistry.register(modBus);
        com.sqc.nuclearsq.monitor.ImageMonitorRegistry.register(modBus);
        com.sqc.nuclearsq.economy.EconomyRegistry.register(modBus);
        com.sqc.nuclearsq.futurechest.FutureChestRegistry.register(modBus);
        com.sqc.nuclearsq.crate.CrateRegistry.register(modBus);
        com.sqc.nuclearsq.robotics.RoboticsRegistry.register(modBus);
        com.sqc.nuclearsq.robotics.decon.DeconRegistry.register(modBus);
        com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.register(modBus);
        com.sqc.nuclearsq.robotics.drone.DroneRegistry.register(modBus);
        com.sqc.nuclearsq.exo.ExoRegistry.register(modBus);
        com.sqc.nuclearsq.oil.OilRegistry.register(modBus);
        com.sqc.nuclearsq.door.DoorRegistry.register(modBus);
        ModSounds.register(modBus);
    }
}
