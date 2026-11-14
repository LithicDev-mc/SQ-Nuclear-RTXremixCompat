package com.sqc.nuclearsq.client;


import com.mojang.blaze3d.platform.InputConstants;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class ZoomClient {
    private ZoomClient() {}

    public static final KeyMapping ZOOM_IN = new KeyMapping("key.nuclearsq.zoom_in",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.nuclearsq");
    public static final KeyMapping ZOOM_OUT = new KeyMapping("key.nuclearsq.zoom_out",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.nuclearsq");

    private static final double STEP = 1.5, MAX = 10.0;
    private static double target = 1.0, zoom = 1.0, oZoom = 1.0;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        double before = target;
        while (ZOOM_IN.consumeClick()) target = Math.min(MAX, target * STEP);
        while (ZOOM_OUT.consumeClick()) target = Math.max(1.0, target / STEP);
        if (target < 1.01) target = 1.0;
        if (mc.player == null) { target = zoom = oZoom = 1.0; return; }
        if (target != before)
            mc.player.displayClientMessage(Component.translatable("message.nuclearsq.zoom", String.format("%.1f", target)), true);
        oZoom = zoom;
        zoom += (target - zoom) * 0.4;
        if (Math.abs(target - zoom) < 0.005) zoom = target;
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        double z = oZoom + (zoom - oZoom) * event.getPartialTick();
        if (z > 1.0001) event.setFOV(event.getFOV() / z);
    }
}
