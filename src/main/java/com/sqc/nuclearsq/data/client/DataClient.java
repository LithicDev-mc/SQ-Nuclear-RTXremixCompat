package com.sqc.nuclearsq.data.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class DataClient {
    private DataClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DataRegistry.INSTRUMENT_BE.get(), InstrumentRenderer::new);
        event.registerBlockEntityRenderer(DataRegistry.MULTI_BE.get(), DataMultiRenderer::new);
        event.registerBlockEntityRenderer(DataRegistry.LOGBOOK_BE.get(), LogbookRenderer::new);
        event.registerBlockEntityRenderer(DataRegistry.POWER_BE.get(), PowerMonitorRenderer::new);
        event.registerBlockEntityRenderer(DataRegistry.WALL_BE.get(), ControlWallRenderer::new);
        event.registerBlockEntityRenderer(com.sqc.nuclearsq.storage.StorageRegistry.MONITOR_BE.get(), com.sqc.nuclearsq.storage.client.StorageMonitorRenderer::new);
    }
}
