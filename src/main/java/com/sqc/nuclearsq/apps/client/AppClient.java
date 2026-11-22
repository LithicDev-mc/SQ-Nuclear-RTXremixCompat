package com.sqc.nuclearsq.apps.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.apps.AppRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class AppClient {
    private AppClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AppRegistry.LOGIN_MONITOR_BE.get(), LoginMonitorRenderer::new);
        event.registerBlockEntityRenderer(AppRegistry.AUCTION_MONITOR_BE.get(), AuctionMonitorRenderer::new);
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAppSession.receive(new com.sqc.nuclearsq.apps.AppPayloads.Session(false, ""));
    }
}
