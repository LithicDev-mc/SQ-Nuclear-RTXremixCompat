package com.sqc.nuclearsq.exo.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoEvents;
import com.sqc.nuclearsq.exo.ExoHotbarPayload;
import com.sqc.nuclearsq.exo.ExoModule;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class ExoHotbarClient {
    private ExoHotbarClient() {}

    private static final net.minecraft.client.KeyMapping[] KEYS = new net.minecraft.client.KeyMapping[9];
    private static int keep = -1;

    static {
        for (int i = 0; i < 9; i++)
            KEYS[i] = new net.minecraft.client.KeyMapping("key.nuclearsq.exo_hotbar_" + (i + 1), KeyConflictContext.IN_GAME, KeyModifier.ALT,
                    InputConstants.Type.KEYSYM, InputConstants.KEY_1 + i, "key.categories.nuclearsq");
    }

    private static boolean active(Minecraft mc) {
        return mc.player != null && !mc.player.isSpectator() && ExoEvents.level(mc.player, ExoModule.Kind.HOTBAR) > 0;
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (var k : KEYS) event.register(k);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getAction() != InputConstants.PRESS || mc.screen != null || !active(mc)) return;
        InputConstants.Key key = InputConstants.getKey(e.getKey(), e.getScanCode());
        for (int i = 0; i < 9; i++) {
            if (!KEYS[i].isActiveAndMatches(key)) continue;
            keep = mc.player.getInventory().selected;
            PacketDistributor.sendToServer(new ExoHotbarPayload(i));
            return;
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (keep >= 0 && mc.player != null) mc.player.getInventory().selected = keep;
        keep = -1;
    }

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "exo_hotbar"), ExoHotbarClient::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !active(mc)) return;
        int x = g.guiWidth() / 2 - 91 - 4 - 182, y = g.guiHeight() - 22;
        g.blitSprite(ResourceLocation.withDefaultNamespace("hud/hotbar"), x, y, 182, 22);
        for (int i = 0; i < 9; i++) {
            int px = x + 3 + i * 20, py = y + 3;
            ItemStack s = mc.player.getInventory().getItem(9 + i);
            g.renderItem(s, px, py, i);
            g.renderItemDecorations(mc.font, s, px, py);
            g.pose().pushPose();
            g.pose().translate(px, py - 1, 200);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(mc.font, Integer.toString(i + 1), 0, 0, 0xAAAAAA, true);
            g.pose().popPose();
        }
    }
}
