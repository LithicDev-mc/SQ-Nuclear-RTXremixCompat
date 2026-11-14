package com.sqc.nuclearsq.crate.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.crate.CrateRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class StorageCrateClient {
    private StorageCrateClient() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(CrateRegistry.MENU.get(), StorageCrateScreen::new);
    }
}
