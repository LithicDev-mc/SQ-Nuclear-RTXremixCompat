package com.sqc.nuclearsq.radiation.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.network.GeigerPayload;
import com.sqc.nuclearsq.radiation.GeigerCounterItem;
import com.sqc.nuclearsq.radiation.RadiationManager;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class GeigerHud {
    private GeigerHud() {}

    private static final String ID = "geiger";
    private static final long STALE_MS = 2000L;

    private static volatile float rate;
    private static volatile long lastMs;

    public static void receive(GeigerPayload p) {
        rate = p.rate();
        lastMs = System.currentTimeMillis();
        GeigerCompass.receive(p);
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "geiger"), GeigerHud::render);
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static String compassText(net.minecraft.world.entity.player.Player p) {
        if (GeigerCompass.here()) return Component.translatable("hud.nuclearsq.geiger.here").getString();
        int dir = Math.floorMod(Math.round(GeigerCompass.rawTurns(p) * 8f), 8);
        double dy = GeigerCompass.heightDiff(p);
        String vert = dy > 3 ? " ▲" : dy < -3 ? " ▼" : "";
        return ARROWS[dir] + " " + Math.round(GeigerCompass.distance(p)) + " m" + vert;
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.gameMode == null) return;
        if (GeigerCounterItem.activeCounter(mc.player) == null || System.currentTimeMillis() - lastMs > STALE_MS) return;
        float r = rate;
        int col = switch (GeigerCounterItem.level(r)) {
            case 2 -> (System.currentTimeMillis() / 400L) % 2 == 0 ? 0xFFFF4040 : 0xFFFF9090;
            case 1 -> 0xFFFFB020;
            default -> 0xFFC4CCD6;
        };
        var font = mc.font;
        String reading = "☢ " + RadiationManager.fmtRate(r);
        String compass = GeigerCompass.active() ? "  " + compassText(mc.player) : "";
        int bw = font.width(reading) + font.width(compass) + 6, bh = 11;
        int sw = g.guiWidth(), sh = g.guiHeight();
        boolean bars = mc.gameMode.canHurtPlayer();
        int top = Math.max(mc.gui.leftHeight, mc.gui.rightHeight);
        int defY = bars ? sh - top - bh : sh - 34;
        int[] p = HudLayout.place(ID, bw, bh, (sw - bw) / 2, defY, sw, sh);
        if (bars && !HudLayout.isMoved(ID)) {
            mc.gui.leftHeight = top + bh;
            mc.gui.rightHeight = top + bh;
        }
        g.fill(p[0], p[1], p[0] + bw, p[1] + bh, 0x900B0D12);
        g.drawString(font, reading, p[0] + 3, p[1] + 2, col, false);
        if (!compass.isEmpty()) g.drawString(font, compass, p[0] + 3 + font.width(reading), p[1] + 2, GeigerCompass.color(), false);
        HudLayout.outline(g, ID);
    }
}
