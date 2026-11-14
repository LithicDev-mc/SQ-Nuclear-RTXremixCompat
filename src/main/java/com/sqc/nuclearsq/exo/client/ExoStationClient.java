package com.sqc.nuclearsq.exo.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class ExoStationClient {
    private ExoStationClient() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ExoRegistry.STATION_MENU.get(), ExoStationScreen::new);
    }
}
