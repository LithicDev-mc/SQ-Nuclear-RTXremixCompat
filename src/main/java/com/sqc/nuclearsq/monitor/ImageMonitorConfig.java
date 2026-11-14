package com.sqc.nuclearsq.monitor;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ImageMonitorConfig {
    private ImageMonitorConfig() {}

    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_RESOLUTION = SERVER_BUILDER
            .comment("Largest image monitor resolution per side in pixels.")
            .defineInRange("maxResolutionPixels", 1920, 16, 1920);

    public static final ModConfigSpec.IntValue SYNC_TICKS = SERVER_BUILDER
            .comment("Send a changed image to players at most every N ticks (1 = 20 frames per second, 2 = 10).")
            .defineInRange("syncTicks", 1, 1, 40);

    public static final ModConfigSpec.IntValue VIEW_DISTANCE = SERVER_BUILDER
            .comment("Only players within this many blocks receive the image (0 = everyone who can see the chunk).")
            .defineInRange("viewDistance", 0, 0, 512);

    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue OCCLUSION_CULLING = CLIENT_BUILDER
            .comment("Do not draw an image monitor that is completely hidden behind solid blocks.")
            .define("occlusionCulling", true);

    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    public static int maxResolution() {
        return SERVER_SPEC.isLoaded() ? MAX_RESOLUTION.get() : MAX_RESOLUTION.getDefault();
    }

    public static int syncTicks() {
        return SERVER_SPEC.isLoaded() ? SYNC_TICKS.get() : SYNC_TICKS.getDefault();
    }

    public static int viewDistance() {
        return SERVER_SPEC.isLoaded() ? VIEW_DISTANCE.get() : VIEW_DISTANCE.getDefault();
    }

    public static boolean occlusionCulling() {
        return !CLIENT_SPEC.isLoaded() || OCCLUSION_CULLING.get();
    }
}
