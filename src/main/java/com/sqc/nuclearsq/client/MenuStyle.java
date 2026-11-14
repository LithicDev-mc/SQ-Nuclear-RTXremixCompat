package com.sqc.nuclearsq.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.client.TermKit;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class MenuStyle {
    private MenuStyle() {}

    private static final Map<String, int[]> TEX = new HashMap<>();
    private static final String[] SHOTS = {"shot_01", "shot_02", "shot_03", "shot_04", "shot_05", "shot_06", "shot_07"};
    private static int shot = -1;
    private static long lastLoad;

    private static ResourceLocation rl(String name) {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "menu/" + name);
    }

    private static int[] texture(String name) {
        return TEX.computeIfAbsent(name, n -> {
            try (InputStream in = MenuStyle.class.getResourceAsStream("/assets/nuclearsq/textures/gui/menu/" + n + ".png")) {
                NativeImage img = NativeImage.read(NativeImage.Format.RGBA, in);
                DynamicTexture t = new DynamicTexture(img);
                t.setFilter(true, false);
                Minecraft.getInstance().getTextureManager().register(rl(n), t);
                return new int[] {t.getId(), img.getWidth(), img.getHeight()};
            } catch (Exception e) {
                NuclearSQ.LOGGER.warn("Menuebild {} nicht geladen", n, e);
                return null;
            }
        });
    }

    public static void background(GuiGraphics g, int w, int h) {
        Screen sc = Minecraft.getInstance().screen;
        String name = "menu";
        if (sc instanceof ConnectScreen || sc instanceof ReceivingLevelScreen || sc instanceof LevelLoadingScreen
                || sc instanceof ProgressScreen || sc instanceof GenericMessageScreen) {
            long now = Util.getMillis();
            if (shot < 0 || now - lastLoad > 2000L) {
                int n = SHOTS.length;
                shot = shot < 0 ? ThreadLocalRandom.current().nextInt(n) : (shot + 1 + ThreadLocalRandom.current().nextInt(n - 1)) % n;
            }
            lastLoad = now;
            name = SHOTS[shot];
        }
        int[] t = texture(name);
        if (t == null) {
            g.fill(0, 0, w, h, 0xFF000000);
            return;
        }
        float s = Math.max((float) w / t[1], (float) h / t[2]);
        int uw = Math.round(w / s), vh = Math.round(h / s);
        g.blit(rl(name), 0, 0, w, h, (t[1] - uw) / 2f, (t[2] - vh) / 2f, uw, vh, t[1], t[2]);
    }

    public static void startScreen(float progress, float fade) {
        int[] t = texture("start");
        if (t == null || fade <= 0f) return;
        int w = Minecraft.getInstance().getWindow().getWidth(), h = Minecraft.getInstance().getWindow().getHeight();
        float sa = (float) w / h, ia = (float) t[1] / t[2];
        float du = sa < ia ? (1 - sa / ia) / 2 : 0, dv = sa > ia ? (1 - ia / sa) / 2 : 0;
        float uw = (1 - 2 * du) / 1.25f, vh = (1 - 2 * dv) / 1.25f, u0 = (1 - uw) / 2, v0 = 1 - dv - vh;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, t[0]);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(0, 0, 0).setUv(u0, v0 + vh).setColor(1f, 1f, 1f, fade);
        b.addVertex(w, 0, 0).setUv(u0 + uw, v0 + vh).setColor(1f, 1f, 1f, fade);
        b.addVertex(w, h, 0).setUv(u0 + uw, v0).setColor(1f, 1f, 1f, fade);
        b.addVertex(0, h, 0).setUv(u0, v0).setColor(1f, 1f, 1f, fade);
        BufferUploader.drawWithShader(b.buildOrThrow());
        int bw = w / 2, bh = Math.max(6, h / 90), bx = (w - bw) / 2, by = h / 12;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(b, bx - 2, by - 2, bx + bw + 2, by + bh + 2, TermKit.col(TermKit.DIM), fade);
        quad(b, bx, by, bx + bw, by + bh, TermKit.col(TermKit.SCREEN), fade);
        quad(b, bx, by, bx + Math.round(bw * Math.min(1f, progress)), by + bh, TermKit.col(TermKit.GREEN), fade);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder b, int x0, int y0, int x1, int y1, int c, float fade) {
        float r = (c >> 16 & 255) / 255f, gr = (c >> 8 & 255) / 255f, bl = (c & 255) / 255f;
        b.addVertex(x0, y0, 0).setColor(r, gr, bl, fade);
        b.addVertex(x1, y0, 0).setColor(r, gr, bl, fade);
        b.addVertex(x1, y1, 0).setColor(r, gr, bl, fade);
        b.addVertex(x0, y1, 0).setColor(r, gr, bl, fade);
    }
}
