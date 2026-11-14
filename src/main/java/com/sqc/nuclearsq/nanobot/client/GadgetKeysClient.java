package com.sqc.nuclearsq.nanobot.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.nanobot.GadgetKeyPayload;
import com.sqc.nuclearsq.nanobot.NanoCanisterItem;
import com.sqc.nuclearsq.nanobot.NanoGadget;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class GadgetKeysClient {
    private GadgetKeysClient() {}

    public static final KeyMapping UNDO = new KeyMapping("key.nuclearsq.gadget_undo", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.nuclearsq");
    public static final KeyMapping REDO = new KeyMapping("key.nuclearsq.gadget_redo", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.nuclearsq");
    public static final KeyMapping SHAPE = new KeyMapping("key.nuclearsq.gadget_shape", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.nuclearsq");

    public static final KeyMapping ROTATE = new KeyMapping("key.nuclearsq.gadget_rotate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.nuclearsq");
    public static final KeyMapping MIRROR = new KeyMapping("key.nuclearsq.gadget_mirror", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.nuclearsq");

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ROTATE);
        event.register(MIRROR);
        event.register(UNDO);
        event.register(REDO);
        event.register(SHAPE);
    }

    private static ItemStack held(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack s = mc.player.getMainHandItem();
        if (s.getItem() instanceof NanoCanisterItem) return s;
        s = mc.player.getOffhandItem();
        return s.getItem() instanceof NanoCanisterItem ? s : ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        ItemStack can = held(mc);
        while (UNDO.consumeClick()) if (!can.isEmpty()) PacketDistributor.sendToServer(new GadgetKeyPayload(NanoGadget.KEY_UNDO, 0));
        while (REDO.consumeClick()) if (!can.isEmpty()) PacketDistributor.sendToServer(new GadgetKeyPayload(NanoGadget.KEY_REDO, 0));
        while (SHAPE.consumeClick()) if (!can.isEmpty()) PacketDistributor.sendToServer(new GadgetKeyPayload(NanoGadget.KEY_SHAPE, 1));
        while (ROTATE.consumeClick()) if (!can.isEmpty()) PacketDistributor.sendToServer(new GadgetKeyPayload(NanoGadget.KEY_ROTATE, mc.player.isShiftKeyDown() ? -1 : 1));
        while (MIRROR.consumeClick()) if (!can.isEmpty()) PacketDistributor.sendToServer(new GadgetKeyPayload(NanoGadget.KEY_MIRROR, 0));
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.isShiftKeyDown()) return;
        ItemStack can = held(mc);
        if (can.isEmpty() || !NanoCanisterItem.task(can).gadget()) return;
        int dir = event.getScrollDeltaY() > 0 ? 1 : -1;
        if (NanoCanisterItem.task(can) == com.sqc.nuclearsq.nanobot.NanoTask.GADGET_PASTE) {
            PacketDistributor.sendToServer(new GadgetKeyPayload(NanoGadget.KEY_ROTATE, dir));
            event.setCanceled(true);
            return;
        }
        PacketDistributor.sendToServer(new GadgetKeyPayload(Screen.hasAltDown() ? NanoGadget.KEY_HEIGHT : NanoGadget.KEY_SIZE, dir));
        event.setCanceled(true);
    }
}
