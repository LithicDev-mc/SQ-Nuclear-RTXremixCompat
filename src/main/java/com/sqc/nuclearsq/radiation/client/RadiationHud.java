package com.sqc.nuclearsq.radiation.client;

import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationState;
import com.sqc.nuclearsq.radiation.RadiationValues;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class RadiationHud {
    private RadiationHud() {}

    private static final ResourceLocation BORDER =
            ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/gui/radiation_heart.png");
    private static final float MIN_SV = 0.005f;
    private static final float FULL_SV = (float) RadiationManager.FULL_DAMAGE_SV;

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH,
                ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "radiation_hearts"), RadiationHud::render);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "radiation_vignette"), RadiationHud::renderVignette);
    }

    private static final int SIDE_STRIPS = 16;

    private static void renderVignette(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float dose = RadiationState.current();
        if (dose < RadiationValues.SV_NAUSEA) return;

        float t = ((mc.level != null ? mc.level.getGameTime() : 0L) + delta.getGameTimeDeltaPartialTick(false)) / 20f;
        int rgb;
        float base, amp, speed;
        if (dose >= RadiationValues.SV_FATAL) {
            rgb = 0x5A0000; base = 0.75f; amp = 0.08f; speed = 1.2f;
        } else if (dose >= RadiationValues.SV_LETHAL) {
            rgb = 0x9A1408; base = 0.45f; amp = 0.2f; speed = 2.2f;
        } else if (dose >= RadiationValues.SV_DAMAGE) {
            rgb = 0x3C8A14; base = 0.35f; amp = 0.15f; speed = 1.2f;
        } else {
            rgb = 0x52A01E; base = 0.14f; amp = 0.1f; speed = 0.8f;
        }
        float a = Mth.clamp(base + amp * (float) Math.sin(t * speed * Mth.TWO_PI), 0f, 1f);
        int edge = ((int) (a * 255f) << 24) | rgb;
        int clear = rgb;

        int w = g.guiWidth(), h = g.guiHeight();
        float size = dose >= RadiationValues.SV_LETHAL ? 0.22f : 0.16f;
        int bandV = Math.max(8, (int) (h * size));
        int bandH = Math.max(8, (int) (w * size));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fillGradient(0, 0, w, bandV, edge, clear);
        g.fillGradient(0, h - bandV, w, h, clear, edge);
        int step = Math.max(1, bandH / SIDE_STRIPS);
        for (int i = 0; i * step < bandH; i++) {
            float f = 1f - (i + 0.5f) * step / bandH;
            int col = ((int) (a * f * f * 255f) << 24) | rgb;
            int x0 = i * step, x1 = Math.min(bandH, x0 + step);
            g.fill(x0, 0, x1, h, col);
            g.fill(w - x1, 0, w - x0, h, col);
        }
        RenderSystem.disableBlend();
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        float dose = RadiationState.current();
        if (dose < MIN_SV) return;

        int n = Mth.clamp((int) Math.ceil(Math.min(1f, dose / FULL_SV) * 10f), 1, 10);
        int x0 = g.guiWidth() / 2 - 91;
        int y = g.guiHeight() - 39;
        float pulse = 0.65f + 0.35f * (float) Math.sin((mc.level != null ? mc.level.getGameTime() : 0L) * 0.3);

        RenderSystem.enableBlend();
        for (int i = 0; i < n; i++) {
            g.setColor(1f, 1f, 1f, i == n - 1 ? pulse : 1f);
            g.blit(BORDER, x0 + i * 8 - 1, y - 1, 0f, 0f, 11, 11, 11, 11);
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
