package com.sqc.nuclearsq.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.network.VeinMinePayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class VeinMinerClient {
    private VeinMinerClient() {}

    public static final KeyMapping VEIN_MINE = new KeyMapping("key.nuclearsq.vein_mine",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.nuclearsq");

    private static boolean sent;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(VEIN_MINE);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            sent = false;
            return;
        }
        boolean held = VEIN_MINE.isDown();
        if (held != sent) {
            sent = held;
            PacketDistributor.sendToServer(new VeinMinePayload(held));
        }
    }
}
