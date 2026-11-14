package com.sqc.nuclearsq.progression.client;

import net.minecraft.client.Minecraft;

public final class ProgressionClient {
    private ProgressionClient() {}

    public static void openSearch(String query) {
        Minecraft.getInstance().setScreen(new ProgressionScreen(query));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new ProgressionScreen());
    }
}
