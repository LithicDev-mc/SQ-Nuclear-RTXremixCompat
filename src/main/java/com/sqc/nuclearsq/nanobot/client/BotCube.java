package com.sqc.nuclearsq.nanobot.client;

import com.sqc.nuclearsq.NuclearClientConfig;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.resources.ResourceLocation;

public final class BotCube {
    private BotCube() {}

    public static final ResourceLocation DARK = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/entity/nano_bot_dark.png");
    public static final ResourceLocation CHROME = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/entity/nano_bot_chrome.png");

    public static ResourceLocation texture() {
        boolean chrome = NuclearClientConfig.SPEC.isLoaded() && NuclearClientConfig.NANOBOT_LOOK.get() == NuclearClientConfig.NanobotLook.CHROME;
        return chrome ? CHROME : DARK;
    }

    public static final float[][][] POS = {
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
            {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}},
            {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}},
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
            {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
    };
    public static final int[][][] UV = {
            {{0, 1}, {1, 1}, {1, 0}, {0, 0}},
            {{0, 1}, {1, 1}, {1, 0}, {0, 0}},
            {{0, 1}, {1, 1}, {1, 0}, {0, 0}},
            {{0, 1}, {1, 1}, {1, 0}, {0, 0}},
            {{0, 0}, {1, 0}, {1, 1}, {0, 1}},
            {{0, 0}, {1, 0}, {1, 1}, {0, 1}},
    };
    public static final float[][] NORMAL = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    public static float u(float[] r, int f, int c) {
        return UV[f][c][0] == 0 ? r[0] : r[2];
    }

    public static float v(float[] r, int f, int c) {
        return UV[f][c][1] == 0 ? r[1] : r[3];
    }
}
