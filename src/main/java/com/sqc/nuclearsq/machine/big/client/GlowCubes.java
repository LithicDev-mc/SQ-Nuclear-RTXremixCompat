package com.sqc.nuclearsq.machine.big.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

public final class GlowCubes {
    private static final int FRAMES = 8;
    private static final Map<String, Mask> MASKS = new HashMap<>();
    private static final Map<String, Map<GeoCube, Boolean>> CUBES = new HashMap<>();

    private record Mask(int w, int h, boolean[] on) {}

    private GlowCubes() {}

    static String pass;

    public static void begin(String id) {
        pass = id;
    }

    public static void end() {
        pass = null;
    }

    public static boolean skip(GeoCube cube) {
        String id = pass;
        return id != null && !glows(id, cube);
    }

    static boolean glows(String id, GeoCube cube) {
        Map<GeoCube, Boolean> m = CUBES.computeIfAbsent(id, k -> new IdentityHashMap<>());
        Boolean b = m.get(cube);
        if (b == null) {
            Mask mask = MASKS.computeIfAbsent(id, GlowCubes::load);
            b = mask == null || test(mask, cube);
            m.put(cube, b);
        }
        return b;
    }

    private static boolean test(Mask mask, GeoCube cube) {
        for (GeoQuad q : cube.quads()) {
            if (q == null) continue;
            float u0 = 2, u1 = -1, v0 = 2, v1 = -1;
            for (GeoVertex v : q.vertices()) {
                u0 = Math.min(u0, v.texU());
                u1 = Math.max(u1, v.texU());
                v0 = Math.min(v0, v.texV());
                v1 = Math.max(v1, v.texV());
            }
            int x0 = Math.max(0, (int) Math.floor(u0 * mask.w - 0.01f)), x1 = Math.min(mask.w, (int) Math.ceil(u1 * mask.w + 0.01f));
            int y0 = Math.max(0, (int) Math.floor(v0 * mask.h - 0.01f)), y1 = Math.min(mask.h, (int) Math.ceil(v1 * mask.h + 0.01f));
            for (int y = y0; y < y1; y++) {
                int row = y * mask.w;
                for (int x = x0; x < x1; x++) if (mask.on[row + x]) return true;
            }
        }
        return false;
    }

    private static Mask load(String id) {
        boolean[] on = null;
        int w = 0, h = 0;
        try {
            for (int k = 0; k < FRAMES; k++) {
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/block/" + id + "_glow_" + k + ".png");
                var res = Minecraft.getInstance().getResourceManager().getResource(loc);
                if (res.isEmpty()) return null;
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    if (on == null) {
                        w = img.getWidth();
                        h = img.getHeight();
                        on = new boolean[w * h];
                    } else if (img.getWidth() != w || img.getHeight() != h) {
                        return null;
                    }
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) if ((img.getPixelRGBA(x, y) >>> 24) != 0) on[y * w + x] = true;
                    }
                }
            }
        } catch (Exception | LinkageError e) {
            NuclearSQ.LOGGER.warn("Leuchtbilder von {} nicht lesbar, Leucht-Durchgang zeichnet alles", id, e);
            return null;
        }
        return on == null ? null : new Mask(w, h, on);
    }
}
