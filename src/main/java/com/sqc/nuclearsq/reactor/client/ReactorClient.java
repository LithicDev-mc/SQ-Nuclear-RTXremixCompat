package com.sqc.nuclearsq.reactor.client;

import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

import com.sqc.nuclearsq.NuclearSQ;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class ReactorClient {
    private ReactorClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ReactorRegistry.FUEL_ROD_BE.get(), FuelRodRenderer::new);
    }

    @SubscribeEvent
    public static void onBaked(ModelEvent.BakingCompleted event) {
        RodGlow.clearCache();
    }

    @SubscribeEvent
    public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        event.register(RodGlow.GLOW);
    }
}
