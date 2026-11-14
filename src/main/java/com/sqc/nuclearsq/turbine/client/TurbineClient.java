package com.sqc.nuclearsq.turbine.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.registry.MultiblockRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class TurbineClient {
    private TurbineClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MultiblockRegistry.TURBINE_BE.get(), TurbineRenderer::new);
    }
}
