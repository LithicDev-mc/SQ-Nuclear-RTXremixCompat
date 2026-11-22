package com.sqc.nuclearsq.client;

import com.mojang.blaze3d.platform.Window;
import com.sqc.nuclearsq.progression.client.ProgressionScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class FitScale {
    private static final int MARGIN = 8;

    private FitScale() {}

    public static int base(Minecraft mc) {
        return mc.getWindow().calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
    }

    public static int fit(Screen screen, int base) {
        int needW, needH, floor = 1, scale = base;
        if (screen instanceof ProgressionScreen) {
            needW = ProgressionScreen.FULL_W + MARGIN;
            needH = ProgressionScreen.FULL_H + MARGIN;
            floor = Math.min(base, 2);
        } else if (screen instanceof AbstractContainerScreen<?> s && screen.getClass().getName().startsWith("com.sqc.nuclearsq.")) {
            needW = s.getXSize() + MARGIN;
            needH = s.getYSize() + MARGIN;
        } else return base;
        Window w = Minecraft.getInstance().getWindow();
        while (scale > floor && (w.getWidth() / scale < needW || w.getHeight() / scale < needH)) scale--;
        return scale;
    }

    public static void apply(Minecraft mc, Screen screen) {
        Window w = mc.getWindow();
        int s = fit(screen, base(mc));
        if (w.getGuiScale() != s) w.setGuiScale(s);
    }
}
