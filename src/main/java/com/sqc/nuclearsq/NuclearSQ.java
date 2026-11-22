package com.sqc.nuclearsq;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.sqc.nuclearsq.compat.cc.ReactorCcCompat;
import com.sqc.nuclearsq.compat.create.CreateCompat;
import com.sqc.nuclearsq.network.ModNetwork;
import com.sqc.nuclearsq.registry.ModRegistries;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(NuclearSQ.MODID)
public class NuclearSQ {
    public static final String MODID = "nuclearsq";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NuclearSQ(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.register(modEventBus);
        com.sqc.nuclearsq.radiation.RadiationAttachments.register(modEventBus);
        com.sqc.nuclearsq.progression.Discovered.register(modEventBus);
        com.sqc.nuclearsq.progression.GuideProgress.register(modEventBus);
        com.sqc.nuclearsq.progression.Titles.register(modEventBus);
        com.sqc.nuclearsq.progression.Streak.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(com.sqc.nuclearsq.storage.ChunkTickets::register);
        modEventBus.addListener(this::commonSetup);

        if (ModList.get().isLoaded("create")) {
            com.sqc.nuclearsq.compat.create.CreateShaftRegistry.register(modEventBus);
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                com.sqc.nuclearsq.compat.create.client.CreateShaftClient.register(modEventBus);
            }
        }
        if (ModList.get().isLoaded("flywheel") && net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            com.sqc.nuclearsq.compat.flywheel.FlywheelSwarmCompat.register();
        }
        if (ModList.get().isLoaded("distanthorizons") && net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            com.sqc.nuclearsq.compat.dh.DhCompat.register();
        }
        if (ModList.get().isLoaded("powergrid")) {
            com.sqc.nuclearsq.compat.powergrid.PowerGridRegistry.register(modEventBus);
        }
        if (ModList.get().isLoaded("computercraft")) {
            modEventBus.addListener(ReactorCcCompat::registerCapabilities);
            modEventBus.addListener(com.sqc.nuclearsq.compat.cc.DataCcCompat::registerCapabilities);
            modEventBus.addListener(com.sqc.nuclearsq.compat.cc.EconomyCcCompat::registerCapabilities);
            if (ModList.get().isLoaded("create")) modEventBus.addListener(com.sqc.nuclearsq.compat.cc.CreateCcCompat::registerCapabilities);
            if (ModList.get().isLoaded("powergrid")) modEventBus.addListener(com.sqc.nuclearsq.compat.cc.PowerGridCcCompat::registerCapabilities);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.sqc.nuclearsq.compat.cc.CcEvents::onServerTick);
        }

        modContainer.registerConfig(ModConfig.Type.SERVER, NuclearConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, NuclearClientConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("create")) {
            event.enqueueWork(CreateCompat::register);
            LOGGER.info("NuclearSQ: Create compatibility enabled");
        }
    }
}
