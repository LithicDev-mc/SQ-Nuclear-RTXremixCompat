package com.sqc.nuclearsq.compat.iris;

import java.lang.reflect.Method;

import com.sqc.nuclearsq.NuclearSQ;

import net.neoforged.fml.ModList;

public final class IrisCompat {
    private IrisCompat() {}

    private static boolean resolved;
    private static Object api;
    private static Method inUse, shadow;

    public static boolean shaderPackInUse() {
        if (frameSeen == frame) return inUseCached;
        frameSeen = frame;
        return inUseCached = shaderPackInUseNow();
    }

    private static int frame, frameSeen = -1;
    private static boolean inUseCached;

    public static void newFrame() {
        frame++;
    }

    private static boolean shaderPackInUseNow() {
        if (!resolved) {
            resolved = true;
            try {
                if (ModList.get() != null && ModList.get().isLoaded("iris")) {
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = c.getMethod("getInstance").invoke(null);
                    inUse = c.getMethod("isShaderPackInUse");
                    shadow = c.getMethod("isRenderingShadowPass");
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                NuclearSQ.LOGGER.warn("Iris gefunden, aber die API ist nicht lesbar; Backen bleibt an", e);
            }
        }
        if (inUse == null) return false;
        try {
            return (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static boolean shadowPass() {
        if (!shaderPackInUse() || shadow == null) return false;
        try {
            return (boolean) shadow.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
