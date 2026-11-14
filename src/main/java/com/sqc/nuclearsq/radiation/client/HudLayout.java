package com.sqc.nuclearsq.radiation.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class HudLayout {
    private HudLayout() {}

    private static final String FILE_NAME = "nuclearsq-hud.properties";
    private static final long VISIBLE_MS = 250L;

    private static final Map<String, double[]> POS = new HashMap<>();
    private static final Map<String, int[]> RECT = new HashMap<>();
    private static final Map<String, Long> SEEN = new HashMap<>();
    private static boolean loaded, editing;
    private static String dragging;
    private static int grabX, grabY;

    public static boolean editing() {
        return editing;
    }

    public static boolean isMoved(String id) {
        load();
        return POS.containsKey(id);
    }

    public static int[] place(String id, int w, int h, int defX, int defY, int sw, int sh) {
        load();
        double[] f = POS.get(id);
        int x = f == null ? defX : (int) Math.round(f[0] * Math.max(0, sw - w));
        int y = f == null ? defY : (int) Math.round(f[1] * Math.max(0, sh - h));
        x = Math.max(0, Math.min(x, sw - w));
        y = Math.max(0, Math.min(y, sh - h));
        RECT.put(id, new int[] {x, y, w, h});
        SEEN.put(id, System.currentTimeMillis());
        return new int[] {x, y};
    }

    public static void outline(GuiGraphics g, String id) {
        if (!editing) return;
        int[] r = RECT.get(id);
        if (r == null) return;
        int c = id.equals(dragging) ? 0xFFFFD040 : 0xC0FFFFFF;
        g.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1], c);
        g.fill(r[0] - 1, r[1] + r[3], r[0] + r[2] + 1, r[1] + r[3] + 1, c);
        g.fill(r[0] - 1, r[1], r[0], r[1] + r[3], c);
        g.fill(r[0] + r[2], r[1], r[0] + r[2] + 1, r[1] + r[3], c);
    }

    public static void hint(GuiGraphics g) {
        if (!editing) return;
        Minecraft mc = Minecraft.getInstance();
        Component t = Component.translatable("hud.nuclearsq.layout.hint");
        int tw = mc.font.width(t);
        int x = (g.guiWidth() - tw) / 2;
        g.fill(x - 3, 2, x + tw + 3, 14, 0xC00B0D12);
        g.drawString(mc.font, t, x, 4, 0xFFFFD040, false);
    }

    private static boolean visible(String id) {
        Long t = SEEN.get(id);
        return t != null && System.currentTimeMillis() - t < VISIBLE_MS;
    }

    private static double[] mouse(Minecraft mc) {
        Window w = mc.getWindow();
        return new double[] {mc.mouseHandler.xpos() * w.getGuiScaledWidth() / Math.max(1, w.getScreenWidth()),
                mc.mouseHandler.ypos() * w.getGuiScaledHeight() / Math.max(1, w.getScreenHeight())};
    }

    private static String hit(double mx, double my) {
        for (Map.Entry<String, int[]> e : RECT.entrySet()) {
            int[] r = e.getValue();
            if (visible(e.getKey()) && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return e.getKey();
        }
        return null;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        boolean alt = mc.player != null && mc.screen == null && mc.getOverlay() == null
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT);
        if (alt && !editing && SEEN.keySet().stream().anyMatch(HudLayout::visible)) {
            editing = true;
            mc.mouseHandler.releaseMouse();
        } else if (!alt && editing) {
            editing = false;
            endDrag();
            if (mc.screen == null && mc.player != null) mc.mouseHandler.grabMouse();
        }
        if (!editing || dragging == null) return;
        int[] r = RECT.get(dragging);
        if (r == null) {
            dragging = null;
            return;
        }
        Window w = mc.getWindow();
        int sw = w.getGuiScaledWidth(), sh = w.getGuiScaledHeight();
        double[] m = mouse(mc);
        int x = Math.max(0, Math.min((int) Math.round(m[0]) - grabX, sw - r[2]));
        int y = Math.max(0, Math.min((int) Math.round(m[1]) - grabY, sh - r[3]));
        POS.put(dragging, new double[] {sw > r[2] ? x / (double) (sw - r[2]) : 0, sh > r[3] ? y / (double) (sh - r[3]) : 0});
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (!editing) return;
        event.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        double[] m = mouse(mc);
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                String id = hit(m[0], m[1]);
                if (id != null) {
                    int[] r = RECT.get(id);
                    dragging = id;
                    grabX = (int) Math.round(m[0]) - r[0];
                    grabY = (int) Math.round(m[1]) - r[1];
                }
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                endDrag();
            }
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            String id = hit(m[0], m[1]);
            if (id != null && POS.remove(id) != null) save();
        }
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!editing) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static void endDrag() {
        if (dragging == null) return;
        dragging = null;
        save();
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.isRegularFile(f)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f)) {
            p.load(r);
        } catch (IOException e) {
            NuclearSQ.LOGGER.warn("NuclearSQ: {} nicht lesbar: {}", FILE_NAME, e.toString());
            return;
        }
        for (String key : p.stringPropertyNames()) {
            if (!key.endsWith(".x")) continue;
            String id = key.substring(0, key.length() - 2);
            try {
                double x = Double.parseDouble(p.getProperty(key));
                double y = Double.parseDouble(p.getProperty(id + ".y", "0"));
                POS.put(id, new double[] {Math.max(0, Math.min(1, x)), Math.max(0, Math.min(1, y))});
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static void save() {
        Properties p = new Properties();
        for (Map.Entry<String, double[]> e : POS.entrySet()) {
            p.setProperty(e.getKey() + ".x", Double.toString(e.getValue()[0]));
            p.setProperty(e.getKey() + ".y", Double.toString(e.getValue()[1]));
        }
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "NuclearSQ HUD-Positionen (Anteil des freien Platzes, 0..1)");
        } catch (IOException e) {
            NuclearSQ.LOGGER.warn("NuclearSQ: {} nicht schreibbar: {}", FILE_NAME, e.toString());
        }
    }
}
