package com.sqc.nuclearsq.transport.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.transport.GridRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class GridClient {
    private GridClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GridRegistry.NODE_BE.get(), GridWireRenderer::new);
    }
}
