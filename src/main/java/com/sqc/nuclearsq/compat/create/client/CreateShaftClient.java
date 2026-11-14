package com.sqc.nuclearsq.compat.create.client;

import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.sqc.nuclearsq.compat.create.CreateShaftRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class CreateShaftClient {
    private CreateShaftClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CreateShaftClient::onRenderers);
        modBus.addListener(CreateShaftClient::onSetup);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateShaftRegistry.OUTPUT_BE.get(), ShaftRenderer::new);
    }

    private static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer.builder(CreateShaftRegistry.OUTPUT_BE.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .skipVanillaRender(be -> false)
                .apply());
    }
}
