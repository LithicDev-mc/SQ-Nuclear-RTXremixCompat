package com.sqc.nuclearsq.radiation.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.network.DosimeterPayload;
import com.sqc.nuclearsq.radiation.DosimeterItem;
import com.sqc.nuclearsq.radiation.RadiationManager;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class DosimeterHud {
    private DosimeterHud() {}

    private static final String ID = "dosimeter";

    private static volatile DosimeterPayload last = new DosimeterPayload(0, 0, 0, 0, false, 0, 0, 0, 0, 0, -1);
    private static volatile long lastMs;

    public static void receive(DosimeterPayload p) {
        last = p;
        lastMs = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "dosimeter"), DosimeterHud::render);
    }

    private static boolean holding(Minecraft mc) {
        for (InteractionHand h : InteractionHand.values()) if (mc.player.getItemInHand(h).getItem() instanceof DosimeterItem) return true;
        return false;
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        HudLayout.hint(g);
        DosimeterPayload d = last;
        boolean stale = System.currentTimeMillis() - lastMs > 3000L;
        boolean pinned = d.pinned() && !stale;
        if (!holding(mc) && !pinned) return;
        float rate = stale ? (float) RadiationManager.AMBIENT_SV_H : d.rate();
        boolean iodine = !stale && d.iodineTicks() > 0, medicine = !stale && d.medicineTicks() > 0, suit = !stale && d.suit() > 0.005f;
        boolean mask = !stale && d.filter() >= 0;
        var font = mc.font;
        float frac = Mth.clamp((float) (d.today() / DosimeterItem.DAY_LIMIT_SV), 0f, 1f);
        int col = frac >= 1f ? 0xFFFF4040 : frac >= 0.5f ? 0xFFFFB020 : 0xFF43E88D;
        boolean blink = frac >= 1f && (System.currentTimeMillis() / 400L) % 2 == 0;
        Component title = Component.translatable("hud.nuclearsq.dosimeter.title");
        Component fix = pinned ? Component.translatable("hud.nuclearsq.dosimeter.pin") : null;
        String doseLine = DosimeterItem.fmtDose(d.today()) + " / " + DosimeterItem.mSv(DosimeterItem.DAY_LIMIT_SV);
        Component rateLine = Component.translatable("hud.nuclearsq.dosimeter.rate", RadiationManager.fmtRate(rate));
        Component doseBody = Component.translatable("hud.nuclearsq.dosimeter.dose", DosimeterItem.fmtDose(stale ? 0 : d.dose()));
        Component bodyLine = Component.translatable("hud.nuclearsq.dosimeter.body", RadiationManager.fmtRate(stale ? 0 : d.body()));
        Component suitLine = suit ? Component.translatable("hud.nuclearsq.dosimeter.suit", factor(d.suit()),
                RadiationManager.fmtRate(d.outside())) : null;
        Component iodineLine = iodine ? Component.translatable("hud.nuclearsq.dosimeter.iodine", time(d.iodineTicks()),
                reduction(com.sqc.nuclearsq.radiation.RadiationValues.IODINE_FACTOR)) : null;
        Component medicineLine = medicine ? Component.translatable("hud.nuclearsq.dosimeter.medicine", time(d.medicineTicks()),
                reduction(com.sqc.nuclearsq.radiation.RadiationValues.MEDICINE_PROTECT_FACTOR)) : null;
        Component filterLine = !mask ? null : d.filter() > 0
                ? Component.translatable("hud.nuclearsq.dosimeter.filter", time(d.filter() * 20),
                        reduction(1.0 - com.sqc.nuclearsq.radiation.RespiratorItem.DUST_BLOCK))
                : Component.translatable("hud.nuclearsq.dosimeter.filter_empty");
        int w = 156;
        w = Math.max(w, font.width(title) + (fix != null ? font.width(fix) + 8 : 0) + 6);
        w = Math.max(w, font.width(doseLine) + 6);
        w = Math.max(w, font.width(rateLine) + 6);
        w = Math.max(w, font.width(doseBody) + 6);
        w = Math.max(w, font.width(bodyLine) + 6);
        if (suitLine != null) w = Math.max(w, font.width(suitLine) + 6);
        if (iodineLine != null) w = Math.max(w, font.width(iodineLine) + 6);
        if (medicineLine != null) w = Math.max(w, font.width(medicineLine) + 6);
        if (filterLine != null) w = Math.max(w, font.width(filterLine) + 6);
        int h = 63 + (iodine ? 11 : 0) + (medicine ? 11 : 0) + (suit ? 11 : 0) + (mask ? 11 : 0);
        int[] pos = HudLayout.place(ID, w + 4, h + 4, 4, 4, g.guiWidth(), g.guiHeight());
        int x = pos[0] + 2, y = pos[1] + 2;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF2A2F3A);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xE00B0D12);
        g.drawString(font, title, x + 3, y + 2, 0xFF8A93A3, false);
        if (fix != null) {
            int fw = font.width(fix);
            g.fill(x + w - fw - 5, y + 1, x + w - 1, y + 11, 0xFF2A2F3A);
            g.drawString(font, fix, x + w - fw - 3, y + 2, 0xFF43E88D, false);
        }
        g.drawString(font, doseLine, x + 3, y + 13, blink ? 0xFFFFFFFF : col, false);
        g.fill(x + 3, y + 23, x + w - 3, y + 26, 0xFF1C2028);
        g.fill(x + 3, y + 23, x + 3 + Math.round((w - 6) * frac), y + 26, col);
        g.drawString(font, rateLine, x + 3, y + 30, 0xFFD8DCE4, false);
        g.drawString(font, doseBody, x + 3, y + 41, d.dose() >= 0.25 && !stale ? 0xFFFFB020 : 0xFFD8DCE4, false);
        g.drawString(font, bodyLine, x + 3, y + 52, d.body() > 1e-6 && !stale ? 0xFFFFB020 : 0xFFD8DCE4, false);
        int ly = y + 63;
        if (suitLine != null) {
            g.drawString(font, suitLine, x + 3, ly, 0xFF4FC3F7, false);
            ly += 11;
        }
        if (iodineLine != null) {
            g.drawString(font, iodineLine, x + 3, ly, 0xFFFFB48A, false);
            ly += 11;
        }
        if (medicineLine != null) {
            g.drawString(font, medicineLine, x + 3, ly, 0xFF43E88D, false);
            ly += 11;
        }
        if (filterLine != null) {
            boolean low = d.filter() <= com.sqc.nuclearsq.radiation.RespiratorItem.WARN_SECONDS;
            g.drawString(font, filterLine, x + 3, ly, low ? 0xFFFF6A5A : 0xFF7BD4FF, false);
        }
        HudLayout.outline(g, ID);
    }

    private static String time(int ticks) {
        int s = (ticks + 19) / 20;
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static String factor(double f) {
        return Math.round(f * 100) + " %";
    }

    private static String reduction(double remaining) {
        return "\u2212" + Math.round((1 - remaining) * 100) + " %";
    }
}
